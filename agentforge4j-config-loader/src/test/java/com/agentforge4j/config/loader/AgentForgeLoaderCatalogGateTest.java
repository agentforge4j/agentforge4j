// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.workflow.ClasspathWorkflowLoader;
import com.agentforge4j.config.loader.workflow.FileSystemWorkflowLoader;
import com.agentforge4j.core.agent.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Exercises the production gate wiring: {@code AgentForgeLoader.loadWorkflowsFromClasspath} runs
 * {@code CatalogCompatibilityGate.defaults()} (the running {@code FrameworkVersion} and supported
 * workflow schema version) before loading. The synthetic shipped fixture on the test classpath ships
 * a compatible {@code agentforge4j-catalog.json}, so the gate passes and the catalog loads.
 */
class AgentForgeLoaderCatalogGateTest {

  @Test
  void load_withCompatibleShippedCatalog_passesGateAndLoadsFixture() {
    ObjectMapper mapper = new ObjectMapper();
    AgentForgeLoader loader = new AgentForgeLoader(emptyAgentLoader(),
        new FileSystemWorkflowLoader(mapper));

    LoadedConfiguration loaded = loader.load(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(new ClasspathWorkflowLoader(mapper)));

    assertThat(loaded.workflows()).containsKey("loader-fixture");
    assertThat(loaded.agents()).containsKey("loader-fixture-agent");
  }

  private static AgentLoader emptyAgentLoader() {
    return new AgentLoader() {
      @Override
      public Map<String, AgentDefinition> loadAgents() {
        return Map.of();
      }

      @Override
      public Map<String, AgentDefinition> loadAgents(List<String> bundleFiles) {
        return Map.of();
      }
    };
  }
}
