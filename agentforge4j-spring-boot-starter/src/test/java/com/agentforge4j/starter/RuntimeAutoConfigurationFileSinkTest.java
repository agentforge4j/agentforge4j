package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.RunContextManager;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.starter.files.NoOpFileSink;
import com.agentforge4j.starter.logging.MdcRunContextManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeAutoConfigurationFileSinkTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(RuntimeCollaboratorsConfiguration.class)
      .withConfiguration(AutoConfigurations.of(RuntimeAutoConfiguration.class));

  @Test
  void fileSinkBeanMethodReturnsNoOpImplementation() {
    assertThat(new RuntimeAutoConfiguration().fileSink()).isInstanceOf(NoOpFileSink.class);
  }

  @Test
  void registersNoOpFileSinkWhenNoCustomBean() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(FileSink.class);
      assertThat(ctx.getBean(FileSink.class)).isInstanceOf(NoOpFileSink.class);
      assertThat(ctx).hasSingleBean(WorkflowRuntime.class);
      assertThat(ctx.getBean(RunContextManager.class)).isInstanceOf(MdcRunContextManager.class);
      assertThat(ctx.getBean(SchemaProvider.class)).isInstanceOf(ClasspathSchemaProvider.class);
      assertThat(ctx.getBean(Clock.class)).isNotNull();
    });
  }

  @Test
  void customFileSinkReplacesDefault() {
    runner.withUserConfiguration(CustomFileSinkConfiguration.class)
        .run(ctx -> {
          assertThat(ctx.getBean(FileSink.class)).isInstanceOf(CustomFileSink.class);
          assertThat(ctx).hasSingleBean(WorkflowRuntime.class);
        });
  }

  @Configuration
  static class RuntimeCollaboratorsConfiguration {

    @Bean
    WorkflowRepository workflowRepository() {
      return mock(WorkflowRepository.class);
    }

    @Bean
    AgentRepository agentRepository() {
      return mock(AgentRepository.class);
    }

    @Bean
    WorkflowStateRepository workflowStateRepository() {
      return mock(WorkflowStateRepository.class);
    }

    @Bean
    WorkflowEventLog workflowEventLog() {
      return mock(WorkflowEventLog.class);
    }

    @Bean
    LlmClientResolver llmClientResolver() {
      return mock(LlmClientResolver.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AgentForge4jProperties agentForge4jProperties() {
      return new AgentForge4jProperties(null, null, null, false, false);
    }
  }

  @Configuration
  static class CustomFileSinkConfiguration {

    @Bean
    CustomFileSink fileSink() {
      return new CustomFileSink();
    }
  }

  static final class CustomFileSink implements FileSink {
    @Override
    public void write(String runId, String stepId, String path, String content) {
      // test double — persistence not exercised here
    }
  }
}
