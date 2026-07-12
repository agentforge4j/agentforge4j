// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlresource;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A workflow-language example with no LLM call. A single {@code RESOURCE} step loads a bundled
 * classpath resource (under the allow-listed {@code /workflow-resources/} root) into the workflow
 * context under a chosen key, then the run completes. It shows that not every step invokes an
 * agent — deterministic, non-AI steps can materialise reusable values for later steps.
 *
 * <p>The run is deterministic and offline. No agent runs, so no model output is scripted; the
 * runtime still requires an LLM resolver, so an empty {@code agentforge4j-llm-fake} script is wired
 * and never consulted.
 */
public final class WlResourceExample {

  /**
   * Workflow id; matches {@code workflows/wl-resource.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-resource";

  /**
   * Context key the {@code RESOURCE} step writes the loaded content under; matches the workflow's
   * {@code contextKey}.
   */
  static final String CONTEXT_KEY = "welcome";

  private WlResourceExample() {
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
        .ifPresent(value -> System.out.printf("%s=%s%n", CONTEXT_KEY, displayValue(value)));
  }

  /**
   * Renders a {@link ContextValue} for console display as its plain typed content, not the
   * internal record {@code toString()} (which would print {@code StringContextValue[value=...,
   * provenance=...]} instead of the value itself).
   */
  static String displayValue(ContextValue value) {
    if (value instanceof StringContextValue string) {
      return string.value();
    }
    if (value instanceof NumberContextValue number) {
      return String.valueOf(number.value());
    }
    if (value instanceof ContextValueList list) {
      return list.values().stream().map(WlResourceExample::displayValue)
          .collect(Collectors.joining(","));
    }
    throw new IllegalStateException(
        "Unsupported disclosed context value type: " + value.getClass().getSimpleName());
  }

  /**
   * Assembles an AgentForge4j runtime for the resource workflow. No agents are configured because
   * the workflow runs none; the empty fake script satisfies the runtime's resolver requirement
   * without ever being consulted. Shared by {@link #main(String[])} and the test.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    LlmClient fakeLlmClient =
        new FakeLlmClient(new StaticFakeResponseSource(new FakeScript(1, Map.of())));

    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(new DefaultLlmClientResolver(List.of(fakeLlmClient)))
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
        WlResourceExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
