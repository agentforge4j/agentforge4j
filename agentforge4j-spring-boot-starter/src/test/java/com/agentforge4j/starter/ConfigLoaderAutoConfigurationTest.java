package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.AgentLoader;
import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.config.loader.WorkflowLoader;
import com.agentforge4j.config.loader.prompt.AgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.agentforge4j.config.loader.workflow.WorkflowDirectoryLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderAutoConfigurationTest {

  private final ConfigLoaderAutoConfiguration configuration = new ConfigLoaderAutoConfiguration();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PromptLoader promptLoader = new PromptLoader();
  private final AgentPromptResolver promptResolver = configuration.agentPromptResolver(promptLoader);

  @TempDir
  Path tempDir;

  @Test
  void blankAgentsPath_returnsNoOpAgentLoader() {
    AgentForge4jProperties properties = properties("", "", true, true);

    AgentLoader loader = configuration.agentLoader(objectMapper, promptResolver, properties);

    assertThat(loader.loadAgents()).isEmpty();
  }

  @Test
  void blankWorkflowsPath_returnsNoOpWorkflowLoader() {
    AgentForge4jProperties properties = properties("", "", true, true);

    WorkflowDirectoryLoader directoryLoader = configuration.workflowDirectoryLoader(objectMapper);
    WorkflowLoader loader = configuration.workflowLoader(directoryLoader, properties);
    WorkflowDirectoryLoad loaded = loader.loadWorkflows();

    assertThat(loaded.workflows()).isEmpty();
    assertThat(loaded.bundledAgents()).isEmpty();
  }

  @Test
  void configuredFilesystemAgentsPath_loadsAgents() throws Exception {
    Path agentsRoot = tempDir.resolve("agents");
    Path bundle = agentsRoot.resolve("alpha.agent");
    Files.createDirectories(bundle);
    Files.writeString(bundle.resolve("agent.json"), """
        {
          "id": "alpha",
          "name": "Alpha",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [{"provider":"openai","model":"gpt-4o-mini"}],
          "supportedCommands": ["COMPLETE"]
        }
        """);
    Files.writeString(bundle.resolve("systemprompt.md"), "alpha prompt");

    AgentForge4jProperties properties = properties(agentsRoot.toString(), "", false, false);
    AgentLoader loader = configuration.agentLoader(objectMapper, promptResolver, properties);

    assertThat(loader.loadAgents()).containsKey("alpha");
  }

  @Test
  void configuredFilesystemWorkflowsPath_loadsWorkflows() throws Exception {
    Path workflowsRoot = tempDir.resolve("workflows");
    Path workflowDir = workflowsRoot.resolve("sample.workflow");
    Files.createDirectories(workflowDir);
    Files.writeString(workflowDir.resolve("workflow.json"), """
        {
          "kind": "WORKFLOW",
          "id": "sample",
          "name": "Sample",
          "description": "Sample workflow",
          "artifacts": {},
          "blueprints": {},
          "steps": [
            {
              "kind": "STEP",
              "stepId": "done",
              "name": "Done",
              "behaviour": {
                "type": "FAIL",
                "reason": "done"
              }
            }
          ]
        }
        """);

    AgentForge4jProperties properties = properties("", workflowsRoot.toString(), false, false);
    WorkflowDirectoryLoader directoryLoader = configuration.workflowDirectoryLoader(objectMapper);
    WorkflowLoader loader = configuration.workflowLoader(directoryLoader, properties);

    assertThat(loader.loadWorkflows().workflows()).containsKey("sample");
  }

  private static AgentForge4jProperties properties(String agentsPath, String workflowsPath,
      boolean loadShippedWorkflows, boolean loadShippedAgents) {
    return new AgentForge4jProperties(
        agentsPath,
        workflowsPath,
        null,
        loadShippedWorkflows,
        loadShippedAgents);
  }
}
