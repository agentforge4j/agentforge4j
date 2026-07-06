// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.core.workflow.requirement.ResolutionContext;

/**
 * Pure-{@code core} {@link CollectionAuthorizer} that denies every guarded action.
 *
 * <p>It is only consulted in {@code ENFORCED} authorization mode, and {@code core} cannot evaluate
 * real permissions, so the safe default is to deny — a gate configured {@code ENFORCED} without a
 * richer authorizer wired by the embedding application admits no guarded operation. {@code OPEN}
 * mode never reaches this SPI.
 */
public final class DefaultCollectionAuthorizer implements CollectionAuthorizer {

  @Override
  public Decision authorize(String actorId, String stepId, CollectionAction action,
      String requirementDescriptor, ResolutionContext context) {
    return Decision.deny(
        "No CollectionAuthorizer configured for ENFORCED mode; action '%s' denied"
            .formatted(action.wire()));
  }
}
