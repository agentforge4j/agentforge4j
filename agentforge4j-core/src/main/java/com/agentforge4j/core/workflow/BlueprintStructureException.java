// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

/**
 * Thrown by {@link WorkflowTreeWalker} when a workflow's structural shape is broken — a
 * {@link com.agentforge4j.core.workflow.step.blueprint.BlueprintRef} that does not resolve, or
 * nesting past the walk's configured depth limit (indicating a circular blueprint reference).
 *
 * <p>Distinguished from a plain {@link IllegalArgumentException} so a check whose own concern is
 * unrelated to blueprint structure (workflow refs, artifact refs, agent refs, retry refs,
 * validate-behaviour contracts) can recognize this specific failure and defer reporting it to
 * whichever check owns blueprint-structural validation, instead of misattributing or duplicating
 * it.
 */
public final class BlueprintStructureException extends IllegalArgumentException {

  public BlueprintStructureException(String message) {
    super(message);
  }
}
