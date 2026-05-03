package com.agentforge4j.llm.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.llm.LlmExecutionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BedrockAnthropicInvokeSerializerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void rejectsNullObjectMapper() {
    assertThatThrownBy(() -> new BedrockAnthropicInvokeSerializer(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ObjectMapper");
  }

  @Test
  void buildsAnthropicMessagesPayload() throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder()
        .defaultModel("anthropic.claude-3-5-sonnet-20240620-v1:0")
        .maxTokens(512)
        .temperature(0.2)
        .build();
    LlmExecutionRequest req = new LlmExecutionRequest(
        "bedrock",
        null,
        "You are the system.",
        "User says hi.");

    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    JsonNode root = mapper.readTree(json);

    assertThat(root.path("anthropic_version").asText()).isEqualTo("bedrock-2023-05-31");
    assertThat(root.path("max_tokens").asInt()).isEqualTo(512);
    assertThat(root.path("temperature").asDouble()).isEqualTo(0.2);
    assertThat(root.path("system").asText()).isEqualTo("You are the system.");
    assertThat(root.path("messages")).hasSize(1);
    assertThat(root.path("messages").get(0).path("role").asText()).isEqualTo("user");
    assertThat(root.path("messages").get(0).path("content").asText()).isEqualTo("User says hi.");
  }

  @Test
  void omitsTemperatureWhenNull() throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder().temperature(null).build();
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    assertThat(mapper.readTree(json).has("temperature")).isFalse();
  }

  @Test
  void usesDefaultMaxTokensWhenUnset() throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder().maxTokens(null).build();
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    assertThat(mapper.readTree(json).path("max_tokens").asInt())
        .isEqualTo(BedrockAnthropicInvokeSerializer.DEFAULT_MAX_TOKENS);
  }

  @Test
  void rejectsNonAnthropicModelId() {
    assertThatThrownBy(() -> BedrockAnthropicInvokeSerializer.validateAnthropicModelId("meta.llama3-8b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("anthropic.");
  }

  @Test
  void acceptsModelIdWithCaseInsensitiveAnthropicPrefix() {
    BedrockAnthropicInvokeSerializer.validateAnthropicModelId("ANTHROPIC.claude-3-haiku-20240307-v1:0");
  }

  @Test
  void rejectsNullRequestInToJson() {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.defaults();
    assertThatThrownBy(() -> serializer.toJson(null, cfg.getDefaultModel(), cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("request");
  }

  @Test
  void rejectsBlankModelIdInToJson() {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    assertThatThrownBy(() -> serializer.toJson(req, "  ", FixedBedrockConfiguration.defaults()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("modelId");
  }

  @Test
  void rejectsNullConfigurationInToJson() {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    assertThatThrownBy(() -> serializer.toJson(req, "anthropic.claude-3-haiku-20240307-v1:0", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("config");
  }

  @Test
  void emitsConfiguredAnthropicVersion() throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder()
        .anthropicVersion("bedrock-2023-05-31-custom")
        .build();
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    assertThat(mapper.readTree(json).path("anthropic_version").asText())
        .isEqualTo("bedrock-2023-05-31-custom");
  }

  @Test
  void usesDefaultMaxTokensWhenConfiguredMaxTokensIsZero() throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder().maxTokens(0).build();
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    assertThat(mapper.readTree(json).path("max_tokens").asInt())
        .isEqualTo(BedrockAnthropicInvokeSerializer.DEFAULT_MAX_TOKENS);
  }
}
