// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.util.Validate;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Thrown when a guarded collection operation is denied by the gate's own authorization path (missing requirement,
 * no/failed authorizer, or an explicit deny; also a blank actor reaching that path directly). Such a denial is audited
 * as {@code COLLECTION_AUTHORIZATION_DENIED} before this is thrown. A blank actor rejected earlier, at the
 * {@code CollectionGateRuntime} method boundary, throws {@link IllegalArgumentException} instead and is not audited —
 * the same non-blank-actor precondition every other actor-taking runtime verb enforces. The embedding application maps
 * this exception to its access-denied response.
 */
@Getter
@Accessors(fluent = true)
public final class CollectionAuthorizationException extends RuntimeException {

  private final String actorId;
  private final String stepId;
  private final CollectionAction action;

  /**
   * @param actorId the denied actor (may be {@code null}/blank when the denial was for a missing actor)
   * @param stepId  the collection step id; must not be blank
   * @param action  the denied action; must not be {@code null}
   * @param reason  non-blank denial reason
   */
  public CollectionAuthorizationException(String actorId, String stepId, CollectionAction action, String reason) {
    super("Collection action '%s' denied on step '%s': %s".formatted(
        Validate.notNull(action, "action must not be null").wire(),
        Validate.notBlank(stepId, "stepId must not be blank"),
        Validate.notBlank(reason, "reason must not be blank")));
    this.actorId = actorId;
    this.stepId = stepId;
    this.action = action;
  }
}
