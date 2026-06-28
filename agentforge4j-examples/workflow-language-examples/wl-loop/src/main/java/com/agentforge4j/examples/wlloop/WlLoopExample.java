// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlloop;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
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
 * A workflow-language example of bounded iteration via a looped blueprint, shown with two
 * termination strategies:
 *
 * <ul>
 *   <li>{@code wl-loop-fixed} — {@code FIXED_COUNT}: the loop body runs exactly {@code maxIterations}
 *       times regardless of what the agent emits (completion signals are ignored), then completes.</li>
 *   <li>{@code wl-loop-signal} — {@code AGENT_SIGNAL}: the loop runs until the agent emits a
 *       {@code COMPLETE} signal. Here the agent signals on its first iteration, so the loop stops at
 *       one iteration — well before its {@code maxIterations} ceiling — showing that the agent, not a
 *       fixed count, decides when to stop.</li>
 * </ul>
 *
 * <p>Both loops are declared as a {@code BLUEPRINT_REF} to a blueprint whose {@code behaviour}
 * carries the {@code loopConfig}. The run is deterministic and offline: the
 * {@code agentforge4j-llm-fake} provider serves one scripted response per iteration, keyed by call
 * ordinal, so no real model, network, or API key is involved.
 */
public final class WlLoopExample {

  /**
   * Workflow id of the fixed-count loop; matches {@code workflows/wl-loop-fixed.workflow/}.
   */
  static final String FIXED_WORKFLOW_ID = "wl-loop-fixed";

  /**
   * Workflow id of the agent-signal loop; matches {@code workflows/wl-loop-signal.workflow/}.
   */
  static final String SIGNAL_WORKFLOW_ID = "wl-loop-signal";

  /**
   * The looped body step's id; matches the {@code stepId} in each blueprint.
   */
  static final String BODY_STEP_ID = "body";

  /**
   * The loop body agent's id; matches {@code agents/loop-agent.agent/} and each body's
   * {@code agentRef}.
   */
  static final String LOOP_AGENT_ID = "loop-agent";

  /**
   * The fixed-count loop's iteration count (its blueprint's {@code maxIterations}).
   */
  static final int FIXED_ITERATIONS = 3;

  /**
   * The number of iterations the agent-signal loop runs before the agent emits {@code COMPLETE} —
   * fewer than the blueprint's {@code maxIterations}, so termination is by the agent's signal, not
   * the ceiling.
   */
  static final int SIGNAL_ITERATIONS = 1;

  private static final String COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private WlLoopExample() {
  }

  /**
   * Runs both loop variants and prints each one's terminal status and iteration count.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();

    for (String workflowId : List.of(FIXED_WORKFLOW_ID, SIGNAL_WORKFLOW_ID)) {
      String runId = agentForge4j.start(workflowId);
      WorkflowState state = agentForge4j.runtime().getState(runId);
      System.out.printf("%s -> status=%s, iterations=%d%n",
          workflowId, state.getStatus(), loopIterations(agentForge4j, runId));
    }
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider, scripting one
   * response per iteration for each loop. The fixed-count loop runs {@link #FIXED_ITERATIONS} times
   * (each body call emits {@code COMPLETE}, which {@code FIXED_COUNT} ignores); the agent-signal loop
   * emits {@code COMPLETE} on its first iteration, which signals it to stop. Shared by
   * {@link #main(String[])} and the test.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    FakeScript script = new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of(
        new FakeScriptKey(FIXED_WORKFLOW_ID, BODY_STEP_ID, LOOP_AGENT_ID, 0), new FakeResponse(COMPLETE, null),
        new FakeScriptKey(FIXED_WORKFLOW_ID, BODY_STEP_ID, LOOP_AGENT_ID, 1), new FakeResponse(COMPLETE, null),
        new FakeScriptKey(FIXED_WORKFLOW_ID, BODY_STEP_ID, LOOP_AGENT_ID, 2), new FakeResponse(COMPLETE, null),
        new FakeScriptKey(SIGNAL_WORKFLOW_ID, BODY_STEP_ID, LOOP_AGENT_ID, 0), new FakeResponse(COMPLETE, null)));
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
   * Counts the loop iterations a run performed, by tallying its {@code LOOP_ITERATION_STARTED}
   * events. The event log is reached through {@code components().workflowEventLog()}, the internal
   * integrator accessor — it is not part of the public API, and an embedding application would
   * normally surface run progress through its own projection rather than read it here.
   *
   * @param agentForge4j the framework facade the run executed on
   * @param runId        the run to inspect
   *
   * @return the number of loop iterations started
   */
  static long loopIterations(AgentForge4j agentForge4j, String runId) {
    long iterations = 0;
    for (WorkflowEvent event : agentForge4j.components().workflowEventLog().getEvents(runId)) {
      if (event.eventType() == WorkflowEventType.LOOP_ITERATION_STARTED) {
        iterations++;
      }
    }
    return iterations;
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
        WlLoopExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
