// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.runtime.RunContextManager;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.starter.logging.MdcRunContextManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeAutoConfigurationFileSinkTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          BootstrapAutoConfiguration.class,
          SpringRuntimeAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.load-shipped-agents=true",
          "agentforge4j.load-shipped-workflows=true");

  @Test
  void registersDefaultFileSinkWhenNoCustomBean() {
    runner.run(ctx -> {
      AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
      assertThat(agentForge4j.components().fileSink()).isNotNull();
      assertThat(agentForge4j.runtime()).isNotNull();
      assertThat(ctx.getBean(RunContextManager.class)).isInstanceOf(MdcRunContextManager.class);
      assertThat(ctx.getBean(SchemaProvider.class)).isInstanceOf(ClasspathSchemaProvider.class);
      assertThat(agentForge4j.components().clock()).isNotNull();
    });
  }

  @Test
  void customFileSinkReplacesDefault() {
    runner.withUserConfiguration(CustomFileSinkConfiguration.class)
        .run(ctx -> {
          assertThat(ctx.getBean(FileSink.class)).isInstanceOf(CustomFileSink.class);
          AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
          assertThat(agentForge4j.runtime()).isNotNull();
        });
  }

  @Test
  void workflowRuntimeBuildsWhenMaxNestingDepthConfigured() {
    runner.withPropertyValues("agentforge4j.max-nesting-depth=3")
        .run(ctx -> {
          assertThat(ctx.getStartupFailure()).isNull();
          AgentForge4j agentForge4j = ctx.getBean(AgentForge4j.class);
          WorkflowRuntime runtime = agentForge4j.runtime();
          assertThat(runtime).isNotNull();
        });
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
