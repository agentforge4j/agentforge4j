// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.humanapproval;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A human-in-the-loop workflow: an agent step gated by a {@code HUMAN_APPROVAL} transition. The agent does its work and
 * emits {@code COMPLETE}, then the run suspends at {@code AWAITING_STEP_APPROVAL} until a person decides. Approving
 * advances the run to {@code COMPLETED}; rejecting fails it with a {@code StepRejectionFailure}.
 *
 * <p>The run is deterministic and offline. The {@code agentforge4j-llm-fake} provider serves one
 * scripted {@code COMPLETE} for the agent's call, so no real model, network, or API key is involved; the approve/reject
 * decisions are supplied directly in code.
 */
public final class HumanApprovalExample {

  /**
   * Workflow id; matches {@code workflows/human-approval.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "human-approval";

  /**
   * The gated step's id; matches the {@code stepId} in the workflow and the resume call.
   */
  static final String STEP_ID = "propose";

  /**
   * Agent id; matches {@code agents/human-approval-agent.agent/} and the step's {@code agentRef}.
   */
  static final String AGENT_ID = "human-approval-agent";

  /**
   * The scripted model output for the agent's single call. An LLM response is a bare JSON array of command objects; a
   * lone {@code COMPLETE} finishes the step, after which the approval gate suspends the run.
   */
  static final String SCRIPTED_COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  private HumanApprovalExample() {
  }

  /**
   * Runs the workflow twice — once approved, once rejected — and prints each outcome.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();

    // Approve path: start, suspend at the gate, approve, reach COMPLETED.
    String approvedRun = agentForge4j.start(WORKFLOW_ID);
    printStatus(agentForge4j, "approve path — after start", approvedRun);
    agentForge4j.runtime().decideStepApproval(approvedRun, STEP_ID,
        new StepApprovalDecision.Approve("alice", "Looks good"));
    printStatus(agentForge4j, "approve path — after approve", approvedRun);

    // Reject path: start, suspend at the gate, reject, fail with the rejection reason.
    String rejectedRun = agentForge4j.start(WORKFLOW_ID);
    printStatus(agentForge4j, "reject path — after start", rejectedRun);
    agentForge4j.runtime().decideStepApproval(rejectedRun, STEP_ID,
        new StepApprovalDecision.Reject("alice", "Needs rework"));
    WorkflowState rejected = agentForge4j.runtime().getState(rejectedRun);
    printStatus(agentForge4j, "reject path — after reject", rejectedRun);
    System.out.printf("  failure reason: %s%n", rejected.getFailureReason());
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider and to this module's own workflow and
   * agent configuration. Shared by {@link #main(String[])} and the test so both exercise exactly the same wiring.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, STEP_ID, AGENT_ID, 0),
        new FakeResponse(SCRIPTED_COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withAgentsDir(resourceDirectory("/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(new DefaultLlmClientResolver(List.of(fakeLlmClient)))
        .build();
  }

  private static void printStatus(AgentForge4j agentForge4j, String label, String runId) {
    WorkflowState state = agentForge4j.runtime().getState(runId);
    System.out.printf("%s: %s%n", label, state.getStatus());
  }

  /**
   * Resolves a directory on the classpath (e.g. {@code /workflows}) to a filesystem path. Works when the example runs
   * from exploded {@code target/classes} (IDE, tests, {@code mvn} build), which is how examples are run from source.
   *
   * @param classpathDirectory absolute classpath path of the directory
   *
   * @return the resolved filesystem path
   *
   * @throws URISyntaxException if the resource URL is not a valid URI
   */
  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        HumanApprovalExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
