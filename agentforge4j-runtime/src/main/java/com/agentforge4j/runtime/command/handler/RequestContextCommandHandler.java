// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.context.ContextSourceResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Handles a {@link RequestContextCommand}: grants a requested selector only when it is in the step's
 * declared {@code expandableScope}, resolves and writes granted content into context, and denies
 * otherwise — the same governance shape as tool invocation (the model requests, the runtime decides).
 *
 * <p>The workflow-declared {@link ContextSelection#effectiveMaxExpansions()} bounds how many
 * {@code RequestContextCommand} instances within a single step invocation's command batch are
 * evaluated at all: {@link CommandApplicationRequest#requestContextRoundNumber()} — a 1-based count of
 * this command among {@code RequestContextCommand} instances in the batch, computed by
 * {@link com.agentforge4j.runtime.command.CommandApplier} — is checked against the limit
 * <em>before</em> the selector-scope check, per design §4.4. A step with no {@link ContextSelection}
 * has no expandable scope, so every request is denied.
 */
public final class RequestContextCommandHandler implements CommandHandler<RequestContextCommand> {

  private static final System.Logger LOG = System.getLogger(
      RequestContextCommandHandler.class.getName());

  private static final String REASON_NOT_IN_SCOPE = "NOT_IN_EXPANDABLE_SCOPE";
  private static final String REASON_MAX_REACHED = "MAX_EXPANSIONS_REACHED";

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
      if (isInScope(requested, expandableScope)) {
        grant(request, requested, round);
      } else {
        recordDenied(request, requested, round, REASON_NOT_IN_SCOPE);
      }
    }
    return CommandApplicationResult.CONTINUE;
  }

  private static boolean isInScope(ContextSelector requested, List<ContextSelector> expandableScope) {
    return expandableScope.stream().anyMatch(allowed ->
        allowed.kind() == requested.kind() && allowed.ref().equals(requested.ref()));
  }

  private void grant(CommandApplicationRequest request, ContextSelector selector, int round) {
    WorkflowState state = request.state();
    // Never overwrite an existing key with a re-encoded copy: a STATE_KEY selector's ref may already
    // name a value the workflow author or a prior grant round put there, and re-writing it here would
    // mutate its type/encoding (e.g. a StringContextValue becoming a re-encoded JsonContextValue) for
    // a context-read request that has no business changing existing state.
    if (state.getContextValue(selector.ref()).isEmpty()) {
      String content = contextSourceResolver.resolveFull(selector, state, request.enclosingWorkflow());
      state.putContextValue(selector.ref(),
          new JsonContextValue(content, ContextProvenance.SYSTEM_GENERATED));
    }
    String payload = "stepId=%s selector=%s:%s round=%d".formatted(state.getCurrentStepId(),
        selector.kind(), selector.ref(), round);
    eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
        WorkflowEventType.CONTEXT_EXPANSION_GRANTED, payload, request.agentId());
    LOG.log(System.Logger.Level.DEBUG, "Context expansion granted stepId={0}, selector={1}, round={2}",
        state.getCurrentStepId(), selector, round);
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
