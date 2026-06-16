// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.requirement;

import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * Opaque inputs handed to a {@link RequirementResolver} when resolving a {@link WorkflowRequirement}.
 *
 * <p>Carries no tenant, user, or role concept — {@code core} has none. A resolving layer that needs such context (for
 * example the embedding application) derives it from {@code runId} internally.
 *
 * @param workflowId    the workflow whose requirement is being resolved; never blank
 * @param runId         the run id when resolving at run start; {@code null} when there is no run (for example
 *                      install-time resolution)
 * @param contextValues opaque key/value context available to the resolver; never {@code null}
 */
public record ResolutionContext(String workflowId, String runId, Map<String, String> contextValues) {

  public ResolutionContext {
    Validate.notBlank(workflowId, "ResolutionContext workflowId must not be blank");
    contextValues = contextValues != null ? Map.copyOf(contextValues) : Map.of();
  }
}
