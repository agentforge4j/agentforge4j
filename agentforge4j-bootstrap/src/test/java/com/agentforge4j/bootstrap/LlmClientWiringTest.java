// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmSecretResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end wiring tests for {@link LlmClientWiring} (OSS #98). Drives
 * {@link LlmClientWiring#assembleClients} with explicit captor factories, so the neutral
 * configuration, options, and resolved credential the bootstrap layer produces can be asserted
 * without depending on real provider modules or {@link java.util.ServiceLoader} discovery.
 *
 * <p>Environment variables cannot be set in-process (no JUnit Pioneer), so the auto-discovery path is
 * exercised via system properties in the canonical dotted key form — exactly what the
 * {@code AGENTFORGE4J_*} env normalization produces for single-word providers. The hyphenated-provider
 * env boundary (#99) is pinned via the collapsed-dot system-property key.
 */
class LlmClientWiringTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final LlmSecretResolver RESOLVER = new EnvSystemPropertyLlmSecretResolver();

  private final Map<String, String> originalValues = new HashMap<>();

  @AfterEach
  void cleanUp() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      if (entry.getValue() == null) {
        System.clearProperty(entry.getKey());
      } else {
        System.setProperty(entry.getKey(), entry.getValue());
      }
    }
    originalValues.clear();
    CaptorLlmClientFactorySupport.reset();
  }

  private static List<LlmClientFactory> captors() {
    return List.of(
        new OpenAiCaptorLlmClientFactory(),
        new OllamaCaptorLlmClientFactory(),
        new OpenAiCompatibleCaptorLlmClientFactory());
  }

  private static List<LlmClient> assemble(Map<String, LlmProviderConfig> programmatic) {
    return LlmClientWiring.assembleClients(MAPPER, programmatic, RESOLVER, captors());
  }

  @Test
  void programmaticBearerProviderConstructsClientWithNeutralConfig() {
    LlmProviderConfig openai = LlmProviderConfig.openai().defaults().apiKey("sk-prog")
        .option("request.timeout", "PT30S").build();

    List<LlmClient> clients = assemble(Map.of("openai", openai));

    assertThat(clients).hasSize(1);
    LlmClientFactoryContext context = captured("openai");
    assertThat(context.configuration().getBaseUrl()).isNotNull();
    assertThat(context.configuration().getApiKeyReference()).isPresent();
    assertThat(context.requireApiKey().value()).isEqualTo("sk-prog");
  }

  @Test
  void openAiCompatibleConstructsViaWithLlmProviderWithOptions() {
    LlmProviderConfig config = LlmProviderConfig.openAiCompatible().defaults().apiKey("sk-compat")
        .baseUrl("https://api.example.com")
        .option("auth.header.name", "Authorization")
        .option("responses.path", "/v1/responses")
        .build();

    List<LlmClient> clients = assemble(Map.of("openai-compatible", config));

    assertThat(clients).hasSize(1);
    LlmClientFactoryContext context = captured("openai-compatible");
    assertThat(context.configuration().getOptions().requireString("auth.header.name"))
        .isEqualTo("Authorization");
    assertThat(context.configuration().getOptions().requireString("responses.path"))
        .isEqualTo("/v1/responses");
  }

  @Test
  void noKeyProviderConstructsWithoutCredential() {
    LlmProviderConfig ollama = LlmProviderConfig.ollama().defaults().build();

    List<LlmClient> clients = assemble(Map.of("ollama", ollama));

    assertThat(clients).hasSize(1);
    assertThat(captured("ollama").configuration().getApiKeyReference()).isEmpty();
  }

  @Test
  void bearerProviderConstructsViaSystemPropertyDottedKeys() {
    setProperty("agentforge4j.llm.openai.api.key", "sk-sys");
    setProperty("agentforge4j.llm.openai.base.url", "https://api.openai.com/v1/responses");
    setProperty("agentforge4j.llm.openai.request.timeout", "PT30S");

    List<LlmClient> clients = assemble(Map.of());

    assertThat(clients).hasSize(1);
    LlmClientFactoryContext context = captured("openai");
    assertThat(context.requireApiKey().value()).isEqualTo("sk-sys");
    assertThat(context.configuration().getBaseUrl()).isEqualTo("https://api.openai.com/v1/responses");
    assertThat(context.configuration().getOptions().requireString("request.timeout"))
        .isEqualTo("PT30S");
  }

  @Test
  void programmaticWinsOverSystemProperty() {
    setProperty("agentforge4j.llm.openai.api.key", "sk-sys");
    LlmProviderConfig programmatic = LlmProviderConfig.openai().defaults().apiKey("sk-prog").build();

    assemble(Map.of("openai", programmatic));

    assertThat(captured("openai").requireApiKey().value()).isEqualTo("sk-prog");
  }

  @Test
  void indirectCredentialReferenceResolvesAndDoesNotLeak() {
    setProperty("OPENAI_KEY_HOLDER", "sk-resolved");
    setProperty("agentforge4j.llm.openai.api.key", "${sysprop:OPENAI_KEY_HOLDER}");
    setProperty("agentforge4j.llm.openai.base.url", "https://api.openai.com/v1/responses");

    assemble(Map.of());

    LlmClientFactoryContext context = captured("openai");
    assertThat(context.requireApiKey().value()).isEqualTo("sk-resolved");
    assertThat(context.configuration().getApiKeyReference().orElseThrow().toString())
        .doesNotContain("sk-resolved")
        .contains("sysprop:OPENAI_KEY_HOLDER");
  }

  @Test
  void unknownConfiguredProviderFailsFast() {
    LlmProviderConfig bedrock = LlmProviderConfig.bedrock().defaults().build();

    assertThatThrownBy(() -> assemble(Map.of("bedrock", bedrock)))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("bedrock");
  }

  @Test
  void duplicateContributorFailsFast() {
    List<LlmClientFactory> duplicates = List.of(
        new OpenAiCaptorLlmClientFactory(), new OpenAiCaptorLlmClientFactory());

    assertThatThrownBy(() -> LlmClientWiring.assembleClients(MAPPER, Map.of(), RESOLVER, duplicates))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("Duplicate")
        .hasMessageContaining("openai");
  }

  @Test
  void invalidConnectTimeoutFailsFast() {
    setProperty("agentforge4j.llm.openai.api.key", "sk");
    setProperty("agentforge4j.llm.openai.connect.timeout", "not-a-duration");

    assertThatThrownBy(() -> assemble(Map.of()))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("connect.timeout");
  }

  @Test
  void unresolvableCredentialReferenceFailsFast() {
    setProperty("agentforge4j.llm.openai.api.key", "${sysprop:MISSING_KEY_HOLDER}");
    setProperty("agentforge4j.llm.openai.base.url", "https://api.openai.com/v1/responses");

    assertThatThrownBy(() -> assemble(Map.of()))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("MISSING_KEY_HOLDER");
  }

  @Test
  void hyphenatedProviderBindsViaSystemPropertyHyphenForm() {
    setProperty("agentforge4j.llm.openai-compatible.api.key", "sk");
    setProperty("agentforge4j.llm.openai-compatible.base.url", "https://api.example.com");
    setProperty("agentforge4j.llm.openai-compatible.auth.header.name", "Authorization");
    setProperty("agentforge4j.llm.openai-compatible.responses.path", "/v1/responses");

    assemble(Map.of());

    assertThat(CaptorLlmClientFactorySupport.CAPTURED).containsKey("openai-compatible");
  }

  @Test
  void hyphenatedProviderViaCollapsedDotKeyFailsFastAsUnknown() {
    // The form an env var would normalize to (AGENTFORGE4J_LLM_OPENAI_COMPATIBLE_* -> dots). It does
    // not match the hyphenated provider id, so it surfaces as an unknown provider (fail fast) rather
    // than being silently dropped; disambiguating it is #99.
    setProperty("agentforge4j.llm.openai.compatible.api.key", "sk");
    setProperty("agentforge4j.llm.openai.compatible.base.url", "https://api.example.com");

    assertThatThrownBy(() -> assemble(Map.of()))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("openai.compatible");
    assertThat(CaptorLlmClientFactorySupport.CAPTURED).doesNotContainKey("openai-compatible");
  }

  @Test
  void configuredProviderMissingRequiredApiKeyFailsFast() {
    // openai is explicitly configured (base.url + request.timeout) but missing its required api.key.
    setProperty("agentforge4j.llm.openai.base.url", "https://api.openai.com/v1/responses");
    setProperty("agentforge4j.llm.openai.request.timeout", "PT30S");

    assertThatThrownBy(() -> assemble(Map.of()))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("openai")
        .hasMessageContaining("API key");
  }

  @Test
  void unknownProviderConfiguredViaSystemPropertyFailsFast() {
    setProperty("agentforge4j.llm.madeupprovider.api.key", "sk");

    assertThatThrownBy(() -> assemble(Map.of()))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("madeupprovider");
  }

  @Test
  void nonProviderNamespacesAreNotMistakenForUnknownProviders() {
    // cache / model-tiers live under agentforge4j.llm.* but are not providers — they must not trip
    // the unknown-provider check.
    setProperty("agentforge4j.llm.cache.enabled", "true");
    setProperty("agentforge4j.llm.model-tiers.openai.fast", "gpt-4o-mini");

    assertThat(assemble(Map.of())).isEmpty();
  }

  private static LlmClientFactoryContext captured(String provider) {
    LlmClientFactoryContext context = CaptorLlmClientFactorySupport.CAPTURED.get(provider);
    assertThat(context).as("captured context for %s", provider).isNotNull();
    return context;
  }

  private void setProperty(String key, String value) {
    originalValues.putIfAbsent(key, System.getProperty(key));
    System.setProperty(key, value);
  }
}
