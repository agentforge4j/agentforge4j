// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextProvenance;
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
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.LlmSummary;
import com.agentforge4j.llm.DefaultTokenEstimator;
import com.agentforge4j.runtime.ContextPackRegistry;
import com.agentforge4j.runtime.context.CompactSibling;
import com.agentforge4j.runtime.context.CompactSiblingStore;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.context.ContextSourceId;
import com.agentforge4j.runtime.context.ContextSourceResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.ModelSource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    // Must be in expandableScope, not selectors: only expandableScope counts toward
    // minDownstreamReuse (see CompactBehaviourHandler's class Javadoc) since selectors is not yet
    // enforced anywhere in the runtime.
    ContextSelector selector = new ContextSelector(ContextSourceKind.LEDGER_SECTION, "requirements",
        ContextVariant.COMPACT_PREFERRED);
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop"))
        .withContextSelection(new ContextSelection(List.of(), List.of(selector), null))
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
    return handler(workflow, mock(AgentInvoker.class));
  }

  private CompactBehaviourHandler handler(WorkflowDefinition workflow, AgentInvoker agentInvoker) {
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    return new CompactBehaviourHandler(
        new ContextSourceResolver(new ContextRenderer(mapper), mapper, ContextPackRegistry.EMPTY),
        new DefaultTokenEstimator(),
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        eventRecorder,
        mapper,
        agentInvoker);
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
    // A deterministic, non-LLM transform of framework-owned ledger content inherits the source's
    // own trust level — never LLM_GENERATED, which would over-trust nothing an LLM ever touched.
    assertThat(storedProvenance(executionContext.getState(), ContextSourceId.of(source())))
        .isEqualTo(ContextProvenance.SYSTEM_GENERATED);
  }

  private static ContextProvenance storedProvenance(WorkflowState state, String sourceId) {
    return state.getContextValue(ReservedContextKeys.compactKey(sourceId))
        .orElseThrow(() -> new AssertionError("No compact sibling stored for " + sourceId))
        .provenance();
  }

  @Test
  void recordsTheResolvedEstimatorOnCompactionPerformed() throws Exception {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 1));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact, referencingStep("s1"));
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    CompactBehaviourHandler handler = new CompactBehaviourHandler(
        new ContextSourceResolver(new ContextRenderer(mapper), mapper, ContextPackRegistry.EMPTY),
        new DefaultTokenEstimator(),
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        new EventRecorder(eventLog, CLOCK), mapper, mock(AgentInvoker.class));

    handler.handle(compact, behaviour, executionContext);

    var performed = eventLog.getEvents("run-1").stream()
        .filter(e -> e.eventType() == com.agentforge4j.core.workflow.event.WorkflowEventType.COMPACTION_PERFORMED)
        .findFirst();
    assertThat(performed).isPresent();
    assertThat(performed.get().payload()).contains("\"estimator\":\"DefaultTokenEstimator\"");
  }

  @Test
  void recordsTheResolvedEstimatorOnCompactionSkipped() {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(1_000_000, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    CompactBehaviourHandler handler = new CompactBehaviourHandler(
        new ContextSourceResolver(new ContextRenderer(mapper), mapper, ContextPackRegistry.EMPTY),
        new DefaultTokenEstimator(),
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        new EventRecorder(eventLog, CLOCK), mapper, mock(AgentInvoker.class));

    handler.handle(compact, behaviour, executionContext);

    var skipped = eventLog.getEvents("run-1").stream()
        .filter(e -> e.eventType() == com.agentforge4j.core.workflow.event.WorkflowEventType.COMPACTION_SKIPPED)
        .findFirst();
    assertThat(skipped).isPresent();
    assertThat(skipped.get().payload()).contains("estimator=DefaultTokenEstimator");
  }

  @Test
  void countsExpandableScopeReferenceTowardReuse() throws Exception {
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 1));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    ContextSelector expandable = new ContextSelector(ContextSourceKind.LEDGER_SECTION,
        "requirements", ContextVariant.COMPACT_PREFERRED);
    StepDefinition expandableOnly = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(new FailBehaviour("stop"))
        .withContextSelection(new ContextSelection(List.of(), List.of(expandable), null))
        .build();
    WorkflowDefinition workflow = workflow(compact, expandableOnly);
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1","rationale":"because"}]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);

    handler(workflow).handle(compact, behaviour, executionContext);

    assertThat(CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper)).isPresent();
  }

  @Test
  void doesNotCountSelectorsOnlyReferenceTowardReuse() {
    // Only expandableScope counts toward minDownstreamReuse; contextSelection.selectors is not yet
    // enforced anywhere in the runtime, so counting it would trigger compaction for a declaration
    // with no actual downstream effect.
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 1));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    ContextSelector selectorsOnly = new ContextSelector(ContextSourceKind.LEDGER_SECTION,
        "requirements", ContextVariant.COMPACT_PREFERRED);
    StepDefinition selectorsOnlyStep = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(new FailBehaviour("stop"))
        .withContextSelection(new ContextSelection(List.of(selectorsOnly), List.of(), null))
        .build();
    WorkflowDefinition workflow = workflow(compact, selectorsOnlyStep);
    ExecutionContext executionContext = context(workflow);

    handler(workflow).handle(compact, behaviour, executionContext);

    assertThat(CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper)).isEmpty();
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
  void sectionSubpathSourceFailsLoudInsteadOfProducingAnEmptyCompact() throws Exception {
    // Load-time validation rejects a COMPACT source naming a ledger section; this pins the runtime
    // backstop for programmatically built definitions: the bare array a subpath resolves to must
    // fail loud, never become {"entries":[]} silently.
    ContextSelector subpathSource = new ContextSelector(ContextSourceKind.LEDGER_SECTION,
        "requirements.entries", ContextVariant.FULL);
    CompactBehaviour behaviour = new CompactBehaviour(subpathSource, new DeterministicExtract(),
        new CompactionPolicy(0, 0));
    StepDefinition compact = StepDefinition.builder().withStepId("compact").withName("compact")
        .withBehaviour(behaviour).build();
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1","rationale":"because"}],"openQuestions":[],"conflicts":[]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);

    assertThatThrownBy(() -> handler(workflow).handle(compact, behaviour, executionContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("whole ledger envelope");
    assertThat(CompactSiblingStore.read(executionContext.getState(),
        ContextSourceId.of(subpathSource), mapper)).isEmpty();
  }

  @Test
  void absentEnvelopeFieldsBecomeEmptyArraysInTheCompactForm() throws Exception {
    // An envelope missing conflicts (or openQuestions) must compact to empty arrays for those
    // fields — never to JSON nulls, which would violate the envelope shape downstream.
    CompactBehaviour behaviour = new CompactBehaviour(source(), new DeterministicExtract(),
        new CompactionPolicy(0, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":["q?"]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);

    handler(workflow).handle(compact, behaviour, executionContext);

    var stored = CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper).orElseThrow();
    var compactNode = mapper.readTree(stored.content());
    assertThat(compactNode.get("conflicts").isArray()).isTrue();
    assertThat(compactNode.get("conflicts")).isEmpty();
    assertThat(stored.content()).doesNotContain("null");
  }

  @Test
  void performsLlmSummaryThroughTheDeclaredAgent() throws Exception {
    CompactBehaviour behaviour = new CompactBehaviour(source(),
        new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(0, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact, referencingStep("s1"));
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":["q?"]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);
    AgentInvoker agentInvoker = mock(AgentInvoker.class);
    when(agentInvoker.invoke(eq("summarizer-agent"), any(), any(), any(), eq("STANDARD"),
        anyString())).thenReturn(new AgentInvocationResult("summary text", List.of(), null, null,
        null, ModelSource.PROVIDER_DEFAULT, null));

    handler(workflow, agentInvoker).handle(compact, behaviour, executionContext);

    var stored = CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper);
    assertThat(stored).isPresent();
    assertThat(stored.get().content()).isEqualTo("summary text");
    // The staged source content must be addressed as the sole input key — nothing else from the
    // run's shared context leaks into the summarization agent's rendered input.
    String inputKey = com.agentforge4j.core.workflow.state.ReservedContextKeys.llmSummaryInputKey(
        ContextSourceId.of(source()));
    verify(agentInvoker).invoke(eq("summarizer-agent"),
        eq(new com.agentforge4j.core.workflow.context.ContextMapping(List.of(inputKey), List.of())),
        any(), any(), eq("STANDARD"), anyString());
    // The staging key is scratch, not durable governance state — it must not survive the invocation.
    assertThat(executionContext.getState().getContextValue(inputKey)).isEmpty();
    // The compact sibling's content is an LLM's own generated text, regardless of what it
    // summarized — LLM_GENERATED, never the framework-owned SYSTEM_GENERATED label DeterministicExtract
    // earns.
    assertThat(storedProvenance(executionContext.getState(), ContextSourceId.of(source())))
        .isEqualTo(ContextProvenance.LLM_GENERATED);
  }

  @Test
  void llmSummaryFailsLoudWhenTheAgentEmitsCommands() throws Exception {
    // A compaction step has no command-application semantics (no pause states, no escalation
    // path); an agent that emits a command must fail the step loudly, not have it silently dropped.
    CompactBehaviour behaviour = new CompactBehaviour(source(),
        new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(0, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);
    AgentInvoker agentInvoker = mock(AgentInvoker.class);
    com.agentforge4j.core.command.SetContextCommand emittedCommand =
        new com.agentforge4j.core.command.SetContextCommand("k",
            new com.agentforge4j.core.workflow.context.StringContextValue("v",
                ContextProvenance.LLM_GENERATED));
    when(agentInvoker.invoke(eq("summarizer-agent"), any(), any(), any(), eq("STANDARD"),
        anyString())).thenReturn(new AgentInvocationResult("summary text",
        List.of(emittedCommand), null, null, null, ModelSource.PROVIDER_DEFAULT, null));

    assertThatThrownBy(() -> handler(workflow, agentInvoker).handle(compact, behaviour,
        executionContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("summarizer-agent")
        .hasMessageContaining("compact")
        .hasMessageContaining("SetContextCommand");
    assertThat(CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper)).isEmpty();
    String inputKey = com.agentforge4j.core.workflow.state.ReservedContextKeys.llmSummaryInputKey(
        ContextSourceId.of(source()));
    assertThat(executionContext.getState().getContextValue(inputKey)).isEmpty();
  }

  @Test
  void llmSummaryPropagatesInvocationFailureAndCleansUpStagingKey() throws Exception {
    CompactBehaviour behaviour = new CompactBehaviour(source(),
        new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(0, 0));
    StepDefinition compact = compactStep(behaviour.policy(), behaviour.mode());
    WorkflowDefinition workflow = workflow(compact);
    ExecutionContext executionContext = context(workflow);
    var merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}]}""");
    com.agentforge4j.runtime.ledger.LedgerMerger.writeMerged(executionContext.getState(), ledger(),
        merged);
    AgentInvoker agentInvoker = mock(AgentInvoker.class);
    when(agentInvoker.invoke(eq("summarizer-agent"), any(), any(), any(), eq("STANDARD"),
        anyString())).thenThrow(new RuntimeException("provider unavailable"));

    assertThatThrownBy(() -> handler(workflow, agentInvoker).handle(compact, behaviour,
        executionContext))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("provider unavailable");
    // The failed-invocation cleanup guarantee: a failed compaction must never leave the resolved
    // source content sitting in run state under the synthetic staging key.
    String inputKey = com.agentforge4j.core.workflow.state.ReservedContextKeys.llmSummaryInputKey(
        ContextSourceId.of(source()));
    assertThat(executionContext.getState().getContextValue(inputKey)).isEmpty();
    assertThat(CompactSiblingStore.read(executionContext.getState(), ContextSourceId.of(source()),
        mapper)).isEmpty();
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
