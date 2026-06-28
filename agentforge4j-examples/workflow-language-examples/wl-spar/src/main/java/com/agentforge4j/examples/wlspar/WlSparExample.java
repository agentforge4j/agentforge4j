// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlspar;

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
 * A workflow-language example of {@code SPAR}: two agents reasoning against each other under workflow
 * control. A primary agent ({@code architect}) and a challenger ({@code developer}) exchange views
 * over bounded rounds; the exchange continues while a side asks for another round with a concrete
 * reason (a {@code CONTINUE} command with {@code wantsAnotherRound} true and a substantive reason).
 * After the rounds, the primary produces the final resolution and the step completes.
 *
 * <p>Here {@code maxRounds} is 2 and both sides ask for a second round in round one, so the exchange
 * runs both rounds and then resolves — a genuine multi-round SPAR, made deterministic by scripting
 * each agent's per-round response. The run is offline: the {@code agentforge4j-llm-fake} provider
 * serves every turn, keyed by call ordinal per agent, so no real model, network, or API key is
 * involved.
 *
 * <p>Each round's contributions are recorded in the context under {@code spar.primary.round.N} and
 * {@code spar.challenger.round.N}.
 */
public final class WlSparExample {

  /**
   * Workflow id; matches {@code workflows/wl-spar.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-spar";

  /**
   * The SPAR step's id; matches the {@code stepId} in the workflow.
   */
  static final String REVIEW_STEP_ID = "review";

  /**
   * The primary agent's id; matches {@code agents/architect.agent/} and the step's {@code agentRef}.
   * The primary also performs the resolution turn.
   */
  static final String PRIMARY_AGENT_ID = "architect";

  /**
   * The challenger agent's id; matches {@code agents/developer.agent/} and the SPAR
   * {@code challengerAgentId}.
   */
  static final String CHALLENGER_AGENT_ID = "developer";

  /**
   * The configured maximum number of exchange rounds. Must stay in sync with {@code maxRounds} in
   * {@code workflow.json}: the JSON is the runtime source of truth for the SPAR loop, and this
   * constant drives the test's per-round assertions.
   */
  static final int MAX_ROUNDS = 2;

  /**
   * Context key prefix under which each round's primary contribution is stored
   * ({@code spar.primary.round.<n>}).
   */
  static final String PRIMARY_ROUND_PREFIX = "spar.primary.round.";

  /**
   * Context key prefix under which each round's challenger contribution is stored
   * ({@code spar.challenger.round.<n>}).
   */
  static final String CHALLENGER_ROUND_PREFIX = "spar.challenger.round.";

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private static final String DECLINE_ANOTHER_ROUND =
      "[{\"type\":\"CONTINUE\",\"wantsAnotherRound\":false}]";
  private static final String COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  private WlSparExample() {
  }

  /**
   * Runs the SPAR workflow and prints the terminal status.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();

    String runId = agentForge4j.start(WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);
    System.out.printf("status=%s%n", state.getStatus());
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider, scripting each
   * agent's turn so the exchange runs both rounds and then resolves. In round one both sides emit a
   * {@code CONTINUE} asking for another round (a substantive reason keeps the request valid); in
   * round two — the last round — both decline further rounds; then the primary's resolution turn
   * emits {@code COMPLETE}. Ordinals advance independently per agent. Shared by {@link #main(String[])}
   * and the test.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    FakeScript script = new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of(
        new FakeScriptKey(WORKFLOW_ID, REVIEW_STEP_ID, PRIMARY_AGENT_ID, 0),
        new FakeResponse(continueRound("The retry policy for failed calls is unspecified."), null),
        new FakeScriptKey(WORKFLOW_ID, REVIEW_STEP_ID, CHALLENGER_AGENT_ID, 0),
        new FakeResponse(continueRound("Input validation for the request payload is missing."), null),
        new FakeScriptKey(WORKFLOW_ID, REVIEW_STEP_ID, PRIMARY_AGENT_ID, 1),
        new FakeResponse(DECLINE_ANOTHER_ROUND, null),
        new FakeScriptKey(WORKFLOW_ID, REVIEW_STEP_ID, CHALLENGER_AGENT_ID, 1),
        new FakeResponse(DECLINE_ANOTHER_ROUND, null),
        new FakeScriptKey(WORKFLOW_ID, REVIEW_STEP_ID, PRIMARY_AGENT_ID, 2),
        new FakeResponse(COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withAgentsDir(resourceDirectory("/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(new DefaultLlmClientResolver(List.of(fakeLlmClient)))
        .build();
  }

  private static String continueRound(String reason) {
    return "[{\"type\":\"CONTINUE\",\"wantsAnotherRound\":true,\"reason\":\"%s\"}]".formatted(reason);
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
        WlSparExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
