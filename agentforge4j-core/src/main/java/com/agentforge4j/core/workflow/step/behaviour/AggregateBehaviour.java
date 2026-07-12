// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.util.Validate;

/**
 * Deterministic, in-workflow aggregation: runs the registered {@code ContextAggregator} named by
 * {@code aggregatorId} over the step's declared {@code contextMapping} input keys and writes each
 * returned logical field back to context under {@code <outputContextKeyPrefix>.<fieldName>}. Lets
 * a workflow expose a derived structured value (for example a token-envelope-and-recommendation
 * report) that the engine itself cannot express via {@code SET_CONTEXT}/{@code ASSIGN_CONTEXT}
 * (which write literals only), without a host-side seam.
 *
 * @param aggregatorId           non-blank id of the registered {@code ContextAggregator} to apply
 * @param outputContextKeyPrefix non-blank prefix every returned field is written under, joined with
 *                               {@code "."}
 * @param transition             non-null gate after aggregation completes
 */
public record AggregateBehaviour(
    String aggregatorId,
    String outputContextKeyPrefix,
    StepTransition transition
) implements StepBehaviour, TransitionAware {

  public AggregateBehaviour {
    Validate.notBlank(aggregatorId, "AggregateBehaviour aggregatorId must not be blank");
    Validate.notBlank(outputContextKeyPrefix,
        "AggregateBehaviour outputContextKeyPrefix must not be blank");
    Validate.notNull(transition, "AggregateBehaviour transition must not be null");
  }
}
