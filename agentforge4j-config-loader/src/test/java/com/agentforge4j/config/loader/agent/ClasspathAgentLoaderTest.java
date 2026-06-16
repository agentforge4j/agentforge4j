// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClasspathAgentLoaderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void loadAgents_rejectsPathTraversalInEntryId() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);

    assertThatThrownBy(() -> loader.loadAgents(List.of("..evil.agent")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path traversal");
  }

  @Test
  void loadAgents_rejectsPathSeparatorInEntryId() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);

    assertThatThrownBy(() -> loader.loadAgents(List.of("bad\\id.agent")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path separators");
  }

  @Test
  void shippedAgentsStillLoad() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);

    assertThat(loader.loadAgents()).isNotNull();
  }

  @Test
  void bundledAgentsStillLoad() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        "/test-bundles/workflow-a.workflow/agents");
    List<String> entries = List.of("agents/test-bundle.agent");

    assertThat(loader.loadAgents(entries)).containsKey("test-bundle");
  }
}
