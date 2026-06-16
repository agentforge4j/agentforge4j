// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.runtime.WorkflowRuntime;
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
          OpenAiProviderAutoConfiguration.class,
          BootstrapAutoConfiguration.class,
          SpringRuntimeAutoConfiguration.class,
          InMemoryRuntimePersistenceAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.load-shipped-workflows=true",
          "agentforge4j.load-shipped-agents=true",
          "agentforge4j.llm.openai.api-key=sk-test",
          "agentforge4j.llm.openai.default-model=gpt-4o-mini",
          "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses");

  @Test
  void loadsShippedWorkflowsAndWiresRuntime() {
    runner.run(ctx -> {
      assertThat(ctx.getStartupFailure()).isNull();
      assertThat(ctx).hasSingleBean(AgentForge4j.class);
      AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
      LoadedConfiguration loadedConfiguration = agentForge4j.components().loadedConfiguration();
      assertThat(loadedConfiguration.workflows()).isNotEmpty();
      assertThat(agentForge4j.components().workflowRepository()).isNotNull();
      assertThat(agentForge4j.components().agentRepository()).isNotNull();
      WorkflowRuntime runtime = agentForge4j.runtime();
      assertThat(runtime).isNotNull();
      assertThat(agentForge4j.components().llmClientResolver()).isNotNull();
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
