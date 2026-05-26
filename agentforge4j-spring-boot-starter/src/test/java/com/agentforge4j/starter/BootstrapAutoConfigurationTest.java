package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.FileSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

class BootstrapAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(BootstrapAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.load-shipped-agents=true",
          "agentforge4j.load-shipped-workflows=true");

  @Test
  void contextLoads() {
    runner.run(ctx -> assertThat(ctx.getStartupFailure()).isNull());
  }

  @Test
  void agentForge4jBeanPresent() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(AgentForge4j.class));
  }

  @Test
  void workflowRuntimeAccessibleViaFacade() {
    runner.run(ctx -> {
      AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
      assertThat(agentForge4j.runtime()).isNotNull();
    });
  }

  @Test
  void agentRepositoryAccessibleViaComponents() {
    runner.run(ctx -> {
      AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
      assertThat(agentForge4j.components().agentRepository()).isNotNull();
    });
  }

  @Test
  void llmClientResolverAccessibleViaComponents() {
    runner.run(ctx -> {
      AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
      assertThat(agentForge4j.components().llmClientResolver()).isNotNull();
    });
  }

  @Test
  void fileSinkAccessibleViaComponents() {
    runner.run(ctx -> {
      AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
      assertThat(agentForge4j.components().fileSink()).isNotNull();
    });
  }

  @Test
  void customAgentRepositoryOverrides() {
    runner.withUserConfiguration(CustomAgentRepositoryConfiguration.class)
        .run(ctx -> {
          assertThat(ctx.getStartupFailure()).isNull();
          assertThat(ctx.getBean(AgentRepository.class))
              .isSameAs(CustomAgentRepositoryConfiguration.CUSTOM_AGENT_REPOSITORY);
        });
  }

  @Test
  void propertiesAgentsDirApplied(@TempDir Path agentsDir) {
    runner.withPropertyValues("agentforge4j.agents-path=" + agentsDir)
        .run(ctx -> assertThat(ctx.getStartupFailure()).isNull());
  }

  @Configuration
  static class CustomAgentRepositoryConfiguration {

    static final AgentRepository CUSTOM_AGENT_REPOSITORY = mock(AgentRepository.class);

    @Bean
    AgentRepository agentRepository() {
      return CUSTOM_AGENT_REPOSITORY;
    }
  }
}
