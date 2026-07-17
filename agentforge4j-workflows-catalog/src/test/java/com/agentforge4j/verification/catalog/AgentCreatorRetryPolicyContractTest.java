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
import java.util.ArrayList;
import java.util.List;
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

  @Test
  void allSevenDeclaredRetryPoliciesMatchTheThreeFieldContract() {
    // loadWorkflows() loads every shipped workflow, not only agent-creator; the production loading
    // path (ConfigurationLoader.defaultObjectMapper()) disables FAIL_ON_UNKNOWN_PROPERTIES for
    // forward-compatible, lenient deserialisation — matched here so an unrelated fixture field on a
    // sibling shipped workflow cannot fail this unrelated contract test.
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    WorkflowDirectoryLoad load = new ClasspathWorkflowLoader(mapper).loadWorkflows();
    WorkflowDefinition agentCreator = load.workflows().get("agent-creator");
    assertThat(agentCreator).as("agent-creator must be a shipped workflow").isNotNull();

    List<RetryPolicy> declared = collectRetryPolicies(agentCreator);

    assertThat(declared)
        .as("agent-creator declares retryPolicy on exactly 7 steps")
        .hasSize(7);
    for (RetryPolicy policy : declared) {
      assertThat(policy.allowRetry()).isTrue();
      assertThat(policy.allowRetryFromPrevious()).isFalse();
      assertThat(policy.maxAttempts()).isEqualTo(2);
    }
  }

  private static List<RetryPolicy> collectRetryPolicies(WorkflowDefinition workflow) {
    List<RetryPolicy> policies = new ArrayList<>();
    collectRetryPolicies(workflow.steps(), policies);
    return policies;
  }

  private static void collectRetryPolicies(List<Executable> executables,
      List<RetryPolicy> accumulator) {
    for (Executable executable : executables) {
      if (executable instanceof StepDefinition step
          && step.behaviour() instanceof AgentBehaviour agentBehaviour
          && (agentBehaviour.retryPolicy().allowRetry()
              || agentBehaviour.retryPolicy().allowRetryFromPrevious())) {
        accumulator.add(agentBehaviour.retryPolicy());
      }
      if (executable instanceof StepDefinition step
          && step.behaviour() instanceof BranchBehaviour branchBehaviour) {
        // Branch arms embed nested Executables directly (not present in the top-level steps
        // list), so a retryPolicy-declaring agent step reachable only through a branch would
        // otherwise be invisible to this scan.
        collectRetryPolicies(branchBehaviour.childExecutables(), accumulator);
      }
    }
  }
}
