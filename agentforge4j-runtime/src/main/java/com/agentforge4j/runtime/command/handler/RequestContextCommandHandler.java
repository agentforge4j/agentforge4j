// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.context.ContextSourceId;
import com.agentforge4j.runtime.context.ContextSourceResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Optional;

/**
 * Handles a {@link RequestContextCommand}: grants a requested selector only when it matches an entry
 * in the step's declared {@code expandableScope}, resolves and writes granted content into context,
 * and denies otherwise — the same governance shape as tool invocation (the model requests, the
 * runtime decides).
 *
 * <p><strong>Scope matching honours the declared form:</strong> a request matches an expandable-scope
 * entry by source ({@code kind} + {@code ref}); the granted content is then resolved using the
 * <em>declared</em> entry — including its {@link com.agentforge4j.core.workflow.step.ContextVariant}
 * — never the variant the requester asked for. An agent therefore cannot widen a declared
 * compact-form source to its full form by requesting {@code FULL}.
 *
 * <p><strong>Round limit:</strong> the workflow-declared
 * {@link ContextSelection#effectiveMaxExpansions()} bounds how many {@code RequestContextCommand}
 * instances within a single step invocation's command batch are evaluated at all:
 * {@link CommandApplicationRequest#requestContextRoundNumber()} — a 1-based count of this command
 * among {@code RequestContextCommand} instances in the batch, computed by
 * {@link com.agentforge4j.runtime.command.CommandApplier} — is checked against the limit
 * <em>before</em> the selector-scope check, so exceeding the limit is reported as
 * {@code MAX_EXPANSIONS_REACHED} even for out-of-scope selectors. A step with no
 * {@link ContextSelection} has no expandable scope, so every request is denied.
 *
 * <p><strong>Write and delivery semantics:</strong> granted content is written under the reserved
 * key {@link ReservedContextKeys#grantedKey} for the selector's canonical source id — never under a
 * plain context key, so a grant can never collide with author- or agent-owned state. Every grant
 * resolves the source's <em>current</em> value; when a source was granted before and its content has
 * changed since, the fresh value replaces the stored one and the {@code CONTEXT_EXPANSION_GRANTED}
 * event carries {@code changedSincePriorGrant=true} with the prior and new content fingerprints.
 * There is no serve-stale path: a resolution failure propagates rather than falling back to the
 * previously stored value. The grant does not re-invoke the requesting agent; the written key
 * reaches an agent through the normal context rendering of a subsequent invocation that maps it.
 */
public final class RequestContextCommandHandler implements CommandHandler<RequestContextCommand> {

  private static final System.Logger LOG = System.getLogger(
      RequestContextCommandHandler.class.getName());

  private static final String REASON_NOT_IN_SCOPE = "NOT_IN_EXPANDABLE_SCOPE";
  private static final String REASON_MAX_REACHED = "MAX_EXPANSIONS_REACHED";
  private static final String REASON_RESERVED_NAMESPACE = "RESERVED_NAMESPACE";

  private final ContextSourceResolver contextSourceResolver;
  private final EventRecorder eventRecorder;

