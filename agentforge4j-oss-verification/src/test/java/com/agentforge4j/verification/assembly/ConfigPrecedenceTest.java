// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.assembly;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.verification.support.Fixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box proof of the bootstrap config-precedence contract for the {@code agentforge4j.agents.path}
 * key. Live resolution order is {@code programmatic > system-property > env} (the env leg is held — see
 * the verification {@code CHANGES.md}). Each leg is observed through {@link AgentForge4j#agents()}: the
 * agents catalog reflects whichever source won, since the two fixture directories declare distinct agent
 * ids.
 */
class ConfigPrecedenceTest {

  private static final String AGENTS_PATH_KEY = "agentforge4j.agents.path";

  private String originalAgentsPath;
  private boolean agentsPathCaptured;

  @AfterEach
  void restoreSystemProperty() {
    if (agentsPathCaptured) {
      if (originalAgentsPath == null) {
        System.clearProperty(AGENTS_PATH_KEY);
      } else {
        System.setProperty(AGENTS_PATH_KEY, originalAgentsPath);
      }
      agentsPathCaptured = false;
    }
  }

  @Test
  void programmaticAgentsDirWinsOverSystemProperty() {
    Path systemPropertyDir = Fixtures.dir("/fixtures/assembly/agents-a");
    Path programmaticDir = Fixtures.dir("/fixtures/assembly/agents-b");
    setAgentsPathProperty(systemPropertyDir);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withAgentsDir(programmaticDir)
        .build();

    assertThat(agentIds(af))
        .as("programmatic withAgentsDir must override the system property")
        .containsExactly("beta-agent");
  }

  @Test
  void systemPropertyHonouredWhenNoProgrammaticAgentsDir() {
    Path systemPropertyDir = Fixtures.dir("/fixtures/assembly/agents-a");
    setAgentsPathProperty(systemPropertyDir);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .build();

    assertThat(agentIds(af))
        .as("the agents.path system property must be honoured when nothing is set programmatically")
        .containsExactly("alpha-agent");
  }

  private void setAgentsPathProperty(Path dir) {
    originalAgentsPath = System.getProperty(AGENTS_PATH_KEY);
    agentsPathCaptured = true;
    System.setProperty(AGENTS_PATH_KEY, dir.toString());
  }

  private static List<String> agentIds(AgentForge4j af) {
    return af.agents().stream().map(AgentDefinition::id).toList();
  }
}
