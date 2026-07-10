// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.reachability.ReachableStep;
import com.agentforge4j.core.workflow.reachability.ReachableStepGraph;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.LlmSummary;
import com.agentforge4j.llm.api.TokenEstimator;
import com.agentforge4j.runtime.context.CanonicalJson;
import com.agentforge4j.runtime.context.CompactSibling;
import com.agentforge4j.runtime.context.CompactSiblingStore;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.context.ContextSourceId;
import com.agentforge4j.runtime.context.ContextSourceResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Handles a {@link CompactBehaviour}: deterministically decides whether compacting the declared source
 * is worthwhile, no-ops when it is not, and always records why.
 *
 * <p><strong>Reuse counting:</strong> a referencing step counts toward
 * {@link CompactionPolicy#minDownstreamReuse()} when it is reachable in the resolved workflow
 * graph ({@link ReachableStepGraph#walk}) from the run root and its declared {@code contextSelection}
 * — its {@code selectors} or its {@code expandableScope} (a granted expansion reads the compact
 * form too, so a potential reader counts as reuse) — references this
 * step's source with variant {@code COMPACT_PREFERRED} or {@code COMPACT_ONLY}. There is no
 * before/after-the-COMPACT-step ordering check: a step that reads the source before compaction ran
 * either fails closed ({@code COMPACT_ONLY}) or falls back to the full source
 * ({@code COMPACT_PREFERRED}) at read time regardless, so ordering cannot make the count unsafe.
 *
 * <p>A referencing step inside a reached sub-workflow counts individually, not collapsed to its
 * {@code WORKFLOW} step: this runtime shares one flat context map for the whole run (no
 * per-sub-workflow context isolation), so there is no input mapping to test for whether the source
 * was passed into the sub-workflow — every reachable step already sees the same source.
 *
 * <p>{@link DeterministicExtract} is implemented only for whole-ledger {@code LEDGER_SECTION}
 * sources: it copies the source verbatim except stripping any top-level {@code rationale} field
 * from each entry. {@link LlmSummary} invokes its declared {@code agentRef} through the normal
 * {@link AgentInvoker} path — the same mechanism as an {@code AGENT} step, no special-casing. Since
 * {@code AgentInvoker} only ever renders context from {@code state} via a {@code ContextMapping}
 * (never from an arbitrary caller-supplied string), the already-resolved source content is first
 * staged under {@link ReservedContextKeys#llmSummaryInputKey(String)} so a mapping can address it —
 * the same convention {@code SparBehaviourHandler} uses for its resolution-round prompt.
 */
public final class CompactBehaviourHandler implements BehaviourHandler<CompactBehaviour> {

  private static final System.Logger LOG = System.getLogger(CompactBehaviourHandler.class.getName());

  private final ContextSourceResolver contextSourceResolver;
  private final TokenEstimator tokenEstimator;
  private final WorkflowRepository workflowRepository;
  private final EventRecorder eventRecorder;
  private final ObjectMapper objectMapper;
  private final AgentInvoker agentInvoker;

  public CompactBehaviourHandler(ContextSourceResolver contextSourceResolver,
      TokenEstimator tokenEstimator, WorkflowRepository workflowRepository,
      EventRecorder eventRecorder, ObjectMapper objectMapper, AgentInvoker agentInvoker) {
    this.contextSourceResolver = Validate.notNull(contextSourceResolver,
        "contextSourceResolver must not be null");
    this.tokenEstimator = Validate.notNull(tokenEstimator, "tokenEstimator must not be null");
    this.workflowRepository = Validate.notNull(workflowRepository,
        "workflowRepository must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.agentInvoker = Validate.notNull(agentInvoker, "agentInvoker must not be null");
  }

  @Override
  public Class<CompactBehaviour> behaviourType() {
    return CompactBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step, CompactBehaviour behaviour,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    WorkflowDefinition enclosing = executionContext.getEnclosingWorkflow();
    ContextSelector source = behaviour.source();
    String sourceId = ContextSourceId.of(source);
    String fullContent = contextSourceResolver.resolveFull(source, state, enclosing);
    String sourceFingerprint = ContextFingerprint.of(fullContent);
    int estimatedUnitsBefore = tokenEstimator.estimate(fullContent);

    SkipReason skip = decideSkip(behaviour.policy(), estimatedUnitsBefore, sourceId,
        sourceFingerprint, executionContext);
    if (skip != null) {
      recordSkipped(state, step.stepId(), sourceId, sourceFingerprint, skip);
      return ExecutionOutcome.COMPLETED;
    }

    performCompaction(step, behaviour, executionContext, source, sourceId, fullContent,
        sourceFingerprint, estimatedUnitsBefore);
    return ExecutionOutcome.COMPLETED;
  }

  private SkipReason decideSkip(CompactionPolicy policy, int estimatedUnits, String sourceId,
      String sourceFingerprint, ExecutionContext executionContext) {
    if (estimatedUnits < policy.minSourceUnits()) {
      return SkipReason.SOURCE_TOO_SMALL;
    }
    // A zero threshold compacts regardless of reuse — skip the reachable-graph walk entirely; it
    // runs on every COMPACT execution and loop-heavy workflows would pay for it repeatedly.
    if (policy.minDownstreamReuse() > 0) {
      int reuseCount = countReferencingSteps(executionContext.getRootWorkflow(), sourceId);
      if (reuseCount < policy.minDownstreamReuse()) {
        return SkipReason.INSUFFICIENT_REUSE;
      }
    }
    return CompactSiblingStore.read(executionContext.getState(), sourceId, objectMapper)
        .filter(sibling -> sibling.metadata().sourceFingerprint().equals(sourceFingerprint))
        .isPresent() ? SkipReason.UP_TO_DATE : null;
  }

  private int countReferencingSteps(WorkflowDefinition root, String sourceId) {
    int count = 0;
    for (ReachableStep reachable : ReachableStepGraph.walk(root, workflowRepository::get)) {
      if (referencesSource(reachable.step(), sourceId)) {
        count++;
      }
    }
    return count;
  }

  private static boolean referencesSource(StepDefinition step, String sourceId) {
    ContextSelection selection = step.contextSelection();
    if (selection == null) {
      return false;
    }
    return referencesSource(selection.selectors(), sourceId)
        || referencesSource(selection.expandableScope(), sourceId);
  }

  private static boolean referencesSource(List<ContextSelector> selectors, String sourceId) {
    for (ContextSelector selector : selectors) {
      boolean compactVariant = selector.variant() == ContextVariant.COMPACT_PREFERRED
          || selector.variant() == ContextVariant.COMPACT_ONLY;
      if (compactVariant && ContextSourceId.of(selector).equals(sourceId)) {
        return true;
      }
    }
    return false;
  }

  private void performCompaction(StepDefinition step, CompactBehaviour behaviour,
      ExecutionContext executionContext, ContextSelector source, String sourceId,
      String fullContent, String sourceFingerprint, int estimatedUnitsBefore) {
    WorkflowState state = executionContext.getState();
    String compactContent;
    ContextProvenance provenance;
    if (behaviour.mode() instanceof DeterministicExtract) {
      Validate.isTrue(source.kind() == ContextSourceKind.LEDGER_SECTION,
          () -> new UnsupportedOperationException(
              "DeterministicExtract is only implemented for LEDGER_SECTION sources in this runtime "
                  + "version; source kind was %s".formatted(source.kind())));
      compactContent = extractLedgerDeterministically(fullContent);
      // Deterministic, non-LLM transform of framework-owned ledger content: inherits the source's
      // own SYSTEM_GENERATED trust level (see CompactSiblingStore.write's Javadoc).
      provenance = ContextProvenance.SYSTEM_GENERATED;
    } else if (behaviour.mode() instanceof LlmSummary llmSummary) {
      compactContent = summarizeViaAgent(step, llmSummary, executionContext, sourceId, fullContent);
      // The content is an LLM's own generated text regardless of what it summarized — the
      // compaction step's determinism does not launder the LLM authorship of its output.
      provenance = ContextProvenance.LLM_GENERATED;
    } else {
      throw new IllegalStateException("Unhandled CompactionMode: " + behaviour.mode().getClass());
    }

    int estimatedUnitsAfter = tokenEstimator.estimate(compactContent);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId, sourceFingerprint,
        behaviour.mode(), estimatedUnitsBefore, estimatedUnitsAfter, step.stepId(),
        behaviour.policy());
    CompactSiblingStore.write(state, sourceId, new CompactSibling(compactContent, metadata),
        objectMapper, provenance);

    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.COMPACTION_PERFORMED,
        toEventPayload(metadata), "runtime");
    LOG.log(System.Logger.Level.INFO,
        "Compaction performed stepId={0}, sourceId={1}, unitsBefore={2}, unitsAfter={3}",
        step.stepId(), sourceId, estimatedUnitsBefore, estimatedUnitsAfter);
  }

  /**
   * Summarizes {@code fullContent} through {@code llmSummary}'s declared agent. The resolved source
   * content is staged in state under a reserved key (see class Javadoc) and addressed via a
   * {@code ContextMapping} naming only that key, so the agent's rendered input is exactly the
   * resolved source — nothing else from the run's shared context leaks in. The staging key is
   * scratch, not durable governance state (unlike the ledger/compact/granted reserved keys) — it is
   * always removed once the invocation completes, whether it succeeds or throws, so a failed
   * compaction never leaves the resolved source content sitting in run state under a synthetic key.
   */
  private String summarizeViaAgent(StepDefinition step, LlmSummary llmSummary,
      ExecutionContext executionContext, String sourceId, String fullContent) {
    WorkflowState state = executionContext.getState();
    String inputKey = ReservedContextKeys.llmSummaryInputKey(sourceId);
    state.putContextValue(inputKey,
        new StringContextValue(fullContent, ContextProvenance.SYSTEM_GENERATED));
    try {
      ContextMapping mapping = new ContextMapping(List.of(inputKey), List.of());
      AgentInvocationResult result = agentInvoker.invoke(
          llmSummary.agentRef(),
          mapping,
          state,
          step.stepPrompt(),
          llmSummary.modelTier(),
          executionContext.getActiveWorkflowId());
      return result.rawResponse();
    } finally {
      state.removeContextValue(inputKey);
    }
  }

  /**
   * Copies the ledger envelope verbatim except stripping a top-level {@code rationale} field from
   * each entry. Entry ids, {@code openQuestions}, and {@code conflicts} are always carried forward
   * (structural, exempt from summarization); an envelope field that is absent from the source is
   * written as an empty array, never as a JSON null.
   *
   * <p>The source must be a whole ledger envelope (a JSON object). A ledger <em>section</em> subpath
   * resolves to a bare array with none of the envelope fields, and extracting it would silently
   * produce an empty compact form of a non-empty ledger — load-time validation rejects such a
   * source, and this method fails loud as the defence-in-depth backstop for programmatically built
   * definitions.
   */
  private String extractLedgerDeterministically(String fullLedgerContentJson) {
    try {
      JsonNode envelope = objectMapper.readTree(fullLedgerContentJson);
      Validate.isTrue(envelope.isObject(), () -> new IllegalStateException(
          ("DeterministicExtract requires a whole ledger envelope (a JSON object); the resolved "
              + "source is %s — a COMPACT source must not name a ledger section")
              .formatted(envelope.getNodeType())));
      ObjectNode result = objectMapper.createObjectNode();
      result.set("entries", stripRationale(envelope.get("entries")));
      result.set("openQuestions", arrayOrEmpty(envelope.get("openQuestions")));
      result.set("conflicts", arrayOrEmpty(envelope.get("conflicts")));
      return CanonicalJson.render(result, objectMapper);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to deterministically extract ledger content: %s".formatted(e.getMessage()), e);
    }
  }

  private JsonNode arrayOrEmpty(JsonNode value) {
    return value != null && value.isArray() ? value : objectMapper.createArrayNode();
  }

  private JsonNode stripRationale(JsonNode entries) {
    if (entries == null || !entries.isArray()) {
      return objectMapper.createArrayNode();
    }
    ArrayNode result = objectMapper.createArrayNode();
    for (JsonNode entry : entries) {
      if (!entry.isObject() || !entry.has("rationale")) {
        result.add(entry);
        continue;
      }
      ObjectNode stripped = entry.deepCopy();
      stripped.remove("rationale");
      result.add(stripped);
    }
    return result;
  }

  private void recordSkipped(WorkflowState state, String stepId, String sourceId,
      String sourceFingerprint, SkipReason reason) {
    String payload = "sourceId=%s sourceFingerprint=%s reason=%s estimator=%s"
        .formatted(sourceId, sourceFingerprint, reason, tokenEstimator.getClass().getSimpleName());
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COMPACTION_SKIPPED, payload,
        "runtime");
    LOG.log(System.Logger.Level.DEBUG, "Compaction skipped stepId={0}, reason={1}", stepId, reason);
  }

  private String toEventPayload(CompactSiblingMetadata metadata) {
    try {
      // Audit evidence for which TokenEstimator implementation produced estimatedUnitsBefore/After
      // — not part of CompactSiblingMetadata itself, since the estimator choice is an audit-log
      // concern (which implementation was resolved when multiple are registered), not the compact
      // sibling's own structural provenance.
      ObjectNode payload = (ObjectNode) objectMapper.valueToTree(metadata);
      payload.put("estimator", tokenEstimator.getClass().getSimpleName());
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to serialize compact sibling metadata: %s".formatted(e.getMessage()), e);
    }
  }

  private enum SkipReason {
    SOURCE_TOO_SMALL,
    INSUFFICIENT_REUSE,
    UP_TO_DATE
  }
}
