// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlbranch;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
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
 * A workflow-language routing example. An agent step ({@code decide}) writes a {@code decision} into
 * the workflow context with a {@code SET_CONTEXT} command; a {@code BRANCH} step then routes on that
 * context value — {@code "approve"} runs an agent step to {@code COMPLETED}, {@code "reject"} ends
 * the run via a {@code FAIL} step ({@code FAILED}). Routing is deterministic and author-controlled:
 * the {@code BRANCH} step itself calls no model, it just reads a context value and dispatches.
 *
 * <p>The run is deterministic and offline. The {@code agentforge4j-llm-fake} provider serves the
 * scripted decision and the approved step's {@code COMPLETE}, so no real model, network, or API key
 * is involved. {@link #assemble(String)} takes the decision the {@code decide} agent should emit, so
 * each branch can be driven from a single workflow definition.
 */
public final class WlBranchExample {

  /**
   * Workflow id; matches {@code workflows/wl-branch.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-branch";

  /**
   * The routing agent step's id; matches the {@code stepId} in the workflow.
   */
  static final String DECIDE_STEP_ID = "decide";

  /**
   * The routing agent's id; matches {@code agents/branch-agent.agent/} and the step's {@code agentRef}.
   */
  static final String BRANCH_AGENT_ID = "branch-agent";

  /**
   * The approved branch's inline step id; matches the {@code stepId} under the {@code "approve"} branch.
   */
  static final String APPROVE_STEP_ID = "approved";

  /**
   * The approved branch agent's id; matches {@code agents/approve-agent.agent/} and the branch's
   * {@code agentRef}.
   */
  static final String APPROVE_AGENT_ID = "approve-agent";

  /**
   * Context key the {@code decide} agent writes and the {@code BRANCH} step routes on.
   */
  static final String DECISION_KEY = "decision";

  /**
   * Branch value that routes to the agent-backed completion path.
   */
  static final String APPROVE = "approve";

  /**
   * Branch value that routes to the {@code FAIL} step.
   */
  static final String REJECT = "reject";

  private WlBranchExample() {
  }

  /**
   * Runs the workflow once per branch and prints each outcome.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    for (String decision : List.of(APPROVE, REJECT)) {
      AgentForge4j agentForge4j = assemble(decision);
      String runId = agentForge4j.start(WORKFLOW_ID);
      WorkflowState state = agentForge4j.runtime().getState(runId);
      System.out.printf("decision=%s -> status=%s%n", decision, state.getStatus());
    }
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider, scripting the
   * {@code decide} agent to emit the given branch decision. Shared by {@link #main(String[])} and the
   * test so both exercise exactly the same wiring.
   *
   * @param decision the decision the {@code decide} agent writes into the context
   *                 (e.g. {@link #APPROVE} or {@link #REJECT})
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble(String decision) throws URISyntaxException {
    String setDecision =
        "[{\"type\":\"SET_CONTEXT\",\"key\":\"%s\",\"value\":{\"type\":\"STRING\",\"value\":\"%s\"}}]"
            .formatted(DECISION_KEY, decision);
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, DECIDE_STEP_ID, BRANCH_AGENT_ID, 0),
        new FakeResponse(setDecision, null),
        new FakeScriptKey(WORKFLOW_ID, APPROVE_STEP_ID, APPROVE_AGENT_ID, 0),
        new FakeResponse("[{\"type\":\"COMPLETE\"}]", null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withAgentsDir(resourceDirectory("/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(new DefaultLlmClientResolver(List.of(fakeLlmClient)))
        .build();
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
        WlBranchExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
