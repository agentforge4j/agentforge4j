package com.agentforge4j.starter;

import com.agentforge4j.config.loader.AgentForgeLoader;
import com.agentforge4j.config.loader.AgentLoader;
import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.config.loader.WorkflowLoader;
import com.agentforge4j.config.loader.agent.ClasspathAgentLoader;
import com.agentforge4j.config.loader.agent.FileSystemAgentLoader;
import com.agentforge4j.config.loader.prompt.AgentPromptResolver;
import com.agentforge4j.config.loader.prompt.FileSystemAgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.agentforge4j.config.loader.validation.WorkflowDraftValidator;
import com.agentforge4j.config.loader.validation.WorkflowValidator;
import com.agentforge4j.config.loader.workflow.ClasspathWorkflowLoader;
import com.agentforge4j.config.loader.workflow.FileSystemWorkflowLoader;
import com.agentforge4j.config.loader.workflow.WorkflowDirectoryLoader;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the filesystem config loader and triggers a single {@link LoadedConfiguration} load at
 * startup.
 *
 * <p>All beans are conditional — embedders that need different loading
 * semantics (for example, loading from a database or a remote registry) can register their own
 * {@link LoadedConfiguration} bean and the rest of the chain will defer.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@EnableConfigurationProperties(AgentForge4jProperties.class)
public class ConfigLoaderAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PromptLoader promptLoader() {
    return new PromptLoader();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentPromptResolver agentPromptResolver(PromptLoader promptLoader) {
    return new FileSystemAgentPromptResolver(promptLoader);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLoader agentLoader(ObjectMapper objectMapper,
      AgentPromptResolver agentPromptResolver,
      AgentForge4jProperties properties) {
    if (StringUtils.isBlank(properties.agentsPath())) {
      return NoOpAgentLoader.INSTANCE;
    }
    return new FileSystemAgentLoader(objectMapper, agentPromptResolver,
        Path.of(properties.agentsPath()));
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowDirectoryLoader workflowDirectoryLoader(ObjectMapper objectMapper) {
    return new FileSystemWorkflowLoader(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowLoader workflowLoader(WorkflowDirectoryLoader workflowDirectoryLoader,
      AgentForge4jProperties properties) {
    if (StringUtils.isBlank(properties.workflowsPath())) {
      return NoOpWorkflowLoader.INSTANCE;
    }
    Path workflowsRoot = Path.of(properties.workflowsPath());
    return () -> workflowDirectoryLoader.loadWorkflows(workflowsRoot);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentForgeLoader agentForgeLoader(@Qualifier("agentLoader") AgentLoader agentLoader,
      @Qualifier("workflowLoader") WorkflowLoader workflowLoader,
      WorkflowDirectoryLoader workflowDirectoryLoader) {
    return new AgentForgeLoader(agentLoader, workflowDirectoryLoader);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowValidator workflowValidator() {
    return new WorkflowValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowDraftValidator workflowDraftValidator(WorkflowValidator validator) {
    return new WorkflowDraftValidator(validator);
  }

  @Bean
  @ConditionalOnMissingBean
  public ClasspathAgentLoader classpathAgentLoader(ObjectMapper objectMapper) {
    return new ClasspathAgentLoader(objectMapper, ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);
  }

  @Bean
  @ConditionalOnMissingBean
  public ClasspathWorkflowLoader classpathWorkflowLoader(ObjectMapper objectMapper) {
    return new ClasspathWorkflowLoader(objectMapper);
  }

  /**
   * Builds the merged catalog from filesystem paths and optional shipped loaders.
   *
   * @throws IllegalArgumentException when every source is absent (no filesystem paths configured
   *     and both shipped loaders are disabled)
   */
  @Bean
  @ConditionalOnMissingBean
  public LoadedConfiguration loadedConfiguration(
      AgentForgeLoader agentForgeLoader,
      AgentForge4jProperties properties,
      ObjectMapper objectMapper) {

    Optional<Path> agentsPathOpt = Optional.ofNullable(properties.agentsPath())
        .filter(StringUtils::isNotBlank)
        .map(Path::of);
    Optional<Path> workflowsPathOpt = Optional.ofNullable(properties.workflowsPath())
        .filter(StringUtils::isNotBlank)
        .map(Path::of);
    Optional<ClasspathAgentLoader> agentClasspathLoader =
        Optional.of(properties.loadShippedAgents())
            .filter(Boolean::booleanValue)
            .map(ignored -> new ClasspathAgentLoader(objectMapper,
                ClasspathAgentLoader.SHIPPED_AGENTS_ROOT));
    Optional<ClasspathWorkflowLoader> workflowClasspathLoader =
        Optional.of(properties.loadShippedWorkflows())
            .filter(Boolean::booleanValue)
            .map(ignored -> new ClasspathWorkflowLoader(objectMapper));

    Validate.isTrue(agentsPathOpt.isPresent() ||
            workflowsPathOpt.isPresent() ||
            agentClasspathLoader.isPresent() ||
            workflowClasspathLoader.isPresent(),
        """
            AgentForge4j requires at least one of:
            agentforge4j.agents-path,
            agentforge4j.workflows-path,
            agentforge4j.load-shipped-workflows=true, or
            agentforge4j.load-shipped-agents=true
            """);

    return agentForgeLoader.load(agentsPathOpt, workflowsPathOpt, agentClasspathLoader,
        workflowClasspathLoader);
  }

  private enum NoOpAgentLoader implements AgentLoader {
    INSTANCE;

    @Override
    public Map<String, com.agentforge4j.core.agent.AgentDefinition> loadAgents() {
      return Map.of();
    }

    @Override
    public Map<String, com.agentforge4j.core.agent.AgentDefinition> loadAgents(
        java.util.List<String> bundleFiles) {
      return Map.of();
    }
  }

  private enum NoOpWorkflowLoader implements WorkflowLoader {
    INSTANCE;

    @Override
    public WorkflowDirectoryLoad loadWorkflows() {
      return new WorkflowDirectoryLoad(Map.of(), Map.of());
    }
  }
}
