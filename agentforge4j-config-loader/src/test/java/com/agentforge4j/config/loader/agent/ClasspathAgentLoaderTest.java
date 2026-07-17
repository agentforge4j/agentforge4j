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

  /**
   * Regression test for a defect where {@code loadAgents()} (the top-level shipped-agents
   * entry point) silently returned an empty map for every real shipped catalog: {@code
   * AgentBundleLocator.shippedAgentIds()} returns bare ids from the index (e.g. {@code
   * "shipped-test"}, no {@code .agent} suffix), but the shared {@code loadAgents(List)} filter
   * required the suffix already be present, so every bare id was silently dropped with no error.
   * The fixture index at {@code src/test/resources/shipped-agents/index} deliberately lists a
   * bare id, matching the real production index shape, so this fails loudly (empty map) if the
   * suffix-normalization at the {@code loadAgents()} call site regresses.
   */
  @Test
  void shippedAgentsLoadFromABareIndexEntryWithNoAgentSuffix() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);

    assertThat(loader.loadAgents()).containsKey("shipped-test");
  }

  @Test
  void bundledAgentsStillLoad() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        "/test-bundles/workflow-a.workflow/agents");
    List<String> entries = List.of("agents/test-bundle.agent");

    assertThat(loader.loadAgents(entries)).containsKey("test-bundle");
  }
}
