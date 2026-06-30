// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

/**
 * Generic-path activation for the OpenAI-compatible gateway: gated on API key, emits the auth-header and responses-path
 * options, and preserves an empty {@code auth.header.prefix} (significant for auth schemes that use no prefix).
 */
class OpenAiCompatibleProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void registersAndMapsAuthHeaderAndResponsesPathOptions() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai-compatible.api-key=k",
        "agentforge4j.llm.openai-compatible.default-model=local-model",
        "agentforge4j.llm.openai-compatible.base-url=http://localhost:8080",
        "agentforge4j.llm.openai-compatible.auth-header-name=X-Api-Key",
        "agentforge4j.llm.openai-compatible.responses-path=/v1/responses")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("openai-compatible");
          assertThat(cfg.getBaseUrl()).isEqualTo("http://localhost:8080");
          assertThat(cfg.getOptions().requireString("auth.header.name")).isEqualTo("X-Api-Key");
          assertThat(cfg.getOptions().requireString("responses.path")).isEqualTo("/v1/responses");
        });
  }

  @Test
  void preservesAuthHeaderPrefixIncludingTrailingSpace() {
    // Supplied through a literal property source: the inlined `withPropertyValues` path round-trips the value through
    // system properties, which strips the trailing space before binding ever runs. A real properties/YAML source binds
    // the value verbatim, so this asserts the mapping preserves the significant trailing space ("Bearer " + token).
    Map<String, Object> properties = new HashMap<>();
    properties.put("agentforge4j.llm.openai-compatible.api-key", "k");
    properties.put("agentforge4j.llm.openai-compatible.base-url", "https://gateway.local");
    properties.put("agentforge4j.llm.openai-compatible.auth-header-name", "Authorization");
    properties.put("agentforge4j.llm.openai-compatible.auth-header-prefix", "Bearer ");
    properties.put("agentforge4j.llm.openai-compatible.responses-path", "/v1/responses");

    runner.withInitializer(context -> context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("openai-compatible-test", properties)))
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("openai-compatible");
          assertThat(cfg.getOptions().requireString("auth.header.name")).isEqualTo("Authorization");
          assertThat(cfg.getOptions().string("auth.header.prefix")).contains("Bearer ");
        });
  }

  @Test
  void treatsEmptyAuthHeaderPrefixAsNoPrefix() {
    // A no-prefix auth scheme (the header value is the bare credential) must still activate, and an empty prefix is
    // surfaced as absent rather than injecting a stray prefix.
    runner.withPropertyValues(
        "agentforge4j.llm.openai-compatible.api-key=k",
        "agentforge4j.llm.openai-compatible.base-url=https://gateway.local",
        "agentforge4j.llm.openai-compatible.auth-header-name=X-Api-Key",
        "agentforge4j.llm.openai-compatible.auth-header-prefix=",
        "agentforge4j.llm.openai-compatible.responses-path=/v1/responses")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("openai-compatible");
          assertThat(cfg.getOptions().string("auth.header.prefix")).isEmpty();
        });
  }
}
