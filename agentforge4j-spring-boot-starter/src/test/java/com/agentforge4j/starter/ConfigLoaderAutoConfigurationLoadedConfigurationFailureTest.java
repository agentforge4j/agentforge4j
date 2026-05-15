package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ConfigLoaderAutoConfigurationLoadedConfigurationFailureTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(ObjectMapperTestConfiguration.class)
      .withConfiguration(AutoConfigurations.of(
          JacksonAutoConfiguration.class,
          ConfigLoaderAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.agents-path=",
          "agentforge4j.workflows-path=",
          "agentforge4j.load-shipped-workflows=false",
          "agentforge4j.load-shipped-agents=false");

  @Test
  void startupFailsWhenNoConfigurationSourceConfigured() {
    runner.run(ctx -> assertThat(ctx.getStartupFailure())
        .isInstanceOf(BeanCreationException.class)
        .rootCause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentForge4j requires at least one of"));
  }

  @Configuration
  static class ObjectMapperTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
