// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.core.workflow.requirement.ResolutionContext;

/**
 * SPI consulted before a guarded collection operation runs in {@code ENFORCED} authorization mode.
 *
 * <p>The runtime resolves the matching {@code STEP_ACTION} requirement value and passes it as
 * {@code requirementDescriptor}; an implementation decides whether {@code actorId} may perform
 * {@code action} on the gate. {@code core} ships {@link DefaultCollectionAuthorizer}, which denies
 * everything (fail-closed); the embedding application supplies a richer, identity- and role-aware
 * implementation. In {@code OPEN} mode the runtime does not call this SPI.
 */
public interface CollectionAuthorizer {

  /**
   * Decides whether {@code actorId} may perform {@code action} on the collection gate at
   * {@code stepId}.
   *
   * @param actorId               the opaque effective actor; never blank when called
   * @param stepId                the collection step id; never blank
   * @param action                the guarded action being attempted; never {@code null}
   * @param requirementDescriptor the resolved {@code STEP_ACTION} requirement value, or {@code null}
   *                              when the requirement resolved to no value
   * @param context               the resolution context for the run; never {@code null}
   *
   * @return an allow or deny {@link Decision}; never {@code null}
   */
  Decision authorize(String actorId, String stepId, CollectionAction action,
      String requirementDescriptor, ResolutionContext context);
}
