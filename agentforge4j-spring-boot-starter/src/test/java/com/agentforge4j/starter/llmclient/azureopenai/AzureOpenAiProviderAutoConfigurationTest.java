package com.agentforge4j.starter.llmclient.azureopenai;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.azureopenai.AzureOpenAiConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AzureOpenAiProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AzureOpenAiProviderAutoConfiguration.class));

  @Test
  void registersWhenApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.azure-openai.api-key=key",
        "agentforge4j.llm.azure-openai.deployment-name=gpt-deployment",
        "agentforge4j.llm.azure-openai.endpoint=https://example.openai.azure.com",
        "agentforge4j.llm.azure-openai.api-version=2024-06-01")
        .run(ctx -> assertThat(ctx).hasSingleBean(AzureOpenAiConfiguration.class));
  }

  @Test
  void skipsWhenApiKeyMissing() {
    runner.withPropertyValues(
        "agentforge4j.llm.azure-openai.deployment-name=gpt-deployment",
        "agentforge4j.llm.azure-openai.endpoint=https://example.openai.azure.com",
        "agentforge4j.llm.azure-openai.api-version=2024-06-01")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AzureOpenAiConfiguration.class));
  }

  @Test
  void exposesDeploymentAsDefaultModelForLlmRouting() {
    runner.withPropertyValues(
        "agentforge4j.llm.azure-openai.api-key=key",
        "agentforge4j.llm.azure-openai.deployment-name=my-deploy",
        "agentforge4j.llm.azure-openai.endpoint=https://example.openai.azure.com",
        "agentforge4j.llm.azure-openai.api-version=v1")
        .run(ctx -> {
          AzureOpenAiConfiguration cfg = ctx.getBean(AzureOpenAiConfiguration.class);
          assertThat(cfg.getDeploymentName()).isEqualTo("my-deploy");
          assertThat(cfg.getDefaultModel()).isEqualTo("my-deploy");
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getProviderName()).isEqualTo("azure-openai");
        });
  }
}
