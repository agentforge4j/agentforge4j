package com.agentforge4j.starter;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.repository.InMemoryAgentRepository;
import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers in-memory workflow and agent catalog repositories from loaded configuration.
 *
 * <p>Run state, events, and file metadata are provided by
 * {@link InMemoryRuntimePersistenceAutoConfiguration} or a platform persistence module.
 */
@AutoConfiguration(after = ConfigLoaderAutoConfiguration.class)
public class RepositoryAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WorkflowRepository workflowRepository(LoadedConfiguration loadedConfiguration) {
    return new InMemoryWorkflowRepository(loadedConfiguration.workflows());
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRepository agentRepository(LoadedConfiguration loadedConfiguration) {
    return new InMemoryAgentRepository(loadedConfiguration.agents());
  }
}
