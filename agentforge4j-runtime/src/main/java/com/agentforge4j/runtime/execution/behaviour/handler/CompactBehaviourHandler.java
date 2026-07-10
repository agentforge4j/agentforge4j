// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.reachability.ReachableStep;
import com.agentforge4j.core.workflow.reachability.ReachableStepGraph;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
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
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Handles a {@link CompactBehaviour}: deterministically decides whether compacting the declared source
 * is worthwhile, no-ops when it is not, and always records why (design §4.5).
 *
 * <p><strong>Reuse counting (owner-relaxed rule, 2026-07-05):</strong> a referencing step counts
 * toward {@link CompactionPolicy#minDownstreamReuse()} when it is reachable in the resolved workflow
 * graph ({@link ReachableStepGraph#walk}) from the run root and its declared {@code contextSelection}
 * — its {@code selectors} or its {@code expandableScope} (a granted expansion reads the compact
 * form too, so a potential reader counts as reuse) — references this
 * step's source with variant {@code COMPACT_PREFERRED} or {@code COMPACT_ONLY}. There is no
 * before/after-the-COMPACT-step ordering check; see the design record for why the relaxation is safe
 * (a before-step's variant resolution fails closed or falls back at read time regardless).
 *
 * <p>A referencing step inside a reached sub-workflow counts individually, not collapsed to its
 * {@code WORKFLOW} step: this runtime shares one flat context map for the whole run (no
 * per-sub-workflow context isolation), so there is no "input mapping" to test for whether the source
 * was "passed into" the sub-workflow — every reachable step already sees the same source. This is a
 * recorded deviation from the design's literal sub-workflow clause, not a design quote.
 *
 * <p>Only {@link DeterministicExtract} is implemented in this pass, and only for
 * {@code LEDGER_SECTION} sources: it copies the source verbatim except stripping any top-level
 * {@code rationale} field from each entry (a recognized convention field name elsewhere in this
 * design). {@link LlmSummary} is not invoked: its shipped shape carries a {@code modelTier} but no
 * agent identity, so there is no agent to invoke through the normal {@link
 * com.agentforge4j.runtime.llm.AgentInvoker} path — invoking one requires a design decision (add an
 * agent reference to {@code LlmSummary}, or define an internal invocation convention) that has not
 * been made. Reaching an {@code LlmSummary} mode throws {@link UnsupportedOperationException}
 * naming this gap, rather than silently no-opping or fabricating a summary.
 */
public final class CompactBehaviourHandler implements BehaviourHandler<CompactBehaviour> {

  private static final System.Logger LOG = System.getLogger(CompactBehaviourHandler.class.getName());

  private final ContextSourceResolver contextSourceResolver;
  private final TokenEstimator tokenEstimator;
  private final WorkflowRepository workflowRepository;
  private final EventRecorder eventRecorder;
  private final ObjectMapper objectMapper;

  public CompactBehaviourHandler(ContextSourceResolver contextSourceResolver,
      TokenEstimator tokenEstimator, WorkflowRepository workflowRepository,
      EventRecorder eventRecorder, ObjectMapper objectMapper) {
    this.contextSourceResolver = Validate.notNull(contextSourceResolver,
        "contextSourceResolver must not be null");
    this.tokenEstimator = Validate.notNull(tokenEstimator, "tokenEstimator must not be null");
    this.workflowRepository = Validate.notNull(workflowRepository,
        "workflowRepository must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
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

    SkipReason skip = decideSkip(behaviour.policy(), fullContent, sourceId, sourceFingerprint,
        executionContext);
    if (skip != null) {
      recordSkipped(state, step.stepId(), sourceId, sourceFingerprint, skip);
      return ExecutionOutcome.COMPLETED;
    }

    performCompaction(step, behaviour, state, source, sourceId, fullContent, sourceFingerprint);
    return ExecutionOutcome.COMPLETED;
  }

  private SkipReason decideSkip(CompactionPolicy policy, String fullContent, String sourceId,
      String sourceFingerprint, ExecutionContext executionContext) {
    int estimatedUnits = tokenEstimator.estimate(fullContent);
    if (estimatedUnits < policy.minSourceUnits()) {
      return SkipReason.SOURCE_TOO_SMALL;
    }
    int reuseCount = countReferencingSteps(executionContext.getRootWorkflow(), sourceId);
    if (reuseCount < policy.minDownstreamReuse()) {
      return SkipReason.INSUFFICIENT_REUSE;
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
      WorkflowState state, ContextSelector source, String sourceId, String fullContent,
      String sourceFingerprint) {
    String compactContent;
    if (behaviour.mode() instanceof DeterministicExtract) {
      Validate.isTrue(source.kind() == ContextSourceKind.LEDGER_SECTION,
          () -> new UnsupportedOperationException(
              "DeterministicExtract is only implemented for LEDGER_SECTION sources in this runtime "
                  + "version; source kind was %s".formatted(source.kind())));
      compactContent = extractLedgerDeterministically(fullContent);
    } else if (behaviour.mode() instanceof LlmSummary) {
      throw new UnsupportedOperationException(
          "CompactBehaviour source '%s' declares LLM_SUMMARY, but LlmSummary carries no agent "
              + "identity to invoke through AgentInvoker; this requires a design decision (add an "
              + "agent reference to LlmSummary, or define an internal invocation convention) that "
              + "has not been made".formatted(sourceId));
    } else {
      throw new IllegalStateException("Unhandled CompactionMode: " + behaviour.mode().getClass());
    }

    int estimatedUnitsBefore = tokenEstimator.estimate(fullContent);
    int estimatedUnitsAfter = tokenEstimator.estimate(compactContent);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId, sourceFingerprint,
        behaviour.mode(), estimatedUnitsBefore, estimatedUnitsAfter, step.stepId(),
        behaviour.policy());
    CompactSiblingStore.write(state, sourceId, new CompactSibling(compactContent, metadata),
        objectMapper);

    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.COMPACTION_PERFORMED,
        toEventPayload(metadata), "runtime");
    LOG.log(System.Logger.Level.INFO,
        "Compaction performed stepId={0}, sourceId={1}, unitsBefore={2}, unitsAfter={3}",
        step.stepId(), sourceId, estimatedUnitsBefore, estimatedUnitsAfter);
  }

  /**
   * Copies the ledger envelope verbatim except stripping a top-level {@code rationale} field from
   * each entry. Entry ids, {@code openQuestions}, and {@code conflicts} are always carried forward
   * (structural, exempt from summarization).
   */
  private String extractLedgerDeterministically(String fullLedgerContentJson) {
    try {
      JsonNode envelope = objectMapper.readTree(fullLedgerContentJson);
      ObjectNode result = objectMapper.createObjectNode();
      result.set("entries", stripRationale(envelope.get("entries")));
      result.set("openQuestions", envelope.get("openQuestions"));
      result.set("conflicts", envelope.get("conflicts"));
      return CanonicalJson.render(result, objectMapper);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to deterministically extract ledger content: %s".formatted(e.getMessage()), e);
    }
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
    String payload = "sourceId=%s sourceFingerprint=%s reason=%s"
        .formatted(sourceId, sourceFingerprint, reason);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COMPACTION_SKIPPED, payload,
        "runtime");
    LOG.log(System.Logger.Level.DEBUG, "Compaction skipped stepId={0}, reason={1}", stepId, reason);
  }

  private String toEventPayload(CompactSiblingMetadata metadata) {
    try {
      return objectMapper.writeValueAsString(metadata);
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
