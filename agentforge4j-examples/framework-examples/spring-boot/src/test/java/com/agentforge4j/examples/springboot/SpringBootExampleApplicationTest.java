// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.springboot;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full Spring context (starter auto-configuration + the example's fake bean) and proves the auto-configured
 * {@link AgentForge4j} runs the workflow to {@code COMPLETED} deterministically.
 */
@SpringBootTest
class SpringBootExampleApplicationTest {

  @DynamicPropertySource
  static void configPaths(DynamicPropertyRegistry registry) {
    registry.add("agentforge4j.workflows-path", () -> resolve("/workflows"));
    registry.add("agentforge4j.agents-path", () -> resolve("/agents"));
  }

  @Autowired
  private AgentForge4j agentForge4j;

  @Test
  void runReachesCompletedDeterministically() {
    String runId = agentForge4j.start(SpringBootExampleApplication.WORKFLOW_ID);

    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.COMPLETED);
  }

  private static String resolve(String classpathDirectory) {
    try {
      return Path.of(Objects.requireNonNull(
          SpringBootExampleApplicationTest.class.getResource(classpathDirectory),
          "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
