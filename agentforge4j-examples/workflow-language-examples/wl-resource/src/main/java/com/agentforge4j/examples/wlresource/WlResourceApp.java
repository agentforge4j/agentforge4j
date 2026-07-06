// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlresource;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A workflow-language example with no LLM call. A single {@code RESOURCE} step loads a bundled
 * classpath resource (under the allow-listed {@code /workflow-resources/} root) into the workflow
 * context under a chosen key, then the run completes. It shows that not every step invokes an
 * agent — deterministic, non-AI steps can materialise reusable values for later steps.
 *
 * <p>Because this workflow runs no agent, it makes no model call and so has no real-provider path: the
 * offline run and the test both use {@link WlResourceFakeLlm}'s empty fake (the runtime still requires a
 * resolver, but it is never consulted). The bundled {@code example.properties} / {@code .env.example}
 * fake/real toggle is present for surface consistency with the sibling examples but is inert here.
 */
public final class WlResourceApp {

  /**
   * Workflow id; matches {@code workflows/wl-resource.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-resource";

  /**
   * Context key the {@code RESOURCE} step writes the loaded content under; matches the workflow's
   * {@code contextKey}.
   */
  static final String CONTEXT_KEY = "welcome";

  private WlResourceApp() {
  }

  /**
   * Runs the workflow and prints the terminal status and the loaded resource content.
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
    state.getContextValue(CONTEXT_KEY)
        .ifPresent(value -> System.out.printf("%s=%s%n", CONTEXT_KEY, value));
  }

  /**
   * Assembles an AgentForge4j runtime for the resource workflow. No agents are configured because the
   * workflow runs none; {@link WlResourceFakeLlm}'s empty fake satisfies the runtime's resolver
   * requirement without ever being consulted. This is the only assembly path — the workflow makes no
   * model call, so there is no real-provider variant. Shared by {@link #main(String[])} and the test.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(WlResourceFakeLlm.resolver())
        .build();
  }

  /**
   * Reads the content the {@code RESOURCE} step stored in the context, or {@code null} when absent.
   *
   * @param state a terminal run state
   *
   * @return the stored {@link ContextValue}, or {@code null}
   */
  static ContextValue loadedResource(WorkflowState state) {
    return state.getContextValue(CONTEXT_KEY).orElse(null);
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
        WlResourceApp.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
