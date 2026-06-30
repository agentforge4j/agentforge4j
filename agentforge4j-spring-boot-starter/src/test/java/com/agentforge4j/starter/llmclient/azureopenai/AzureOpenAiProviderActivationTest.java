// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.azureopenai;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Generic-path activation for Azure OpenAI, including the fan-out mapping: {@code deployment-name} feeds both the
 * default model and the {@code deployment} option, {@code endpoint} becomes the base URL, and {@code api-version}
 * becomes the {@code api.version} option.
 */
class AzureOpenAiProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void registersWhenApiKeySet() {
    runner.withPropertyValues("agentforge4j.llm.azure-openai.api-key=k")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  /**
   * Relaxed binding regression for the dashed provider id: an environment variable
   * ({@code AGENTFORGE4J_LLM_AZURE_OPENAI_API_KEY}) cannot express the {@code azure-openai} dash, so a map bind of the
   * subtree would never see it. The scalar fully-qualified Binder lookup must still activate the provider and map its
   * fields, proving the fix covers {@code azure-openai}, not only single-word ids such as {@code openai}.
   */
  @Test
  void activatesFromEnvironmentVariablesDespiteDashedProviderId() {
    Map<String, Object> env = new HashMap<>();
    env.put("AGENTFORGE4J_LLM_AZURE_OPENAI_API_KEY", "env-key");
    env.put("AGENTFORGE4J_LLM_AZURE_OPENAI_DEPLOYMENT_NAME", "gpt-4o");
    env.put("AGENTFORGE4J_LLM_AZURE_OPENAI_ENDPOINT", "https://env-resource.openai.azure.com");
    env.put("AGENTFORGE4J_LLM_AZURE_OPENAI_API_VERSION", "2024-02-01");

    runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("azure-openai");
          assertThat(cfg.getApiKeyReference()).isPresent();
          assertThat(cfg.getDefaultModel()).isEqualTo("gpt-4o");
          assertThat(cfg.getBaseUrl()).isEqualTo("https://env-resource.openai.azure.com");
          assertThat(cfg.getOptions().requireString("deployment")).isEqualTo("gpt-4o");
          assertThat(cfg.getOptions().requireString("api.version")).isEqualTo("2024-02-01");
        });
  }

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void fansDeploymentNameAndEndpointIntoNeutralFieldsAndOptions() {
    runner.withPropertyValues(
        "agentforge4j.llm.azure-openai.api-key=k",
        "agentforge4j.llm.azure-openai.deployment-name=gpt-4o",
        "agentforge4j.llm.azure-openai.endpoint=https://resource.openai.azure.com",
        "agentforge4j.llm.azure-openai.api-version=2024-02-01",
        "agentforge4j.llm.azure-openai.request-timeout=PT45S")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("azure-openai");
          assertThat(cfg.getDefaultModel()).isEqualTo("gpt-4o");
          assertThat(cfg.getBaseUrl()).isEqualTo("https://resource.openai.azure.com");
          assertThat(cfg.getApiKeyReference()).isPresent();
          assertThat(cfg.getOptions().requireString("deployment")).isEqualTo("gpt-4o");
          assertThat(cfg.getOptions().requireString("api.version")).isEqualTo("2024-02-01");
          assertThat(cfg.getOptions().requireDuration("request.timeout")).isEqualTo(Duration.ofSeconds(45));
        });
  }
}
