// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.requirement;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * A central, self-targeting declaration of something a workflow needs satisfied.
 *
 * <p>The declaration is opaque to {@code core}: {@code type} and {@code defaultJson} are never
 * interpreted here. {@code type} names the kind of requirement (interpreted by the configured
 * {@link RequirementResolver}); {@code defaultJson} carries the optional default as raw JSON text. {@code core} owns
 * only the structural shape, load-time structural validation, and the run-start resolution checkpoint.
 *
 * @param id          unique (within a workflow) requirement id
 * @param type        opaque requirement type; interpreted by the configured {@link RequirementResolver}
 * @param scope       target granularity (workflow, step, or step-action)
 * @param stepId      target step id when scope is {@link RequirementScope#STEP} or
 *                    {@link RequirementScope#STEP_ACTION}; otherwise {@code null}
 * @param action      opaque target action when scope is {@link RequirementScope#STEP_ACTION}; otherwise {@code null}
 * @param required    whether the workflow cannot run unless this requirement resolves
 * @param defaultJson optional default value as raw JSON text; {@code null} when absent
 * @param resolution  when the requirement is expected to be resolved
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowRequirement(
    String id,
    String type,
    RequirementScope scope,
    String stepId,
    String action,
    boolean required,
    @JsonProperty("default")
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    String defaultJson,
    ResolutionMode resolution
) {

  public WorkflowRequirement {
    Validate.notBlank(id, "WorkflowRequirement id must not be blank");
    Validate.notBlank(type, "WorkflowRequirement type must not be blank for requirement: %s".formatted(id));
    Validate.notNull(scope, "WorkflowRequirement scope must not be null for requirement: %s".formatted(id));
    Validate.notNull(resolution, "WorkflowRequirement resolution must not be null for requirement: %s".formatted(id));
    stepId = StringUtils.defaultIfBlank(stepId, null);
    action = StringUtils.defaultIfBlank(action, null);
    defaultJson = StringUtils.defaultIfBlank(defaultJson, null);
    switch (scope) {
      case WORKFLOW -> {
        requireAbsent(stepId, id, "WORKFLOW", "a stepId");
        requireAbsent(action, id, "WORKFLOW", "an action");
      }
      case STEP -> {
        requirePresent(stepId, id, "STEP", "a stepId");
        requireAbsent(action, id, "STEP", "an action");
      }
      case STEP_ACTION -> {
        requirePresent(stepId, id, "STEP_ACTION", "a stepId");
        requirePresent(action, id, "STEP_ACTION", "an action");
      }
    }
  }

  private static void requirePresent(String value, String id, String scope, String field) {
    Validate.notBlank(value, "WorkflowRequirement '%s' with %s scope requires %s".formatted(id, scope, field));
  }

  private static void requireAbsent(String value, String id, String scope, String field) {
    Validate.isTrue(value == null, "WorkflowRequirement '%s' with %s scope must not declare %s".formatted(id, scope, field));
  }

  /**
   * Finds the {@link RequirementScope#STEP_ACTION} requirement in {@code requirements} targeting
   * {@code stepId} and {@code action}, if any. Shared by both load-time validation and runtime
   * authorization so the matching rule is defined in exactly one place.
   *
   * @param requirements the requirements to search
   * @param stepId       the target step id
   * @param action       the opaque target action (e.g. a {@code CollectionAction.wire()} value)
   *
   * @return the matching requirement, or {@code null} when none matches
   */
  public static WorkflowRequirement findStepAction(List<WorkflowRequirement> requirements,
      String stepId, String action) {
    for (WorkflowRequirement requirement : requirements) {
      if (requirement.scope() == RequirementScope.STEP_ACTION
          && stepId.equals(requirement.stepId())
          && action.equals(requirement.action())) {
        return requirement;
      }
    }
    return null;
  }
}
