package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.runtime.command.FileSink;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigReaderWiringTest {

  private final Map<String, String> originalValues = new HashMap<>();

  @AfterEach
  void restoreProperties() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      String key = entry.getKey();
      String previous = entry.getValue();
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
    originalValues.clear();
  }

  @Test
  void agentsDirFromEnv(@TempDir Path agentsDir) {
    setProperty("agentforge4j.agents.path", agentsDir.toString());
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void workflowsDirFromEnv(@TempDir Path workflowsDir) {
    setProperty("agentforge4j.workflows.path", workflowsDir.toString());
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void fileSinkPathFromEnv(@TempDir Path sinkDir) {
    setProperty("agentforge4j.filesink.path", sinkDir.toString());
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().fileSink()).isNotEqualTo(FileSink.NO_OP_FILE_SINK);
  }

  @Test
  void cacheEnabledFromEnv() {
    setProperty("agentforge4j.llm.cache.enabled", "true");
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void maxNestingDepthFromEnv() {
    setProperty("agentforge4j.max-nesting-depth", "5");
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void invalidMaxNestingDepthThrows() {
    setProperty("agentforge4j.max-nesting-depth", "notanint");
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void loadShippedAgentsFalseFromEnv() {
    setProperty("agentforge4j.load-shipped-agents", "false");
    setProperty("agentforge4j.load-shipped-workflows", "false");
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.agents()).isEmpty();
  }

  @Test
  void programmaticAgentsDirWinsOverEnv(@TempDir Path agentsDir) {
    setProperty("agentforge4j.agents.path", "/tmp/env-agents");
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentsDir(agentsDir)
        .build();
    assertThat(af).isNotNull();
  }

  private void setProperty(String key, String value) {
    originalValues.putIfAbsent(key, System.getProperty(key));
    System.setProperty(key, value);
  }
}
