// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.blueprint;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.util.Validate;

import java.util.List;

/**
 * Reusable subgraph: id, display name, loop and transition behaviour, and an ordered list of child
 * executables. A blueprint is a standalone definition resolved via {@link BlueprintRef}; it is
 * deliberately not itself an {@link Executable} and is never embedded inline in a {@code steps()}
 * array.
 *
 * @param blueprintId non-blank stable id
 * @param name        non-blank human-readable name
 * @param behaviour   non-null loop and transition wrapper
 * @param steps       non-empty immutable copy of child steps or nested executables
 */
public record BlueprintDefinition(
    String blueprintId,
    String name,
    BlueprintBehaviour behaviour,
    List<Executable> steps
) {

  public BlueprintDefinition {
    Validate.notBlank(blueprintId, "BlueprintDefinition blueprintId must not be blank");
    Validate.notBlank(name,
        "BlueprintDefinition name must not be blank for blueprint: %s".formatted(blueprintId));
    Validate.notNull(behaviour,
        "BlueprintDefinition behaviour must not be null for blueprint: %s".formatted(blueprintId));
    Validate.notEmpty(steps,
        "BlueprintDefinition steps must not be empty for blueprint: %s".formatted(blueprintId));
    steps = List.copyOf(steps);
  }

  public static BlueprintDefinition duplicate(BlueprintDefinition blueprint,
      List<Executable> executables) {
    return new BlueprintDefinition(
        blueprint.blueprintId(),
        blueprint.name(),
        blueprint.behaviour(),
        executables);
  }
}
