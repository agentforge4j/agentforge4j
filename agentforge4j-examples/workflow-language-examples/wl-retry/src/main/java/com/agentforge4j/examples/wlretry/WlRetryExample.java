// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlretry;

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
 * A workflow-language example of {@code RETRY_PREVIOUS}: re-executing an earlier step. The
 * {@code request} step collects a note via {@code INPUT}; a {@code RETRY_PREVIOUS} step then rewinds
 * to it ({@code FROM_STEP}, {@code maxAttempts} 1), so the input is requested a second time. Once the
 * single retry attempt is exhausted, the run falls through to the {@code fallback} agent step and
 * completes.
 *
 * <p>The re-executed step is the {@code INPUT} step on purpose: {@code RETRY_PREVIOUS} rewinds the
 * run and re-runs the previous step, and a re-run {@code INPUT} step re-suspends at
 * {@code AWAITING_INPUT} — which is the observable proof that the rewind happened. (A re-run plain
 * agent step would have nothing to suspend on.) The two {@code submitInput} calls in
 * {@link #main(String[])} and the test correspond to the original request and the retry's re-request.
 *
 * <p>The run is deterministic and offline. The {@code agentforge4j-llm-fake} provider serves the
 * fallback agent's {@code COMPLETE}; the inputs are supplied directly in code, so no real model,
 * network, or API key is involved.
 */
public final class WlRetryExample {

  /**
   * Workflow id; matches {@code workflows/wl-retry.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-retry";

  /**
   * The input step that {@code RETRY_PREVIOUS} rewinds to; matches the {@code stepId} and the
   * {@code retryStepId}.
   */
  static final String REQUEST_STEP_ID = "request";

  /**
   * The input artifact's id; matches {@code retry-form.artifact.json} and the input step's
   * {@code artifactId}.
   */
  static final String ARTIFACT_ID = "retry-form";

  /**
   * The artifact item id collected from the user.
   */
  static final String FORM_FIELD = "note";

  /**
   * The fallback agent's id; matches {@code agents/finalize-agent.agent/} and the fallback step's
   * {@code agentRef}. The fallback runs once the retry attempts are exhausted.
   */
  static final String FINALIZE_AGENT_ID = "finalize-agent";

  /**
   * The fallback step's id; matches the inline {@code fallback} step's {@code stepId}.
   */
  static final String FINALIZE_STEP_ID = "finalize";

  /**
   * The scripted model output for the fallback agent's single call: a lone {@code COMPLETE} finishes
   * the run.
   */
  static final String SCRIPTED_COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  private WlRetryExample() {
  }

  /**
   * Runs the workflow, supplying the note twice (original request and retry re-request), and prints
   * the status after each step.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();

    String runId = agentForge4j.start(WORKFLOW_ID);
    printStatus(agentForge4j, "after start", runId);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "first note"), "requester");
    printStatus(agentForge4j, "after first input (retry rewinds, re-requests)", runId);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "second note"), "requester");
    printStatus(agentForge4j, "after second input (attempts exhausted, fallback)", runId);
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider and this module's own
   * workflow, artifact, and agent configuration. Shared by {@link #main(String[])} and the test so
   * both exercise exactly the same wiring.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, FINALIZE_STEP_ID, FINALIZE_AGENT_ID, 0),
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
        WlRetryExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
