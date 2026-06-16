// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.config.loader.AgentForgeLoader;
import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.agent.FileSystemAgentLoader;
import com.agentforge4j.config.loader.prompt.FileSystemAgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end load of filesystem-backed agents and workflows through {@link AgentForgeLoader}.
 */
class ClasspathWorkflowLoaderIT {

  @TempDir
  Path tempDir;

  @Test
  void load_withFilesystemAgentsAndWorkflows_wiresConfigurationAndValidatesAgentRefs() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    Path workflowsRoot = tempDir.resolve("workflows");
    Files.createDirectories(agentsRoot.resolve("global.agent"));
    Files.createDirectories(workflowsRoot.resolve("sample.workflow").resolve("agents").resolve("sample-local.agent"));

    Files.writeString(agentsRoot.resolve("global.agent").resolve("agent.json"), """
        {
          "id": "global",
          "name": "Global Agent",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [{"provider":"openai","model":"gpt-4o-mini"}],
          "supportedCommands": ["COMPLETE"]
        }
        """);
    Files.writeString(agentsRoot.resolve("global.agent").resolve("systemprompt.md"), "global prompt");

    Path workflowDir = workflowsRoot.resolve("sample.workflow");
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
              "stepId": "s1",
              "name": "S1",
              "behaviour": {
                "type": "AGENT",
                "agentRef": "global",
                "transition": "AUTO"
              }
            },
            {
              "kind": "STEP",
              "stepId": "s2",
              "name": "S2",
              "behaviour": {
                "type": "AGENT",
                "agentRef": "sample-local",
                "transition": "AUTO"
              }
            }
          ]
        }
        """);
    Files.writeString(workflowDir.resolve("agents").resolve("sample-local.agent").resolve("agent.json"), """
        {
          "id": "sample-local",
          "name": "Sample Local",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [{"provider":"openai","model":"gpt-4o-mini"}],
          "supportedCommands": ["COMPLETE"]
        }
        """);
    Files.writeString(workflowDir.resolve("agents").resolve("sample-local.agent").resolve("systemprompt.md"), "local prompt");

    ObjectMapper mapper = new ObjectMapper();
    AgentForgeLoader loader = new AgentForgeLoader(
        new FileSystemAgentLoader(mapper, new FileSystemAgentPromptResolver(new PromptLoader()), agentsRoot),
        new FileSystemWorkflowLoader(mapper));

    LoadedConfiguration loaded = loader.load(
        Optional.of(agentsRoot),
        Optional.of(workflowsRoot),
        Optional.empty(),
        Optional.empty());

    assertThat(loaded.agents()).containsKeys("global", "sample-local");
    assertThat(loaded.workflows()).containsKey("sample");
  }
}
