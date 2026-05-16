package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentforge4j.config.loader.AgentLoader;
import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.agent.ClasspathAgentLoader;
import com.agentforge4j.config.loader.prompt.AgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.agentforge4j.config.loader.workflow.WorkflowDirectoryLoader;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    assertThat(directoryLoader.loadWorkflows(workflowsRoot).workflows()).containsKey("sample");
  }

  @Test
  void loadedConfiguration_usesRegisteredClasspathAgentLoaderBean() {
    ClasspathAgentLoader customClasspathAgentLoader =
        CustomClasspathAgentLoaderConfiguration.CUSTOM_CLASSPATH_AGENT_LOADER;

    new ApplicationContextRunner()
        .withUserConfiguration(
            ObjectMapperTestConfiguration.class,
            CustomClasspathAgentLoaderConfiguration.class)
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            ConfigLoaderAutoConfiguration.class))
        .withPropertyValues(
            "agentforge4j.agents-path=",
            "agentforge4j.workflows-path=",
            "agentforge4j.load-shipped-agents=true",
            "agentforge4j.load-shipped-workflows=false")
        .run(ctx -> {
          assertThat(ctx.getStartupFailure()).isNull();
          assertThat(ctx.getBean(ClasspathAgentLoader.class)).isSameAs(customClasspathAgentLoader);
          LoadedConfiguration loaded = ctx.getBean(LoadedConfiguration.class);
          assertThat(loaded.agents()).containsKey("custom-shipped");
          verify(customClasspathAgentLoader).loadAgents();
        });
  }

  private static AgentDefinition sampleAgent(String id, String name) {
    return new AgentDefinition(id, name, AgentLocality.CLOUD, true, "sys",
        List.of(new ProviderPreference("openai", "gpt-4o-mini")), List.of("COMPLETE"), null, null,
        "1.0.0");
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

  @Configuration
  static class ObjectMapperTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Configuration
  static class CustomClasspathAgentLoaderConfiguration {

    private static final ClasspathAgentLoader CUSTOM_CLASSPATH_AGENT_LOADER =
        mock(ClasspathAgentLoader.class);

    static {
      when(CUSTOM_CLASSPATH_AGENT_LOADER.loadAgents()).thenReturn(Map.of(
          "custom-shipped", sampleAgent("custom-shipped", "Custom Shipped")));
    }

    @Bean
    ClasspathAgentLoader classpathAgentLoader() {
      return CUSTOM_CLASSPATH_AGENT_LOADER;
    }

    /** Separate {@code agentLoader} bean because {@link ClasspathAgentLoader} is an {@link AgentLoader}. */
    @Bean("agentLoader")
    AgentLoader agentLoader() {
      AgentLoader loader = mock(AgentLoader.class);
      when(loader.loadAgents()).thenReturn(Map.of());
      return loader;
    }
  }
}
