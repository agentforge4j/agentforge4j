// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.collection.AuthorizationMode;
import com.agentforge4j.core.workflow.collection.DuplicatePolicy;
import com.agentforge4j.core.workflow.collection.ReopenPolicy;
import com.agentforge4j.core.workflow.collection.ReplacementPolicy;
import com.agentforge4j.core.workflow.collection.WithdrawalPolicy;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.util.Validate;

/**
 * Step that suspends the run into a collection gate, accepting zero or more submissions over time
 * while the run stays paused, until the gate is explicitly closed (and optionally reopened). The
 * materialized collection is published to the step's declared context output key(s) on advance.
 *
 * <p>The closability invariant ({@code manualClose || externalDeadlineClosable}) and the
 * {@code reopenPolicy == ALLOWED} requires {@code manualClose} rule are cross-field configuration
 * checks enforced at config-load time; this record validates only intrinsic field bounds.
 *
 * @param itemSchemaRef            optional id of a JSON schema validating each item's inline JSON;
 *                                 {@code null} when none
 * @param minItems                minimum non-withdrawn items required to close; {@code 0} allows
 *                                 closing with no submissions
 * @param maxItems                maximum items accepted; {@code null} means unbounded
 * @param maxItemsPerActor        per-actor submission cap; {@code null} means uncapped
 * @param maxInlinePayloadBytes   cap on a single inline JSON payload; defaulted when not positive
 * @param duplicatePolicy         duplicate handling; defaults to {@link DuplicatePolicy#ALLOW}
 * @param replacementPolicy       replacement handling; defaults to {@link ReplacementPolicy#NONE}
 * @param withdrawalPolicy        withdrawal handling; defaults to {@link WithdrawalPolicy#NONE}
 * @param manualClose             whether a human close is permitted; defaults to {@code true}
 * @param externalDeadlineClosable whether an externally-driven deadline close is permitted; defaults
 *                                 to {@code false}
 * @param reopenPolicy            reopen handling; defaults to {@link ReopenPolicy#NONE}
 * @param authorizationMode       authorization handling; defaults to {@link AuthorizationMode#OPEN}
 * @param transition              non-null gate after the gate closes; defaults to
 *                                {@link StepTransition#AUTO}
 */
public record CollectionBehaviour(
    String itemSchemaRef,
    int minItems,
    Integer maxItems,
    Integer maxItemsPerActor,
    int maxInlinePayloadBytes,
    DuplicatePolicy duplicatePolicy,
    ReplacementPolicy replacementPolicy,
    WithdrawalPolicy withdrawalPolicy,
    Boolean manualClose,
    Boolean externalDeadlineClosable,
    ReopenPolicy reopenPolicy,
    AuthorizationMode authorizationMode,
    StepTransition transition
) implements StepBehaviour, TransitionAware {

  /**
   * Default cap on a single inline JSON payload (64 KiB) applied when a non-positive value is given.
   */
  public static final int DEFAULT_MAX_INLINE_PAYLOAD_BYTES = 64 * 1024;

  public CollectionBehaviour {
    Validate.isTrue(itemSchemaRef == null || !itemSchemaRef.isBlank(),
        "CollectionBehaviour itemSchemaRef must be null or non-blank");
    Validate.isNotNegative(minItems, "CollectionBehaviour minItems must not be negative");
    if (maxItems != null) {
      Validate.isGreaterThanZero(maxItems, "CollectionBehaviour maxItems must be at least 1");
      Validate.isTrue(maxItems >= minItems,
          "CollectionBehaviour maxItems must be greater than or equal to minItems");
    }
    if (maxItemsPerActor != null) {
      Validate.isGreaterThanZero(maxItemsPerActor,
          "CollectionBehaviour maxItemsPerActor must be at least 1");
    }
    maxInlinePayloadBytes =
        maxInlinePayloadBytes > 0 ? maxInlinePayloadBytes : DEFAULT_MAX_INLINE_PAYLOAD_BYTES;
    duplicatePolicy = duplicatePolicy != null ? duplicatePolicy : DuplicatePolicy.ALLOW;
    replacementPolicy = replacementPolicy != null ? replacementPolicy : ReplacementPolicy.NONE;
    withdrawalPolicy = withdrawalPolicy != null ? withdrawalPolicy : WithdrawalPolicy.NONE;
    manualClose = manualClose != null ? manualClose : Boolean.TRUE;
    externalDeadlineClosable =
        externalDeadlineClosable != null ? externalDeadlineClosable : Boolean.FALSE;
    reopenPolicy = reopenPolicy != null ? reopenPolicy : ReopenPolicy.NONE;
    authorizationMode = authorizationMode != null ? authorizationMode : AuthorizationMode.OPEN;
    transition = transition != null ? transition : StepTransition.AUTO;
  }
}
