// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Anti-regression cover for relaxed binding through the generic provider path. The former per-provider
 * {@code @ConfigurationProperties} records bound the {@code agentforge4j.llm.<provider>.*} subtree with full relaxed
 * binding, so a provider activated identically whether configured in kebab-case, camelCase, or environment-variable
 * form. The generic registrar must preserve that: a map bind of the subtree cannot reconstruct the canonical kebab key
 * from camelCase ({@code apiKey}) or an environment variable ({@code ..._API_KEY} → {@code api.key}), so the registrar
 * resolves each key through a scalar relaxed {@link org.springframework.boot.context.properties.bind.Binder} lookup of
 * its fully-qualified name. These tests pin all three forms, using OpenAI as the migrated vehicle and asserting that
 * non-gating fields ({@code default-model}, {@code url}) also flow through, not just activation.
 */
class RelaxedPropertyBindingTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void activatesFromKebabCaseProperties() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai.api-key=sk-kebab",
        "agentforge4j.llm.openai.default-model=gpt-4o",
        "agentforge4j.llm.openai.url=https://kebab.example/v1")
        .run(ctx -> assertOpenAiBound(ctx.getBean(LlmClientConfiguration.class), "https://kebab.example/v1"));
  }

  @Test
  void activatesFromCamelCaseProperties() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai.apiKey=sk-camel",
        "agentforge4j.llm.openai.defaultModel=gpt-4o",
        "agentforge4j.llm.openai.url=https://camel.example/v1")
        .run(ctx -> assertOpenAiBound(ctx.getBean(LlmClientConfiguration.class), "https://camel.example/v1"));
  }

  @Test
  void activatesFromEnvironmentVariables() {
    Map<String, Object> env = new HashMap<>();
    env.put("AGENTFORGE4J_LLM_OPENAI_API_KEY", "sk-env");
    env.put("AGENTFORGE4J_LLM_OPENAI_DEFAULT_MODEL", "gpt-4o");
    env.put("AGENTFORGE4J_LLM_OPENAI_URL", "https://env.example/v1");

    runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
        .run(ctx -> assertOpenAiBound(ctx.getBean(LlmClientConfiguration.class), "https://env.example/v1"));
  }

  private static void assertOpenAiBound(LlmClientConfiguration cfg, String expectedBaseUrl) {
    assertThat(cfg.getProviderName()).isEqualTo("openai");
    assertThat(cfg.getApiKeyReference()).isPresent();
    assertThat(cfg.getDefaultModel()).isEqualTo("gpt-4o");
    assertThat(cfg.getBaseUrl()).isEqualTo(expectedBaseUrl);
  }
}
