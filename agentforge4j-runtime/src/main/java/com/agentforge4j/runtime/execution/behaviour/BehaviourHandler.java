// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;

/**
 * Handles a single {@link StepBehaviour} type.
 *
 * <p>Implementations are keyed by {@link #behaviourType()} and looked up by
 * {@code StepExecutor}.
 *
 * @param <B> the concrete {@code StepBehaviour} subtype handled
 */
public interface BehaviourHandler<B extends StepBehaviour> {

  /**
   * @return the concrete behaviour class this handler supports
   */
  Class<B> behaviourType();

  /**
   * Execute the given step's behaviour.
   *
   * @param step             the owning step — provides step id and context mapping
   * @param behaviour        the typed behaviour instance
   * @param executionContext the run-time execution context
   * @return outcome indicating whether to advance, pause, signal completion, or fail
   */
  ExecutionOutcome handle(StepDefinition step, B behaviour, ExecutionContext executionContext);
}
