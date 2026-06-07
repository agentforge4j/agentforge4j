package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThat(root.path("system").isArray()).isTrue();
    assertThat(root.path("system")).hasSize(1);
    assertThat(root.path("system").path(0).path("text").asText()).isEqualTo("You are the system.");
    assertThat(root.path("system").path(0).has("cache_control")).isFalse();
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

  @Test
  void prefers_request_max_output_tokens_over_configuration() throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder().maxTokens(512).build();
    LlmExecutionRequest req =
        new LlmExecutionRequest("bedrock", null, "s", "u", 2048);
    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    assertThat(mapper.readTree(json).path("max_tokens").asInt()).isEqualTo(2048);
  }

  @Test
  void falls_back_to_configured_max_tokens_when_request_max_output_tokens_absent()
      throws Exception {
    BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
    BedrockConfiguration cfg = FixedBedrockConfiguration.builder().maxTokens(777).build();
    LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
    String json = serializer.toJson(req, cfg.getDefaultModel(), cfg);
    assertThat(mapper.readTree(json).path("max_tokens").asInt()).isEqualTo(777);
  }

  @Nested
  class PromptCacheRequestBodyTests {

    private static String cacheableLayerUtf8(int utf8Bytes) {
      return "x".repeat(utf8Bytes);
    }

    private static int utf8Length(String value) {
      return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static PromptLayerBoundaries boundariesFor(
        String layer1, String separator, String layer2, String layer3) {
      int layer1End = utf8Length(layer1);
      int layer2End = utf8Length(layer1 + separator + layer2);
      Integer layer3End = layer3 == null
          ? null
          : utf8Length(layer1 + separator + layer2 + separator + layer3);
      return new PromptLayerBoundaries(layer1End, layer2End, layer3End);
    }

    @Test
    void shouldPlaceCacheControlOnQualifyingLayerBlocks() throws Exception {
      String separator = "\n\n";
      String layer1 = cacheableLayerUtf8(4096);
      String layer2 = cacheableLayerUtf8(4096);
      String layer3 = cacheableLayerUtf8(4096);
      String systemPrompt = layer1 + separator + layer2 + separator + layer3;
      PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, layer3);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "bedrock", "anthropic.claude-3-opus-20240229-v1:0", systemPrompt, "user", null,
          boundaries);
      BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
      BedrockConfiguration cfg = FixedBedrockConfiguration.defaults();

      JsonNode system = mapper.readTree(
          serializer.toJson(request, cfg.getDefaultModel(), cfg)).path("system");

      assertThat(system).hasSize(3);
      assertThat(system.path(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
      assertThat(system.path(1).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
      assertThat(system.path(2).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
      assertThat(system.path(0).has("ttl")).isFalse();
    }

    @Test
    void shouldOmitCacheControlOnSubThresholdLayer() throws Exception {
      String separator = "\n\n";
      String layer1 = cacheableLayerUtf8(100);
      String layer2 = cacheableLayerUtf8(4096);
      String layer3 = cacheableLayerUtf8(4096);
      String systemPrompt = layer1 + separator + layer2 + separator + layer3;
      PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, layer3);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "bedrock", "anthropic.claude-3-opus-20240229-v1:0", systemPrompt, "user", null,
          boundaries);
      BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);

      JsonNode system = mapper.readTree(serializer.toJson(
          request, "anthropic.claude-3-opus-20240229-v1:0", FixedBedrockConfiguration.defaults()))
          .path("system");

      assertThat(system.path(0).has("cache_control")).isFalse();
      assertThat(system.path(1).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
      assertThat(system.path(2).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
    }

    @Test
    void shouldOmitCacheControlWhenBoundariesNull() throws Exception {
      LlmExecutionRequest request = new LlmExecutionRequest("bedrock", null, "my system", "my user");
      BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);
      JsonNode system = mapper.readTree(serializer.toJson(
          request, FixedBedrockConfiguration.defaults().getDefaultModel(),
          FixedBedrockConfiguration.defaults())).path("system");

      assertThat(system).hasSize(1);
      assertThat(system.path(0).path("text").asText()).isEqualTo("my system");
      assertThat(system.path(0).has("cache_control")).isFalse();
    }

    @Test
    void shouldEmitNoTtlFieldInRequestBody() {
      String layer1 = cacheableLayerUtf8(4096);
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(
          utf8Length(layer1), utf8Length(layer1), null);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "bedrock", "anthropic.claude-3-opus-20240229-v1:0", layer1, "user", null,
          boundaries);
      BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);

      String json = serializer.toJson(
          request, "anthropic.claude-3-opus-20240229-v1:0", FixedBedrockConfiguration.defaults());

      assertThat(json).doesNotContain("\"ttl\"");
    }

    @Test
    void shouldNotEmitCachePoint() {
      String layer1 = cacheableLayerUtf8(4096);
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(
          utf8Length(layer1), utf8Length(layer1), null);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "bedrock", "anthropic.claude-3-opus-20240229-v1:0", layer1, "user", null, boundaries);
      BedrockAnthropicInvokeSerializer serializer = new BedrockAnthropicInvokeSerializer(mapper);

      String json = serializer.toJson(
          request, "anthropic.claude-3-opus-20240229-v1:0", FixedBedrockConfiguration.defaults());

      assertThat(json).doesNotContain("cachePoint");
    }
  }
}
