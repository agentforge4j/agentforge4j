package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.agent.ClasspathAgentLoader;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.starter.llmclient.openai.OpenAiProviderAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * End-to-end wiring of starter auto-configuration without test doubles for application beans.
 */
class AgentForge4jStarterWiringIT {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(ObjectMapperTestConfiguration.class)
      .withConfiguration(AutoConfigurations.of(
          JacksonAutoConfiguration.class,
          ConfigLoaderAutoConfiguration.class,
          RepositoryAutoConfiguration.class,
          InMemoryRuntimePersistenceAutoConfiguration.class,
          OpenAiProviderAutoConfiguration.class,
          LlmAutoConfiguration.class,
          RuntimeAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.load-shipped-workflows=true",
          "agentforge4j.llm.openai.api-key=sk-test",
          "agentforge4j.llm.openai.default-model=gpt-4o-mini",
          "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses");

  @Test
  void loadsShippedWorkflowsAndWiresRuntime() {
    runner.run(ctx -> {
      assertThat(ctx.getStartupFailure()).isNull();
      assertThat(ctx).hasSingleBean(LoadedConfiguration.class);
      assertThat(ctx.getBean(LoadedConfiguration.class).workflows()).isNotEmpty();
      assertThat(ctx).hasSingleBean(WorkflowRepository.class);
      assertThat(ctx).hasSingleBean(AgentRepository.class);
      assertThat(ctx).hasSingleBean(WorkflowRuntime.class);
      assertThat(ctx).hasSingleBean(LlmClientResolver.class);
      assertThat(ctx).hasSingleBean(ClasspathAgentLoader.class);
    });
  }

  @Configuration
  static class ObjectMapperTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
