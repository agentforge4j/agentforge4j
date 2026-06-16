// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.AgentForgeLoader;
import com.agentforge4j.config.loader.AgentLoader;
import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.agent.ClasspathAgentLoader;
import com.agentforge4j.config.loader.agent.FileSystemAgentLoader;
import com.agentforge4j.config.loader.prompt.AgentPromptResolver;
import com.agentforge4j.config.loader.prompt.FileSystemAgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.agentforge4j.config.loader.workflow.ClasspathWorkflowLoader;
import com.agentforge4j.config.loader.workflow.FileSystemWorkflowLoader;
import com.agentforge4j.config.loader.workflow.WorkflowDirectoryLoader;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the {@link ObjectMapper} and {@link LoadedConfiguration} used by
 * {@link AgentForge4jBootstrap}. Internal — not part of the public API.
 */
final class ConfigurationLoader {

  private static final System.Logger LOG = System.getLogger(ConfigurationLoader.class.getName());

  private ConfigurationLoader() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Builds the default Jackson {@link ObjectMapper} with JavaTimeModule, no-timestamps
   * serialisation, and lenient deserialisation. Unknown properties stay non-fatal (forward
   * compatibility) but are logged at WARNING so misspelled config keys are visible.
   *
   * @return configured mapper; never {@code null}
   */
  static ObjectMapper defaultObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.addHandler(new UnknownPropertyWarningHandler());
    return mapper;
  }

  /**
   * Logs each unknown JSON property at WARNING and defers to the default lenient handling, so a
   * typo such as {@code systemPromt} is reported instead of silently ignored.
   */
  private static final class UnknownPropertyWarningHandler extends DeserializationProblemHandler {

    @Override
    public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser,
        JsonDeserializer<?> deserializer, Object beanOrClass, String propertyName) {
      String targetType = beanOrClass instanceof Class<?> clazz
          ? clazz.getName()
          : beanOrClass.getClass().getName();
      LOG.log(System.Logger.Level.WARNING,
          "Ignoring unknown property \"{0}\" while reading {1} - is it misspelled?",
          propertyName, targetType);
      return false;
    }
  }

  /**
   * Loads agent and workflow definitions from the configured sources.
   *
   * @param objectMapper         mapper for JSON parsing; must not be {@code null}
   * @param agentsDir            optional filesystem agents directory
   * @param workflowsDir         optional filesystem workflows directory
   * @param loadShippedAgents    whether to load classpath-bundled agents
   * @param loadShippedWorkflows whether to load classpath-bundled workflows
   *
   * @return loaded configuration; never {@code null}
   *
   * @throws IllegalStateException if loading fails
   */
  static LoadedConfiguration load(ObjectMapper objectMapper,
      Path agentsDir,
      Path workflowsDir,
      boolean loadShippedAgents,
      boolean loadShippedWorkflows) {
    WorkflowDirectoryLoader workflowDirectoryLoader = new FileSystemWorkflowLoader(objectMapper);

    ClasspathAgentLoader shippedClasspathAgentLoader = null;
    Optional<ClasspathAgentLoader> classpathAgentLoader;
    if (loadShippedAgents) {
      shippedClasspathAgentLoader = new ClasspathAgentLoader(
          objectMapper, ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);
      classpathAgentLoader = Optional.of(shippedClasspathAgentLoader);
    } else {
      classpathAgentLoader = Optional.empty();
    }

    Optional<ClasspathWorkflowLoader> classpathWorkflowLoader = loadShippedWorkflows
        ? Optional.of(new ClasspathWorkflowLoader(objectMapper))
        : Optional.empty();

    AgentLoader agentLoader = buildAgentLoader(objectMapper, agentsDir,
        shippedClasspathAgentLoader);

    AgentForgeLoader loader = new AgentForgeLoader(agentLoader, workflowDirectoryLoader);
    try {
      return loader.load(
          Optional.ofNullable(agentsDir),
          Optional.ofNullable(workflowsDir),
          classpathAgentLoader,
          classpathWorkflowLoader);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Failed to load AgentForge4j configuration", exception);
    }
  }

  private static AgentLoader buildAgentLoader(ObjectMapper objectMapper,
      Path agentsDir,
      ClasspathAgentLoader shippedClasspathAgentLoader) {
    AgentLoader agentLoader;
    if (agentsDir != null) {
      AgentPromptResolver promptResolver = new FileSystemAgentPromptResolver(new PromptLoader());
      agentLoader = new FileSystemAgentLoader(objectMapper, promptResolver, agentsDir);
    } else {
      agentLoader = Objects.requireNonNullElseGet(shippedClasspathAgentLoader,
          () -> new ClasspathAgentLoader(
              objectMapper, ClasspathAgentLoader.SHIPPED_AGENTS_ROOT));
    }
    return agentLoader;
  }
}
