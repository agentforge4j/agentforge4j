// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.LlmSummary;
import com.agentforge4j.llm.DefaultTokenEstimator;
import com.agentforge4j.runtime.context.CompactSibling;
import com.agentforge4j.runtime.context.CompactSiblingStore;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.context.ContextSourceId;
import com.agentforge4j.runtime.context.ContextSourceResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactBehaviourHandlerTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"),
      ZoneOffset.UTC);

  private final ObjectMapper mapper = new ObjectMapper();

  private static LedgerDefinition ledger() {
    return new LedgerDefinition("requirements", "ledger/requirement-ledger.schema.json",
        LedgerMergeStrategy.APPEND, null);
  }

  private static ContextSelector source() {
    return new ContextSelector(ContextSourceKind.LEDGER_SECTION, "requirements",
        ContextVariant.FULL);
  }

  private static StepDefinition compactStep(CompactionPolicy policy,
      com.agentforge4j.core.workflow.step.behaviour.CompactionMode mode) {
    return StepDefinition.builder().withStepId("compact").withName("compact")
        .withBehaviour(new CompactBehaviour(source(), mode, policy)).build();
  }

  private static StepDefinition referencingStep(String id) {
    ContextSelector selector = new ContextSelector(ContextSourceKind.LEDGER_SECTION, "requirements",
        ContextVariant.COMPACT_PREFERRED);
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop"))
        .withContextSelection(new ContextSelection(List.of(selector), List.of(), null))
        .build();
  }

  private static WorkflowDefinition workflow(StepDefinition compact, StepDefinition... rest) {
    List<com.agentforge4j.core.workflow.Executable> steps = new java.util.ArrayList<>();
    steps.add(compact);
    steps.addAll(List.of(rest));
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), steps, List.of(),
        List.of(ledger()));
  }

  private CompactBehaviourHandler handler(WorkflowDefinition workflow) {
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    return new CompactBehaviourHandler(
        new ContextSourceResolver(new ContextRenderer(mapper), mapper),
        new DefaultTokenEstimator(),
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        eventRecorder,
        mapper);
  }

  private ExecutionContext context(WorkflowDefinition workflow) {
    WorkflowState state = new WorkflowState("run-1", workflow.id(), null,
        Instant.parse("2026-01-01T00:00:00Z"));
    state.setCurrentStepId("compact");
    return new ExecutionContext(state, workflow, 32);
  }

  @Test
  void skipsWhenSourceTooSmall() {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(1_000_000, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);

    ExecutionOutcome outcome = handler(workflow).handle(compact, behaviour, executionContext);

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper)).isEmpty();
  }

  @Test
  void skipsWhenInsufficientReuse() {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 5));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact, referencingStep("s1"));
    ExecutionContext executionContext = context(workflow);

    handler(workflow).handle(compact, behaviour, executionContext);

    assertThat(CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper)).isEmpty();
  }

  @Test
  void performsDeterministicExtractWhenReuseSufficient() throws Exception {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 1));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact, referencingStep("s1"));
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1","rationale":"because"}],"openQuestions":["q?"]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);

    handler(workflow).handle(compact, behaviour, executionContext);

    var stored = CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper);
    assertThat(stored).isPresent();
    assertThat(stored.get().content()).doesNotContain("rationale").contains("REQ-1").contains("q?");
    assertThat(stored.get().metadata().producedByStepId()).isEqualTo("compact");
  }

  @Test
  void skipsWhenAlreadyUpToDate() {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 1));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact, referencingStep("s1"));
    ExecutionContext executionContext = context(workflow);
    CompactBehaviourHandler handler = handler(workflow);

    handler.handle(compact, behaviour, executionContext);
    var firstMetadata = CompactSiblingStore.read(executionContext.getState(),
        ContextSourceId.of(source()), mapper).orElseThrow().metadata();

    handler.handle(compact, behaviour, executionContext);
    var secondMetadata = CompactSiblingStore.read(executionContext.getState(),
        ContextSourceId.of(source()), mapper).orElseThrow().metadata();

    assertThat(secondMetadata).isEqualTo(firstMetadata);
  }

  @Test
  void llmSummaryThrowsUnsupported() {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new LlmSummary("STANDARD"),
        new CompactionPolicy(0, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);

    assertThatThrownBy(() -> handler(workflow).handle(compact, behaviour, executionContext))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("LLM_SUMMARY");
  }

  @Test
  void deterministicExtractOnNonLedgerSourceThrows() {
    ContextSelector artifactSource = new ContextSelector(ContextSourceKind.STATE_KEY, "k",
        ContextVariant.FULL);
    CompactBehaviour behaviour = new CompactBehaviour(artifactSource, new DeterministicExtract(),
        new CompactionPolicy(0, 0));
    StepDefinition compact = StepDefinition.builder().withStepId("compact").withName("compact")
        .withBehaviour(behaviour).build();
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);
    executionContext.getState().putContextValue("k",
        new com.agentforge4j.core.workflow.context.StringContextValue("v",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() -> handler(workflow).handle(compact, behaviour, executionContext))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
