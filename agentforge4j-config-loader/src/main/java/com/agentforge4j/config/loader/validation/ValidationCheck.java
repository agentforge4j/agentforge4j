// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.spi.contextpack.ContextPack;
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
   * @param validator         the {@link WorkflowValidator} instance whose methods back each check
   * @param loadedPacksByName the context packs actually loaded for this assembly, keyed by name;
   *                          empty when none are configured. Closed over by the
   *                          {@code validateContextSelectionRefs} check, whose {@code CONTEXT_PACK}
   *                          selectors resolve against exactly the packs this assembly loaded
   * @return immutable ordered list of checks
   */
  public static List<ValidationCheck> suite(WorkflowValidator validator,
      Map<String, ContextPack> loadedPacksByName) {
    Validate.notNull(loadedPacksByName, "loadedPacksByName must not be null");
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
        new ValidationCheck("validateContextSelectionRefs", false,
            (workflows, agents) -> validator.validateContextSelectionRefs(workflows, loadedPacksByName)),
        new ValidationCheck("validateLedgerSchemas", false,
            (workflows, agents) -> validator.validateLedgerSchemas(workflows)),
        new ValidationCheck("validateNoCollectionSteps", false,
            (workflows, agents) -> validator.validateNoCollectionSteps(workflows))
    );
  }
}
