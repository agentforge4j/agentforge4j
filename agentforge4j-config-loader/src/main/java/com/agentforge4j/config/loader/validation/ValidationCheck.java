// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Single ordered registry of the named workflow-validation checks, shared by every validation entry
 * point so the suite of checks cannot drift between them. Two legitimate, different <em>runner</em>
 * strategies consume the same {@link #suite(WorkflowValidator)}: the loader's fail-fast runner (throws
 * on the first failing check) and the draft validator's collect-all runner (accumulates every failure
 * into a report) — only the run strategy differs, never the list of checks.
 *
 * @param code        stable identifier for the check, used in loader logs and draft
 *                    {@link ValidationError#code()} values
 * @param draftExempt {@code true} when draft validation deliberately skips this check; {@code false}
 *                    means every entry point runs it. No current check is exempt — the flag exists so a
 *                    future exemption is an explicit, documented decision rather than a silent omission
 * @param action      the validation action; receives the loaded workflows and agents
 */
public record ValidationCheck(
    String code,
    boolean draftExempt,
    BiConsumer<Map<String, WorkflowDefinition>, Map<String, AgentDefinition>> action
) {

  public ValidationCheck {
    Validate.notBlank(code, "ValidationCheck code must not be blank");
    Validate.notNull(action, "ValidationCheck action must not be null");
  }

  /**
   * Builds the ordered suite of workflow validation checks backed by {@code validator}.
   *
   * @param validator the {@link WorkflowValidator} instance whose methods back each check
   * @return immutable ordered list of checks
   */
  public static List<ValidationCheck> suite(WorkflowValidator validator) {
    return List.of(
        new ValidationCheck("validateWorkflowRefs", false,
            (workflows, agents) -> validator.validateWorkflowRefs(workflows)),
        new ValidationCheck("validateBlueprintRefs", false,
            (workflows, agents) -> validator.validateBlueprintRefs(workflows)),
        new ValidationCheck("validateAgentRefs", false,
            (workflows, agents) -> validator.validateAgentRefs(workflows, agents)),
        new ValidationCheck("validateArtifactRefs", false,
            (workflows, agents) -> validator.validateArtifactRefs(workflows)),
        new ValidationCheck("validateCircularRefs", false,
            (workflows, agents) -> validator.validateCircularRefs(workflows)),
        new ValidationCheck("validateReachableStepIdUniqueness", false,
            (workflows, agents) -> validator.validateReachableStepIdUniqueness(workflows)),
        new ValidationCheck("validateRetryStepRefs", false,
            (workflows, agents) -> validator.validateRetryStepRefs(workflows)),
        new ValidationCheck("validateRequirements", false,
            (workflows, agents) -> validator.validateRequirements(workflows)),
        new ValidationCheck("validateValidateBehaviourContracts", false,
            (workflows, agents) -> validator.validateValidateBehaviourContracts(workflows)),
        new ValidationCheck("validateNoCollectionSteps", false,
            (workflows, agents) -> validator.validateNoCollectionSteps(workflows))
    );
  }
}
