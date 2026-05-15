package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForEachLoopStrategyTest {

  private static final String LIST_KEY = "items";
  private static final String BLUEPRINT_ID = "bp-for-each";

  private EventRecorder eventRecorder;
  private MaxIterationsHandler maxIterationsHandler;
  private StepSequenceExecutor stepSequenceExecutor;
  private ForEachLoopStrategy strategy;
  private WorkflowState state;
  private ExecutionContext executionContext;
  private BlueprintDefinition blueprint;

  @BeforeEach
  void setUp() {
    eventRecorder = mock(EventRecorder.class);
    maxIterationsHandler = new MaxIterationsHandler(eventRecorder,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    stepSequenceExecutor = mock(StepSequenceExecutor.class);
    strategy = new ForEachLoopStrategy(stepSequenceExecutor, eventRecorder, maxIterationsHandler);
    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T12:00:00Z"));
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1",
        "wf-1",
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(dummyStep()));
    executionContext = new ExecutionContext(state, workflow, 32);
    blueprint = new BlueprintDefinition(
        BLUEPRINT_ID,
        "for-each blueprint",
        new BlueprintBehaviour(forEachConfig(false), StepTransition.AUTO),
        List.of(dummyStep()));
  }

  @Test
  void unchanged_list_resumes_at_stored_iteration() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> calls.getAndIncrement() == 0
            ? ExecutionOutcome.PAUSED
            : ExecutionOutcome.COMPLETED);

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isEqualTo(1);
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isPresent();

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // iteration 1 (paused), then resume re-runs iteration 1 and runs iteration 2
    assertThat(calls.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void mutated_list_fails_when_mutation_not_allowed() {
    putList("a", "b");
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.PAUSED);

    strategy.iterate(blueprint, forEachConfig(false), executionContext);
    putList("x", "y");

    assertThatThrownBy(() -> strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(LIST_KEY)
        .hasMessageContaining(BLUEPRINT_ID)
        .hasMessageContaining("run-1")
        .hasMessageContaining("allowForEachListMutation=true");
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void mutated_list_restarts_when_mutation_allowed() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> calls.getAndIncrement() == 0
            ? ExecutionOutcome.PAUSED
            : ExecutionOutcome.COMPLETED);

    strategy.iterate(blueprint, forEachConfig(true), executionContext);
    putList("x", "y");

    assertThat(strategy.iterate(blueprint, forEachConfig(true), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // iteration 1 (paused), then mutation restart runs iterations 1 and 2 on the new list
    assertThat(calls.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void cancelled_clears_cursor_and_fingerprint() {
    putList("a", "b");
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          state.setStatus(WorkflowStatus.CANCELLED);
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  private void putList(String... values) {
    List<ContextValue> items = new ArrayList<>();
    for (String value : values) {
      items.add(new StringContextValue(value));
    }
    state.putContextValue(LIST_KEY, new ContextValueList(items));
  }

  private static StepDefinition dummyStep() {
    return new StepDefinition(
        "dummy",
        "dummy",
        new ResourceBehaviour("/workflow-resources/info.txt", "dummy.out", StepTransition.AUTO),
        null,
        null,
        null);
  }

  private static LoopConfig forEachConfig(boolean allowMutation) {
    return new LoopConfig(
        LoopTerminationStrategy.FOR_EACH,
        LIST_KEY,
        null,
        10,
        null,
        allowMutation);
  }
}
