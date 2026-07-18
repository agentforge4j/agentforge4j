// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.config.loader.workflow.ClasspathWorkflowLoader;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mechanical contract test: {@code agent-creator}'s shipped {@code workflow.json} declares
 * {@code retryPolicy} on 7 steps. This proves — through the real production loading path
 * ({@link ClasspathWorkflowLoader}), not a hand-crafted fixture — that all 7 declarations load
 * cleanly under the shrunk 3-field {@link RetryPolicy} shape (no {@code allowAgentSwap} /
 * {@code allowPromptOverride} left over) and match the intended governance contract:
 * {@code allowRetry=true}, {@code allowRetryFromPrevious=false}, {@code maxAttempts=2}.
 */
class AgentCreatorRetryPolicyContractTest {

  private static final Set<String> POLICY_CARRYING_STEP_IDS = Set.of(
      "structure-requirements",
      "assess",
      "design-preview",
      "estimate-tokens",
      "generate-agent",
      "generate-verification",
      "review");

  @Test
  void theSevenNamedStepsCarryTheExactThreeFieldContractPolicy() {
    // loadWorkflows() loads every shipped workflow, not only agent-creator; the production loading
    // path (ConfigurationLoader.defaultObjectMapper()) disables FAIL_ON_UNKNOWN_PROPERTIES for
    // forward-compatible, lenient deserialisation — matched here so an unrelated fixture field on a
    // sibling shipped workflow cannot fail this unrelated contract test.
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    WorkflowDirectoryLoad load = new ClasspathWorkflowLoader(mapper).loadWorkflows();
    WorkflowDefinition agentCreator = load.workflows().get("agent-creator");
    assertThat(agentCreator).as("agent-creator must be a shipped workflow").isNotNull();

    Map<String, RetryPolicy> declared = collectAgentRetryPolicies(agentCreator);

    // Pinned by step id, not just count: a policy silently migrating to a different step — or one
    // step's policy degrading to the undeclared/none() default — changes the key set and fails.
    // (A declared all-false policy normalises to RetryPolicy.none() and is indistinguishable from
    // an undeclared one, so "carries a policy" is defined as "differs from none()".)
    assertThat(declared.keySet())
        .as("exactly these agent-creator steps carry a governing retryPolicy")
        .containsExactlyInAnyOrderElementsOf(POLICY_CARRYING_STEP_IDS);
    RetryPolicy expected = new RetryPolicy(true, false, 2);
    declared.forEach((stepId, policy) ->
        assertThat(policy).as("retryPolicy on step '%s'", stepId).isEqualTo(expected));
  }

  private static Map<String, RetryPolicy> collectAgentRetryPolicies(WorkflowDefinition workflow) {
    Map<String, RetryPolicy> policies = new LinkedHashMap<>();
    collectAgentRetryPolicies(workflow.steps(), policies);
    return policies;
  }

  private static void collectAgentRetryPolicies(List<Executable> executables,
      Map<String, RetryPolicy> accumulator) {
    for (Executable executable : executables) {
      if (executable instanceof StepDefinition step
          && step.behaviour() instanceof AgentBehaviour agentBehaviour
          && !RetryPolicy.none().equals(agentBehaviour.retryPolicy())) {
        accumulator.put(step.stepId(), agentBehaviour.retryPolicy());
      }
      if (executable instanceof StepDefinition step
          && step.behaviour() instanceof BranchBehaviour branchBehaviour) {
        // Branch arms embed nested Executables directly (not present in the top-level steps
        // list), so a retryPolicy-declaring agent step reachable only through a branch would
        // otherwise be invisible to this scan.
        collectAgentRetryPolicies(branchBehaviour.childExecutables(), accumulator);
      }
    }
  }
}
