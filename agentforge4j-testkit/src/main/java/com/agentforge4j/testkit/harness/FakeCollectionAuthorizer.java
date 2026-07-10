// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import com.agentforge4j.core.workflow.collection.CollectionAction;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizer;
import com.agentforge4j.core.workflow.collection.Decision;
import com.agentforge4j.core.workflow.requirement.ResolutionContext;
import com.agentforge4j.util.Validate;
import java.util.HashSet;
import java.util.Set;

/**
 * Deterministic {@link CollectionAuthorizer} for tests of {@code ENFORCED} collection gates. Either
 * allows everything, denies everything, or allows a specific set of {@code (actorId, action)} pairs
 * and denies the rest. Wire it through
 * {@code AgentForge4jBootstrap.Builder.withCollectionAuthorizer(...)} or
 * {@code WorkflowRuntimeBuilder.collectionAuthorizer(...)}.
 */
public final class FakeCollectionAuthorizer implements CollectionAuthorizer {

  private final boolean allowAll;
  private final Set<String> allowedKeys;

  private FakeCollectionAuthorizer(boolean allowAll, Set<String> allowedKeys) {
    this.allowAll = allowAll;
    this.allowedKeys = Set.copyOf(allowedKeys);
  }

  /**
   * @return an authorizer that allows every guarded action for any actor
   */
  public static FakeCollectionAuthorizer allowAll() {
    return new FakeCollectionAuthorizer(true, Set.of());
  }

  /**
   * @return an authorizer that denies every guarded action
   */
  public static FakeCollectionAuthorizer denyAll() {
    return new FakeCollectionAuthorizer(false, Set.of());
  }

  /**
   * @param actorId the actor to permit; must not be blank
   * @param actions the actions to permit for that actor; must not be empty
   *
   * @return an authorizer that allows exactly the given actor/action pairs and denies all others
   */
  public static FakeCollectionAuthorizer permitting(String actorId, CollectionAction... actions) {
    Validate.notBlank(actorId, "actorId must not be blank");
    Validate.notNull(actions, "actions must not be null");
    Validate.isTrue(actions.length > 0, "actions must not be empty");
    Set<String> keys = new HashSet<>();
    for (CollectionAction action : actions) {
      keys.add(key(actorId, action));
    }
    return new FakeCollectionAuthorizer(false, keys);
  }

  @Override
  public Decision authorize(String actorId, String stepId, CollectionAction action,
      String requirementDescriptor, ResolutionContext context) {
    if (allowAll || allowedKeys.contains(key(actorId, action))) {
      return Decision.allow();
    }
    return Decision.deny("FakeCollectionAuthorizer denies '%s' for actor '%s'"
        .formatted(action.wire(), actorId));
  }

  private static String key(String actorId, CollectionAction action) {
    return "%s|%s".formatted(actorId, action.wire());
  }
}
