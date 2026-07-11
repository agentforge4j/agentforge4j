// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.spi.aggregation.AggregationContext;
import com.agentforge4j.core.spi.aggregation.ContextAggregator;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AggregateBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AggregateBehaviourHandlerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
  private static final String AGGREGATOR_ID = "double-it";

  private final InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();

  private static ContextAggregator aggregator(String id,
      Function<AggregationContext, Map<String, ContextValue>> fn) {
    return new ContextAggregator() {
      @Override
      public String aggregatorId() {
        return id;
      }

      @Override
      public Map<String, ContextValue> aggregate(AggregationContext context) {
        return fn.apply(context);
      }
    };
  }

  private AggregateBehaviourHandler handler(List<ContextAggregator> aggregators) {
    return new AggregateBehaviourHandler(aggregators, new EventRecorder(eventLog, CLOCK));
  }

  private static WorkflowState stateWithStep(String stepId) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, CLOCK.instant());
    state.setCurrentStepId(stepId);
    return state;
  }

  private static ExecutionContext context(WorkflowState state, StepDefinition step) {
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf-1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step), List.of());
    return new ExecutionContext(state, wf, 32);
  }

  private static StepDefinition aggregateStep(AggregateBehaviour behaviour, List<String> inputKeys) {
    return StepDefinition.builder()
        .withStepId("a1")
        .withName("A")
        .withBehaviour(behaviour)
        .withContextMapping(new ContextMapping(inputKeys, List.of()))
        .build();
  }

  private String stepFailedAudit() {
    return eventLog.getEvents("run-1").stream()
        .filter(e -> e.eventType() == com.agentforge4j.core.workflow.event.WorkflowEventType.STEP_FAILED)
        .map(com.agentforge4j.core.workflow.event.WorkflowEvent::payload)
        .reduce("", (a, b) -> a + b);
  }

  @Test
  void resolves_aggregator_and_writes_prefixed_results_restamped_system_generated() {
    ContextAggregator doubler = aggregator(AGGREGATOR_ID, ctx -> Map.of(
        "doubled", new NumberContextValue(
            ctx.values().get("input").provenance() == ContextProvenance.USER_SUPPLIED
                ? ((NumberContextValue) ctx.values().get("input")).value().intValue() * 2
                : -1,
            ContextProvenance.LLM_GENERATED)));
    WorkflowState state = stateWithStep("a1");
    state.putContextValue("input", new NumberContextValue(21, ContextProvenance.USER_SUPPLIED));
    AggregateBehaviour behaviour =
        new AggregateBehaviour(AGGREGATOR_ID, "result", StepTransition.AUTO);
    StepDefinition step = aggregateStep(behaviour, List.of("input"));

    ExecutionOutcome outcome =
        handler(List.of(doubler)).handle(step, behaviour, context(state, step));

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    ContextValue written = state.getContextValue("result.doubled").orElseThrow();
    assertThat(written).isInstanceOf(NumberContextValue.class);
    assertThat(((NumberContextValue) written).value()).isEqualTo(42);
    // The handler re-stamps every returned value SYSTEM_GENERATED regardless of what the
    // aggregator returned (LLM_GENERATED above) — the value originates from trusted, deterministic
    // Java, not a model.
    assertThat(written.provenance()).isEqualTo(ContextProvenance.SYSTEM_GENERATED);
  }

  @Test
  void aggregator_sees_only_its_own_step_declared_input_keys() {
    List<String> seenKeys = new java.util.ArrayList<>();
    ContextAggregator recorder = aggregator(AGGREGATOR_ID, ctx -> {
      seenKeys.addAll(ctx.values().keySet());
      return Map.of("noted", new StringContextValue("ok", ContextProvenance.SYSTEM_GENERATED));
    });
    WorkflowState state = stateWithStep("a1");
    state.putContextValue("declared", new StringContextValue("x", ContextProvenance.USER_SUPPLIED));
    state.putContextValue("undeclared", new StringContextValue("y", ContextProvenance.USER_SUPPLIED));
    AggregateBehaviour behaviour = new AggregateBehaviour(AGGREGATOR_ID, "out", StepTransition.AUTO);
    StepDefinition step = aggregateStep(behaviour, List.of("declared"));

    handler(List.of(recorder)).handle(step, behaviour, context(state, step));

    assertThat(seenKeys).containsExactly("declared");
  }

  @Test
  void fails_on_unknown_aggregator_id() {
    WorkflowState state = stateWithStep("a1");
    AggregateBehaviour behaviour = new AggregateBehaviour("missing", "out", StepTransition.AUTO);
    StepDefinition step = aggregateStep(behaviour, List.of());

    assertThatThrownBy(() -> handler(List.of()).handle(step, behaviour, context(state, step)))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("no ContextAggregator registered for aggregatorId 'missing'");
    assertThat(stepFailedAudit()).contains("no ContextAggregator registered");
  }

  @Test
  void fails_when_declared_input_key_missing_from_context() {
    ContextAggregator ok = aggregator(AGGREGATOR_ID, ctx -> Map.of());
    WorkflowState state = stateWithStep("a1");
    AggregateBehaviour behaviour = new AggregateBehaviour(AGGREGATOR_ID, "out", StepTransition.AUTO);
    StepDefinition step = aggregateStep(behaviour, List.of("missing-key"));

    assertThatThrownBy(() -> handler(List.of(ok)).handle(step, behaviour, context(state, step)))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("declared input context key 'missing-key' is missing");
  }

  @Test
  void fails_when_aggregator_returns_null() {
    ContextAggregator nullReturning = aggregator(AGGREGATOR_ID, ctx -> null);
    WorkflowState state = stateWithStep("a1");
    AggregateBehaviour behaviour = new AggregateBehaviour(AGGREGATOR_ID, "out", StepTransition.AUTO);
    StepDefinition step = aggregateStep(behaviour, List.of());

    assertThatThrownBy(() -> handler(List.of(nullReturning))
        .handle(step, behaviour, context(state, step)))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("returned a null result");
  }

  @Test
  void constructor_rejects_duplicate_aggregator_ids() {
    ContextAggregator a = aggregator(AGGREGATOR_ID, ctx -> Map.of());
    ContextAggregator b = aggregator(AGGREGATOR_ID, ctx -> Map.of());

    assertThatThrownBy(() -> handler(List.of(a, b)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate ContextAggregator");
  }
}
