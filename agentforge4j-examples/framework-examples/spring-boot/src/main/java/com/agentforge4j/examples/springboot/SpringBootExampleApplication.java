// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.springboot;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeResponseSource;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * A Spring Boot application that uses the AgentForge4j Spring Boot starter. The starter auto-configures the
 * {@link AgentForge4j} facade from {@code application.properties}; this class adds two things a deterministic, offline
 * example needs:
 *
 * <ul>
 *   <li>a run-agnostic {@link FakeResponseSource} bean (overriding the starter's run-scoped default,
 *       which is {@code @ConditionalOnMissingBean}) so every run is served one scripted
 *       {@code COMPLETE} with no per-run registration;</li>
 *   <li>the workflows/agents paths, resolved from this module's classpath resources, supplied as
 *       default properties (the starter loads config from filesystem directories).</li>
 * </ul>
 *
 * <p>A {@link CommandLineRunner} then starts the workflow on boot and prints its terminal status. No
 * real model, network, or API key is involved.
 */
@SpringBootApplication
public class SpringBootExampleApplication {

  /**
   * Workflow id; matches {@code workflows/spring-boot-demo.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "spring-boot-demo";

  /**
   * The single step's id; matches the {@code stepId} in the workflow definition.
   */
  static final String STEP_ID = "greet";

  /**
   * Agent id; matches {@code agents/spring-boot-agent.agent/} and the step's {@code agentRef}.
   */
  static final String AGENT_ID = "spring-boot-agent";

  /**
   * The scripted model output for the agent's single call: a bare JSON array with one COMPLETE.
   */
  static final String SCRIPTED_COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  /**
   * Boots the application with the resolved config paths supplied as default properties.
   *
   * @param args passed through to Spring
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    SpringApplication application = new SpringApplication(SpringBootExampleApplication.class);
    application.setDefaultProperties(configPathProperties());
    application.run(args);
  }

  /**
   * The starter's workflows/agents path properties, pointing at this module's classpath config dirs resolved to
   * filesystem paths (they sit under exploded {@code target/classes} when run from source).
   *
   * @return the property map to seed the environment with
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static Map<String, Object> configPathProperties() throws URISyntaxException {
    return Map.of(
        "agentforge4j.workflows-path", resourceDirectory("/workflows"),
        "agentforge4j.agents-path", resourceDirectory("/agents"));
  }

  /**
   * A run-agnostic fake response source: every run is served the same scripted {@code COMPLETE}. Wins over the
   * starter's run-scoped default because that bean is {@code @ConditionalOnMissingBean}.
   *
   * @return the deterministic response source
   */
  @Bean
  FakeResponseSource fakeResponseSource() {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, STEP_ID, AGENT_ID, 0),
        new FakeResponse(SCRIPTED_COMPLETE, null)));
    return new StaticFakeResponseSource(script);
  }

  /**
   * Runs the workflow once on startup and prints its terminal status.
   *
   * @param agentForge4j the auto-configured framework facade
   *
   * @return the startup runner
   */
  @Bean
  CommandLineRunner runWorkflow(AgentForge4j agentForge4j) {
    return args -> {
      String runId = agentForge4j.start(WORKFLOW_ID);
      WorkflowState state = agentForge4j.runtime().getState(runId);
      System.out.printf("Workflow '%s' (run %s) finished with status: %s%n", WORKFLOW_ID, runId, state.getStatus());
    };
  }

  private static String resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        SpringBootExampleApplication.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI()).toString();
  }
}
