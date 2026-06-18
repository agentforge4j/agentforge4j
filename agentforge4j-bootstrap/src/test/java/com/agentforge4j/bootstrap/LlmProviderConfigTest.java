// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmSecretReference;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderConfigTest {

  @Test
  void openaiDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.openai().defaults().build();
    assertThat(config.provider()).isEqualTo("openai");
    assertThat(config.baseUrl()).isNotNull();
    assertThat(config.defaultModel()).isNotNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void claudeDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.claude().defaults().build();
    assertThat(config.provider()).isEqualTo("claude");
    assertThat(config.baseUrl()).isNotNull();
    assertThat(config.defaultModel()).isNotNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void ollamaDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.ollama().defaults().build();
    assertThat(config.provider()).isEqualTo("ollama");
    assertThat(config.baseUrl()).isNotNull();
    assertThat(config.defaultModel()).isNotNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void vllmDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.vllm().defaults().build();
    assertThat(config.provider()).isEqualTo("vllm");
    assertThat(config.baseUrl()).isEqualTo("http://localhost:8000");
    assertThat(config.defaultModel()).isNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void geminiDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.gemini().defaults().build();
    assertThat(config.provider()).isEqualTo("gemini");
    assertThat(config.baseUrl()).isNotNull();
    assertThat(config.defaultModel()).isNotNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void mistralDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.mistral().defaults().build();
    assertThat(config.provider()).isEqualTo("mistral");
    assertThat(config.baseUrl()).isNotNull();
    assertThat(config.defaultModel()).isNotNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void azureOpenAiDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.azureOpenAi().defaults().build();
    assertThat(config.provider()).isEqualTo("azure-openai");
    assertThat(config.baseUrl()).isNull();
    assertThat(config.defaultModel()).isNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void openAiCompatibleDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.openAiCompatible().defaults().build();
    assertThat(config.provider()).isEqualTo("openai-compatible");
    assertThat(config.baseUrl()).isNull();
    assertThat(config.defaultModel()).isNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void bedrockDefaultsArePopulated() {
    LlmProviderConfig config = LlmProviderConfig.bedrock().defaults().build();
    assertThat(config.provider()).isEqualTo("bedrock");
    assertThat(config.baseUrl()).isNull();
    assertThat(config.defaultModel()).isNull();
    assertThat(config.connectTimeout()).isNotNull();
    assertThat(config.apiKeyReference()).isNull();
  }

  @Test
  void apiKeyOverrideIsApplied() {
    LlmProviderConfig config = LlmProviderConfig.openai().defaults().apiKey("test-key").build();
    assertThat(config.apiKeyReference()).isEqualTo(LlmSecretReference.literal("test-key"));
  }

  @Test
  void baseUrlOverrideIsApplied() {
    LlmProviderConfig config = LlmProviderConfig.openai()
        .defaults()
        .baseUrl("https://custom.example.com")
        .build();
    assertThat(config.baseUrl()).isEqualTo("https://custom.example.com");
  }

  @Test
  void defaultModelOverrideIsApplied() {
    LlmProviderConfig config = LlmProviderConfig.openai()
        .defaults()
        .defaultModel("gpt-99-turbo")
        .build();
    assertThat(config.defaultModel()).isEqualTo("gpt-99-turbo");
  }

  @Test
  void connectTimeoutOverrideIsApplied() {
    Duration expected = Duration.ofSeconds(99);
    LlmProviderConfig config = LlmProviderConfig.openai()
        .defaults()
        .connectTimeout(expected)
        .build();
    assertThat(config.connectTimeout()).isEqualTo(expected);
  }

  @Test
  void blankApiKeyThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().apiKey("").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullApiKeyThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().apiKey(null).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankBaseUrlThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().baseUrl("").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullBaseUrlThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().baseUrl(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankDefaultModelThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().defaultModel(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullDefaultModelThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().defaultModel(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullConnectTimeoutThrows() {
    assertThatThrownBy(() -> LlmProviderConfig.openai().defaults().connectTimeout(null).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void recordEqualityByValue() {
    LlmProviderConfig first = LlmProviderConfig.openai().defaults().apiKey("k").build();
    LlmProviderConfig second = LlmProviderConfig.openai().defaults().apiKey("k").build();
    assertThat(first).isEqualTo(second);
  }

  @Test
  void defaultsCallIsFluentNoOp() {
    LlmProviderConfig once = LlmProviderConfig.openai().defaults().build();
    LlmProviderConfig twice = LlmProviderConfig.openai().defaults().defaults().build();
    assertThat(twice).isEqualTo(once);
  }
}