  public RequestContextCommandHandler(ContextSourceResolver contextSourceResolver,
      EventRecorder eventRecorder) {
    this.contextSourceResolver = Validate.notNull(contextSourceResolver,
        "contextSourceResolver must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  @Override
  public Class<RequestContextCommand> getCommandClass() {
    return RequestContextCommand.class;
  }

  @Override
  public CommandApplicationResult apply(RequestContextCommand command,
      CommandApplicationRequest request) {
    ContextSelection selection = request.step().contextSelection();
    List<ContextSelector> expandableScope = selection != null
        ? selection.expandableScope()
        : List.of();
    int maxExpansions = selection != null ? selection.effectiveMaxExpansions()
        : ContextSelection.DEFAULT_MAX_EXPANSIONS;
    int round = request.requestContextRoundNumber();

    if (round > maxExpansions) {
      command.requestedSelectors()
          .forEach(selector -> recordDenied(request, selector, round, REASON_MAX_REACHED));
      return CommandApplicationResult.CONTINUE;
    }

    for (ContextSelector requested : command.requestedSelectors()) {
      Optional<ContextSelector> declared = findInScope(requested, expandableScope);
      if (declared.isPresent()) {
        grant(request, declared.get(), round);
      } else {
        recordDenied(request, requested, round, REASON_NOT_IN_SCOPE);
      }
    }
    return CommandApplicationResult.CONTINUE;
  }

  /**
   * Matches a requested selector against the declared expandable scope by source ({@code kind} +
   * {@code ref}) and returns the <em>declared</em> entry, whose variant governs what is served —
   * the requested variant is deliberately ignored (see the class contract).
   */
  private static Optional<ContextSelector> findInScope(ContextSelector requested,
      List<ContextSelector> expandableScope) {
    return expandableScope.stream()
        .filter(allowed -> allowed.kind() == requested.kind()
            && allowed.ref().equals(requested.ref()))
        .findFirst();
  }

  private void grant(CommandApplicationRequest request, ContextSelector selector, int round) {
    // Reject the reserved '__' runtime namespace (ledger merges, compact siblings, and other
    // governance state) the same way SetContextCommandHandler guards LLM-declared output keys: a
    // granted expansion must never read runtime-owned state as a source, even if a workflow author
    // declared such a ref in expandableScope. Treated as a deny, not a thrown exception, so it stays
    // consistent with this class's existing "requests are granted or denied" governance shape.
    if (selector.ref().startsWith("__")) {
      recordDenied(request, selector, round, REASON_RESERVED_NAMESPACE);
      return;
    }
    WorkflowState state = request.state();
    // resolve() (not resolveFull()) so a granted expansion honors the DECLARED ContextVariant:
    // COMPACT_PREFERRED serves the compact sibling when fresh, and COMPACT_ONLY fails closed via
    // CompactSiblingUnavailableException (allowed to propagate, per this class's contract of
    // surfacing command-application failures rather than swallowing them). Resolution always reads
    // the source's CURRENT value — there is no serve-stale path.
    String content = contextSourceResolver.resolve(selector, state, request.enclosingWorkflow());
    String grantedKey = ReservedContextKeys.grantedKey(ContextSourceId.of(selector));
    Optional<String> priorContent = state.getContextValue(grantedKey)
        .map(RequestContextCommandHandler::contentOf);
    String changeSuffix = "";
    if (priorContent.isPresent() && !priorContent.get().equals(content)) {
      // Same staleness mechanism as compact siblings: content identity by fingerprint.
      changeSuffix = " changedSincePriorGrant=true priorFingerprint=%s newFingerprint=%s"
          .formatted(ContextFingerprint.of(priorContent.get()), ContextFingerprint.of(content));
    }
    if (priorContent.isEmpty() || !changeSuffix.isEmpty()) {
      state.putContextValue(grantedKey, grantedValue(selector.kind(), content));
    }
    String payload = "stepId=%s selector=%s:%s round=%d%s".formatted(state.getCurrentStepId(),
        selector.kind(), selector.ref(), round, changeSuffix);
    eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
        WorkflowEventType.CONTEXT_EXPANSION_GRANTED, payload, request.agentId());
    LOG.log(System.Logger.Level.DEBUG, "Context expansion granted stepId={0}, selector={1}, round={2}",
        state.getCurrentStepId(), selector, round);
  }

  /**
   * Extracts the raw content string a prior grant stored, for fingerprint comparison. Grants only
   * ever write the two types produced by {@link #grantedValue}.
   */
  private static String contentOf(ContextValue value) {
    if (value instanceof JsonContextValue json) {
      return json.json();
    }
    if (value instanceof StringContextValue text) {
      return text.value();
    }
    throw new IllegalStateException(
        "Granted-context key holds an unexpected value type: %s".formatted(value.getClass()));
  }

  /**
   * Wraps granted content in the value type matching what the source kind actually produced:
   * ledger sections resolve to canonical JSON and artifact/state keys resolve to rendered JSON, so
   * both store as {@link JsonContextValue}; a step's raw output and a context pack's file content
   * are arbitrary text, so both store as {@link StringContextValue} — storing them as JSON would
   * hand downstream JSON consumers unparseable content far from the cause.
   */
  private static ContextValue grantedValue(ContextSourceKind kind, String content) {
    return switch (kind) {
      case LEDGER_SECTION, ARTIFACT, STATE_KEY ->
          new JsonContextValue(content, ContextProvenance.SYSTEM_GENERATED);
      case STEP_OUTPUT, CONTEXT_PACK ->
          new StringContextValue(content, ContextProvenance.SYSTEM_GENERATED);
    };
  }

  private void recordDenied(CommandApplicationRequest request, ContextSelector selector, int round,
      String reason) {
    WorkflowState state = request.state();
    String payload = "stepId=%s selector=%s:%s round=%d reason=%s".formatted(
        state.getCurrentStepId(), selector.kind(), selector.ref(), round, reason);
    eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
        WorkflowEventType.CONTEXT_EXPANSION_DENIED, payload, request.agentId());
    LOG.log(System.Logger.Level.DEBUG,
        "Context expansion denied stepId={0}, selector={1}, round={2}, reason={3}",
        state.getCurrentStepId(), selector, round, reason);
  }
}
