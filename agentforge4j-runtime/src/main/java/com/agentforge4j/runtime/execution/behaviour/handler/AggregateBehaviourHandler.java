// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.spi.aggregation.AggregationContext;
import com.agentforge4j.core.spi.aggregation.ContextAggregator;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AggregateBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the registered {@link ContextAggregator} named by an {@link AggregateBehaviour}'s
 * {@code aggregatorId} over the step's declared {@code contextMapping} input keys, and writes each
 * returned logical field back to context under {@code <outputContextKeyPrefix>.<fieldName>},
 * re-stamping every written value's provenance as {@link ContextProvenance#SYSTEM_GENERATED} (the
 * value originates from trusted, deterministic Java, not a model) — mirroring
 * {@link AssignContextBehaviourHandler}'s re-stamping convention. An unresolvable
 * {@code aggregatorId}, a missing declared input key, or a {@code null} aggregator result fails the
 * run closed; deterministic and always completes otherwise.
 */
public final class AggregateBehaviourHandler implements BehaviourHandler<AggregateBehaviour> {

  private static final System.Logger LOG = System.getLogger(AggregateBehaviourHandler.class.getName());

  private final Map<String, ContextAggregator> aggregatorsById;
  private final EventRecorder eventRecorder;

  /**
   * Creates a handler over the registered aggregators.
   *
   * @param aggregators   aggregators registered by their {@code aggregatorId} (no duplicates)
   * @param eventRecorder audit sink for the context-update / failure events
   */
  public AggregateBehaviourHandler(List<ContextAggregator> aggregators, EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.aggregatorsById = indexAggregators(Validate.notNull(aggregators, "aggregators must not be null"));
  }

  @Override
  public Class<AggregateBehaviour> behaviourType() {
    return AggregateBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step, AggregateBehaviour behaviour,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    LOG.log(System.Logger.Level.DEBUG, "Aggregate behaviour start stepId={0}, aggregatorId={1}",
        step.stepId(), behaviour.aggregatorId());

    ContextAggregator aggregator = aggregatorsById.get(behaviour.aggregatorId());
    if (aggregator == null) {
      throw fail(step, state,
          "no ContextAggregator registered for aggregatorId '%s'".formatted(behaviour.aggregatorId()));
    }

    AggregationContext aggregationContext = buildAggregationContext(step, state);
    Map<String, ContextValue> result = aggregator.aggregate(aggregationContext);
    if (result == null) {
      throw fail(step, state,
          "aggregator '%s' returned a null result".formatted(behaviour.aggregatorId()));
    }

    writeResult(step, state, behaviour, result);
    return ExecutionOutcome.COMPLETED;
  }

  private AggregationContext buildAggregationContext(StepDefinition step, WorkflowState state) {
    Map<String, ContextValue> values = new LinkedHashMap<>();
    for (String key : step.contextMapping().inputKeys()) {
      ContextValue value = state.getContextValue(key)
          .orElseThrow(() -> fail(step, state,
              "declared input context key '%s' is missing".formatted(key)));
      values.put(key, value);
    }
    Map<String, ContextValue> immutableValues = Map.copyOf(values);
    return () -> immutableValues;
  }

  private void writeResult(StepDefinition step, WorkflowState state, AggregateBehaviour behaviour,
      Map<String, ContextValue> result) {
    String prefix = behaviour.outputContextKeyPrefix();
    for (Map.Entry<String, ContextValue> entry : result.entrySet()) {
      state.putContextValue(prefix + "." + entry.getKey(),
          entry.getValue().withProvenance(ContextProvenance.SYSTEM_GENERATED));
    }
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.CONTEXT_UPDATED,
        "aggregated %d field(s) under prefix: %s".formatted(result.size(), prefix), "runtime");
  }

  private StepExecutionException fail(StepDefinition step, WorkflowState state, String reason) {
    String message = "Aggregation failed at step '%s': %s".formatted(step.stepId(), reason);
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_FAILED, message, "runtime");
    return new StepExecutionException(message);
  }

  private static Map<String, ContextAggregator> indexAggregators(List<ContextAggregator> aggregators) {
    Map<String, ContextAggregator> byId = new LinkedHashMap<>();
    for (ContextAggregator aggregator : aggregators) {
      Validate.notNull(aggregator, "aggregators must not contain null entries");
      String id = Validate.notBlank(aggregator.aggregatorId(),
          "ContextAggregator aggregatorId must not be blank");
      ContextAggregator previous = byId.putIfAbsent(id, aggregator);
      Validate.isTrue(previous == null, "Duplicate ContextAggregator for id '%s'".formatted(id));
    }
    return byId;
  }
}
