package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmInvocationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedrockModelRegistryTest {

  private final BedrockModelRegistry registry = new BedrockModelRegistry();

  @Test
  void resolvesAnthropicToInvokeModelWithPromptCache() {
    BedrockModelResolution r = registry.resolve("anthropic.claude-3-5-sonnet-20240620-v1:0");
    assertThat(r.family()).isEqualTo(BedrockModelFamily.ANTHROPIC);
    assertThat(r.transport()).isEqualTo(BedrockTransportType.INVOKE_MODEL);
    assertThat(r.capabilities().promptCache()).isTrue();
  }

  @Test
  void resolvesRegionPrefixedAnthropicInferenceProfile() {
    assertThat(registry.resolve("us.anthropic.claude-3-haiku-20240307-v1:0").family())
        .isEqualTo(BedrockModelFamily.ANTHROPIC);
  }

  @Test
  void resolvesLlamaToConverseWithoutPromptCache() {
    BedrockModelResolution r = registry.resolve("meta.llama3-1-8b-instruct-v1:0");
    assertThat(r.family()).isEqualTo(BedrockModelFamily.LLAMA);
    assertThat(r.transport()).isEqualTo(BedrockTransportType.CONVERSE);
    assertThat(r.capabilities().promptCache()).isFalse();
  }

  @Test
  void resolvesRegionPrefixedLlama() {
    assertThat(registry.resolve("us.meta.llama3-1-70b-instruct-v1:0").family())
        .isEqualTo(BedrockModelFamily.LLAMA);
  }

  @Test
  void resolvesNovaToConverse() {
    BedrockModelResolution r = registry.resolve("amazon.nova-lite-v1:0");
    assertThat(r.family()).isEqualTo(BedrockModelFamily.NOVA);
    assertThat(r.transport()).isEqualTo(BedrockTransportType.CONVERSE);
    assertThat(r.capabilities().promptCache()).isFalse();
  }

  @Test
  void resolvesTitanToConverse() {
    BedrockModelResolution r = registry.resolve("amazon.titan-text-express-v1");
    assertThat(r.family()).isEqualTo(BedrockModelFamily.TITAN);
    assertThat(r.transport()).isEqualTo(BedrockTransportType.CONVERSE);
  }

  @Test
  void resolvesCaseInsensitively() {
    assertThat(registry.resolve("ANTHROPIC.claude-3-haiku-20240307-v1:0").family())
        .isEqualTo(BedrockModelFamily.ANTHROPIC);
    assertThat(registry.resolve("AMAZON.NOVA-lite-v1:0").family())
        .isEqualTo(BedrockModelFamily.NOVA);
  }

  @Test
  void resolvesUnknownVendorToError() {
    assertThatThrownBy(() -> registry.resolve("cohere.command-r-v1:0"))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("cohere.command-r-v1:0");
  }

  @Test
  void resolvesUnknownMetaNonLlamaModelToError() {
    // Matching is "meta.llama" only, so a non-Llama Meta id resolves as unknown.
    assertThatThrownBy(() -> registry.resolve("meta.foo"))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("meta.foo");
  }

  @Test
  void rejectsBlankModelId() {
    assertThatThrownBy(() -> registry.resolve("  "))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void supportsReflectsKnownAndUnknownFamilies() {
    assertThat(registry.supports("amazon.titan-text-express-v1")).isTrue();
    assertThat(registry.supports("anthropic.claude-3-haiku-20240307-v1:0")).isTrue();
    assertThat(registry.supports("cohere.command-r-v1:0")).isFalse();
    assertThat(registry.supports(null)).isFalse();
  }
}
