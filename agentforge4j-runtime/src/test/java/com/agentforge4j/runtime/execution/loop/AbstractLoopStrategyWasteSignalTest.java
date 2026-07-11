// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.spi.governance.WasteSignalKind;
import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@code AbstractLoopStrategy}'s waste-signal evaluation, shared by all four
 * {@link LoopStrategy} implementations — exercised here through {@link FixedCountLoopStrategy}
 * (the simplest strategy: no signal-based termination to control for).
 */
class AbstractLoopStrategyWasteSignalTest {

  private static final String BLUEPRINT_ID = "bp-fixed";

  @Test
  void unchangedLoopContextRaisesASignalOnTheSecondIteration() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = recorder(eventLog);
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(stepSequenceExecutor,
        eventRecorder, new MaxIterationsHandler(eventRecorder, CLOCK), new ObjectMapper(),
        WasteSignalPolicy.NO_OP);
    WorkflowState state = state();
    ExecutionContext executionContext = executionContext(state);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      executionContext.allocateStepSequenceUid();
      return ExecutionOutcome.COMPLETED;
    });
    LoopConfig config = fixedCountConfig(2);

    strategy.iterate(blueprint(config), config, executionContext);

    List<String> signalPayloads = tokenGovernanceSignalPayloads(eventLog);
    // Nothing in the shared context ever changes between the two iterations; the first iteration
    // has no prior to compare against, so only the second raises.
    assertThat(signalPayloads).hasSize(1);
    assertThat(signalPayloads.get(0)).contains("kind=UNCHANGED_LOOP_CONTEXT")
        .contains("blueprintId=" + BLUEPRINT_ID)
        .contains("iteration=2");
  }

  @Test
  void unchangedLoopContextDoesNotFireWhenOnlyLedgerProgressChanged() {
    // Regression for the P0 fingerprint-filter fix: canonicalNonReservedContext previously
    // stripped every __-prefixed key, including __ledger.* — the reserved namespace a declared
    // ledger's merged section lives under. A loop whose per-iteration progress is recorded only
    // via the ledger therefore fingerprinted as "unchanged" every iteration regardless of real
    // progress.
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = recorder(eventLog);
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    AtomicInteger iterationCount = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      context.allocateStepSequenceUid();
      context.getState().putContextValue(ReservedContextKeys.ledgerKey("progress"),
          new StringContextValue("iteration-" + iterationCount.incrementAndGet(),
              ContextProvenance.SYSTEM_GENERATED));
      return ExecutionOutcome.COMPLETED;
    });
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(stepSequenceExecutor,
        eventRecorder, new MaxIterationsHandler(eventRecorder, CLOCK), new ObjectMapper(),
        WasteSignalPolicy.NO_OP);
    WorkflowState state = state();
    ExecutionContext executionContext = executionContext(state);
    LoopConfig config = fixedCountConfig(2);

    strategy.iterate(blueprint(config), config, executionContext);

    assertThat(tokenGovernanceSignalPayloads(eventLog))
        .noneMatch(payload -> payload.contains("UNCHANGED_LOOP_CONTEXT"));
  }

  @Test
  void repeatedLoopOutputRaisesASignalWhenAnIterationRepeatsAPriorOutput() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = recorder(eventLog);
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      context.allocateStepSequenceUid();
      context.getState().putStepOutput("s1", "same output");
      return ExecutionOutcome.COMPLETED;
    });
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(stepSequenceExecutor,
        eventRecorder, new MaxIterationsHandler(eventRecorder, CLOCK), new ObjectMapper(),
        WasteSignalPolicy.NO_OP);
    WorkflowState state = state();
    ExecutionContext executionContext = executionContext(state);
    LoopConfig config = fixedCountConfig(2);

    strategy.iterate(blueprint(config), config, executionContext);

    List<String> signalPayloads = tokenGovernanceSignalPayloads(eventLog);
    assertThat(signalPayloads).anySatisfy(payload -> assertThat(payload)
        .contains("kind=REPEATED_LOOP_OUTPUT")
        .contains("blueprintId=" + BLUEPRINT_ID)
        .contains("iteration=2"));
  }

  @Test
  void repeatedLoopOutputDoesNotFireWhenOutputsDiffer() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = recorder(eventLog);
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      context.allocateStepSequenceUid();
      int iteration = context.getState().getLoopIterationCursor(BLUEPRINT_ID);
      context.getState().putStepOutput("s1", "output-" + iteration);
      return ExecutionOutcome.COMPLETED;
    });
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(stepSequenceExecutor,
        eventRecorder, new MaxIterationsHandler(eventRecorder, CLOCK), new ObjectMapper(),
        WasteSignalPolicy.NO_OP);
    WorkflowState state = state();
    ExecutionContext executionContext = executionContext(state);
    LoopConfig config = fixedCountConfig(2);

    strategy.iterate(blueprint(config), config, executionContext);

    assertThat(tokenGovernanceSignalPayloads(eventLog))
        .noneMatch(payload -> payload.contains("REPEATED_LOOP_OUTPUT"));
  }

  @Test
  void configuredWasteSignalPolicyReceivesTheRaisedLoopSignal() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = recorder(eventLog);
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    WasteSignalPolicy policy = mock(WasteSignalPolicy.class);
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(stepSequenceExecutor,
        eventRecorder, new MaxIterationsHandler(eventRecorder, CLOCK), new ObjectMapper(), policy);
    WorkflowState state = state();
    ExecutionContext executionContext = executionContext(state);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      executionContext.allocateStepSequenceUid();
      return ExecutionOutcome.COMPLETED;
    });
    LoopConfig config = fixedCountConfig(2);

    strategy.iterate(blueprint(config), config, executionContext);

    org.mockito.Mockito.verify(policy).onSignal(org.mockito.ArgumentMatchers.argThat(
        signal -> signal.kind() == WasteSignalKind.UNCHANGED_LOOP_CONTEXT));
  }

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"),
      ZoneOffset.UTC);

  private static EventRecorder recorder(InMemoryWorkflowEventLog eventLog) {
    return new EventRecorder(eventLog, CLOCK);
  }

  private static List<String> tokenGovernanceSignalPayloads(InMemoryWorkflowEventLog eventLog) {
    return eventLog.getEvents("run-1").stream()
        .filter(e -> e.eventType() == WorkflowEventType.TOKEN_GOVERNANCE_SIGNAL)
        .map(WorkflowEvent::payload)
        .toList();
  }

  private static WorkflowState state() {
    return new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
  }

  private static ExecutionContext executionContext(WorkflowState state) {
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(dummyStep()), List.of(), List.of());
    return new ExecutionContext(state, workflow, 32);
  }

  private static LoopConfig fixedCountConfig(int maxIterations) {
    return LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, maxIterations,
        MaxIterationsAction.AWAIT_USER);
  }

  private static BlueprintDefinition blueprint(LoopConfig config) {
    return new BlueprintDefinition(BLUEPRINT_ID, "fixed count bp",
        new BlueprintBehaviour(config, StepTransition.AUTO), List.of(dummyStep()));
  }

  private static StepDefinition dummyStep() {
    return StepDefinition.builder()
        .withStepId("s1")
        .withName("s1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .build();
  }
}
