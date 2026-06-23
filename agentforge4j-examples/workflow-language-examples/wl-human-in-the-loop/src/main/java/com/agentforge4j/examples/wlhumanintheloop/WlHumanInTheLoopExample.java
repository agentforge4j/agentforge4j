// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlhumanintheloop;

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
 * A human-in-the-loop workflow expressed in the workflow language, with two suspend/resume gates:
 * an {@code INPUT} step that collects a request from a person, followed by an agent step gated by a
 * {@code HUMAN_APPROVAL} transition. The run suspends at {@code AWAITING_INPUT} until
 * {@link com.agentforge4j.core.runtime.WorkflowRuntime#submitInput} supplies the form, then the
 * agent does its work and the run suspends at {@code AWAITING_STEP_APPROVAL} until
 * {@link com.agentforge4j.core.runtime.WorkflowRuntime#decideStepApproval} decides. Approving
 * advances the run to {@code COMPLETED}; rejecting fails it with a {@code StepRejectionFailure}.
 *
 * <p>This is the workflow-language companion to {@code framework-examples/human-approval}: that
 * example introduces the suspend/resume basics; this one shows the language constructs (an
 * {@code INPUT} step with its artifact and the {@code HUMAN_APPROVAL} gate) and the resume-verb
 * contract together.
 *
 * <p>The run is deterministic and offline. The {@code agentforge4j-llm-fake} provider serves one
 * scripted {@code COMPLETE} for the reviewer agent's call; the input and the approval decision are
 * supplied directly in code, so no real model, network, or API key is involved.
 */
public final class WlHumanInTheLoopExample {

  /**
   * Workflow id; matches {@code workflows/wl-human-in-the-loop.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-human-in-the-loop";

  /**
   * The input step's id; matches the {@code stepId} in the workflow.
   */
  static final String COLLECT_STEP_ID = "collect";

  /**
   * The gated review step's id; matches the {@code stepId} in the workflow and the approval call.
   */
  static final String REVIEW_STEP_ID = "review";

  /**
   * The reviewer agent's id; matches {@code agents/reviewer-agent.agent/} and the step's
   * {@code agentRef}.
   */
  static final String REVIEW_AGENT_ID = "reviewer-agent";

  /**
   * The input artifact's id; matches {@code request-form.artifact.json} and the input step's
   * {@code artifactId}.
   */
  static final String ARTIFACT_ID = "request-form";

  /**
   * The artifact item id collected from the user; answers are stored in the context under
   * {@code <artifactId>.<itemId>}.
   */
  static final String FORM_FIELD = "summary";

  /**
   * The scripted model output for the reviewer agent's single call: a lone {@code COMPLETE} finishes
   * the step, after which the approval gate suspends the run.
   */
  static final String SCRIPTED_COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  private WlHumanInTheLoopExample() {
  }

  /**
   * Runs the workflow twice — once approved, once rejected — and prints each outcome, including the
   * two suspension points.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    runApprovePath();
    runRejectPath();
  }

  private static void runApprovePath() throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();
    String runId = agentForge4j.start(WORKFLOW_ID);
    printStatus(agentForge4j, "approve — after start", runId);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "Ship the release"), "requester");
    printStatus(agentForge4j, "approve — after input", runId);

    agentForge4j.runtime().decideStepApproval(runId, REVIEW_STEP_ID,
        new StepApprovalDecision.Approve("alice", "Looks good"));
    printStatus(agentForge4j, "approve — after approve", runId);
  }

  private static void runRejectPath() throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();
    String runId = agentForge4j.start(WORKFLOW_ID);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "Ship the release"), "requester");
    agentForge4j.runtime().decideStepApproval(runId, REVIEW_STEP_ID,
        new StepApprovalDecision.Reject("alice", "Needs rework"));

    WorkflowState rejected = agentForge4j.runtime().getState(runId);
    printStatus(agentForge4j, "reject — after reject", runId);
    System.out.printf("  failure reason: %s%n", rejected.getFailureReason());
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider and to this module's
   * own workflow, artifact, and agent configuration. Shared by {@link #main(String[])} and the test
   * so both exercise exactly the same wiring.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, REVIEW_STEP_ID, REVIEW_AGENT_ID, 0),
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
   * Resolves a directory on the classpath (e.g. {@code /workflows}) to a filesystem path. Works when
   * the example runs from exploded {@code target/classes} (IDE, tests, {@code mvn} build), which is
   * how examples are run from source.
   *
   * @param classpathDirectory absolute classpath path of the directory
   *
   * @return the resolved filesystem path
   *
   * @throws URISyntaxException if the resource URL is not a valid URI
   */
  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        WlHumanInTheLoopExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
