// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.quickstart;

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
 * The shortest runnable AgentForge4j program: assemble the framework in plain Java, start a one-step workflow, and read
 * its terminal state.
 *
 * <p>The run is deterministic and offline. The {@code agentforge4j-llm-fake} provider serves a
 * scripted response keyed by ({@value #WORKFLOW_ID}, {@value #STEP_ID}, {@value #AGENT_ID}, call ordinal), so no real
 * model, network, or API key is involved. The single scripted call returns one {@code COMPLETE} command, which finishes
 * the agent step; with an {@code AUTO} transition and a single step, the workflow reaches {@code COMPLETED}.
 */
public final class QuickStartExample {

  /**
   * Workflow id; matches {@code workflows/quick-start.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "quick-start";

  /**
   * The single step's id; matches the {@code stepId} in the workflow definition.
   */
  static final String STEP_ID = "greet";

  /**
   * Agent id; matches {@code agents/quick-start-agent.agent/} and the step's {@code agentRef}.
   */
  static final String AGENT_ID = "quick-start-agent";

  /**
   * The scripted model output for the agent's single call. An LLM response is a bare JSON array of command objects; a
   * lone {@code COMPLETE} terminates the step.
   */
  static final String SCRIPTED_COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  private QuickStartExample() {
  }

  /**
   * Assembles the runtime, runs the workflow to completion, and prints the terminal status.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    // tag::run[]
    AgentForge4j agentForge4j = assemble();

    String runId = agentForge4j.start(WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);

    System.out.printf("Workflow '%s' (run %s) finished with status: %s%n", WORKFLOW_ID, runId, state.getStatus());
    System.out.printf("Step outputs: %s%n", state.getStepOutputs());
    // end::run[]
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
    // tag::assemble[]
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
    // end::assemble[]
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
        QuickStartExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
