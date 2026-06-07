package com.agentforge4j.starter;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures AgentForge4j by delegating to {@link AgentForge4jBootstrap}.
 *
 * <p>Exposes a single {@link AgentForge4j} bean. All assembled components are
 * accessible via {@link AgentForge4j#components()}. Spring beans for individual components can be
 * registered by the application as needed:
 *
 * <pre>{@code
 * public WorkflowRuntime workflowRuntime(AgentForge4j agentForge4j) {
 *     return agentForge4j.runtime();
 * }
 * }</pre>
 *
 * <p>Register the method above as a Spring bean when needed.
 *
 * <p>Maps {@link AgentForge4jProperties} to bootstrap builder calls. All beans use
 * {@link ConditionalOnMissingBean} — the {@link AgentForge4j} bean can be overridden by the
 * application.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@EnableConfigurationProperties({AgentForge4jProperties.class, LlmCacheSettings.class})
public class BootstrapAutoConfiguration {

  /**
   * Assembles and exposes the full AgentForge4j facade.
   *
   * @param properties           bound AgentForge4j properties; must not be {@code null}
   * @param cacheSettings        LLM prompt cache settings; must not be {@code null}
   * @param objectMapperProvider optional Jackson mapper from the context
   * @param llmConfigurations    optional provider configuration beans registered before this
   *                             config
   * @return assembled facade; never {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public AgentForge4j agentForge4j(
      AgentForge4jProperties properties,
      LlmCacheSettings cacheSettings,
      ObjectProvider<ObjectMapper> objectMapperProvider,
      ObjectProvider<List<LlmClientConfiguration>> llmConfigurations,
      ObjectProvider<List<ToolProvider>> toolProviders,
      ObjectProvider<ToolPolicy> toolPolicy,
      ObjectProvider<ToolExecutionOptions> toolExecutionOptions) {
    AgentForge4jBootstrap.Builder builder = AgentForge4jBootstrap.defaults();
    applyProperties(builder, properties);
    builder.withCacheEnabled(cacheSettings.enabled());
    List<LlmClientConfiguration> configurations = llmConfigurations.getIfAvailable(List::of);
    if (!configurations.isEmpty()) {
      ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
      builder.withLlmClientResolver(DefaultLlmClientResolver.discover(mapper, configurations));
    } else {
      builder.withLlmClientResolver(new DefaultLlmClientResolver(List.of()));
    }
    applyToolSupport(builder, toolProviders, toolPolicy, toolExecutionOptions);
    return builder.build();
  }

  private static void applyToolSupport(AgentForge4jBootstrap.Builder builder,
      ObjectProvider<List<ToolProvider>> toolProviders,
      ObjectProvider<ToolPolicy> toolPolicy,
      ObjectProvider<ToolExecutionOptions> toolExecutionOptions) {
    List<ToolProvider> providers = toolProviders.getIfAvailable(List::of);
    if (providers.isEmpty()) {
      return;
    }
    builder.withToolProviders(providers);
    ToolPolicy policy = toolPolicy.getIfAvailable();
    if (policy != null) {
      builder.withToolPolicy(policy);
    }
    ToolExecutionOptions options = toolExecutionOptions.getIfAvailable();
    if (options != null) {
      builder.withToolExecutionOptions(options);
    }
  }

  private static void applyProperties(AgentForge4jBootstrap.Builder builder,
      AgentForge4jProperties properties) {
    Validate.notNull(properties, "AgentForge4j properties must not be null");
    if (StringUtils.isNotBlank(properties.agentsPath())) {
      builder.withAgentsDir(Path.of(properties.agentsPath()));
    }
    if (StringUtils.isNotBlank(properties.workflowsPath())) {
      builder.withWorkflowsDir(Path.of(properties.workflowsPath()));
    }
    if (properties.maxNestingDepth() != null) {
      builder.withMaxNestingDepth(properties.maxNestingDepth());
    }
    builder.withLoadShippedAgents(properties.loadShippedAgents());
    builder.withLoadShippedWorkflows(properties.loadShippedWorkflows());
  }
}
