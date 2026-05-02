package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeLlmClientTest {

  private static FixedClaudeConfiguration.Builder baseConfig() {
    return FixedClaudeConfiguration.builder();
  }

  private static String requestBodyToString(HttpRequest httpRequest) throws Exception {
    HttpRequest.BodyPublisher publisher = httpRequest.bodyPublisher().orElseThrow();
    CompletableFuture<String> result = new CompletableFuture<>();
    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
      private final StringBuilder sb = new StringBuilder();

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(ByteBuffer buffer) {
        sb.append(StandardCharsets.UTF_8.decode(buffer.duplicate()));
      }

      @Override
      public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
      }

      @Override
      public void onComplete() {
        result.complete(sb.toString());
      }
    });
    return result.get();
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldConstructWithValidConfiguration() {
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();
      ObjectMapper mapper = new ObjectMapper();

      ClaudeLlmClient client = new ClaudeLlmClient(mapper, config);

      assertThat(client.getProviderName()).isEqualTo("claude");
      assertThat(client.getDefaultModel()).isEqualTo("claude-3-opus-20240229");
    }

    @Test
    void shouldThrowWhenObjectMapperNull() {
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();

      assertThatThrownBy(() -> new ClaudeLlmClient(null, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void shouldThrowWhenConfigurationNull() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new ClaudeLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenApiKeyBlank() {
      ClaudeConfiguration config = baseConfig().apiKey("  ").build();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new ClaudeLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiKey");
    }

    @Test
    void shouldThrowWhenApiVersionBlank() {
      ClaudeConfiguration config = baseConfig().apiKey("key").apiVersion("").build();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new ClaudeLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiVersion");
    }

    @Test
    void shouldThrowWhenUrlBlank() {
      ClaudeConfiguration config = baseConfig().apiKey("key").url("").build();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new ClaudeLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("URL");
    }

    @Test
    void shouldThrowWhenMaxTokenSizeNotPositive() {
      ClaudeConfiguration config = baseConfig().apiKey("key").maxTokenSize(0).build();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new ClaudeLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxTokenSize");
    }

    @Test
    void shouldThrowWhenDefaultModelBlank() {
      ClaudeConfiguration config = baseConfig().apiKey("key").defaultModel("").build();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new ClaudeLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("default model");
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void shouldThrowWhenJsonBlank() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse(""))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("json");
    }

    @Test
    void shouldThrowWhenJsonNull() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse(null))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void shouldThrowWhenJsonMalformed() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("not-json"))
          .isInstanceOf(IOException.class);
    }

    @Test
    void shouldThrowWhenContentNull() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{\"content\":null}"))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenContentEmptyArray() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{\"content\":[]}"))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenNoTextBlock() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = "{\"content\":[{\"type\":\"tool_use\"}]}";

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no text content");
    }

    @Test
    void shouldIgnoreUnknownPropertiesOnToolUseBlocks() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = """
          {"content":[{"type":"tool_use","id":"toolu_01","name":"weather"}]}
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no text content");
    }

    @Test
    void shouldThrowWhenTextBlocksOnlyBlank() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = """
          {"content":[{"type":"text","text":"  "},{"type":"text","text":""}]}
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no text content");
    }

    @Test
    void shouldUseFirstTextBlockAfterNonTextBlocks() throws IOException {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = """
          {"content":[{"type":"tool_use"},{"type":"text","text":"from-text"}]}
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("from-text");
    }

    @Test
    void shouldPickFirstNonBlankTextBlock() throws IOException {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = """
          {"content":[{"type":"text","text":""},{"type":"text","text":"second"}]}
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("second");
    }

    @Test
    void shouldExtractPlainText() throws IOException {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = """
          {"content":[{"type":"text","text":"  Hello  "}]}
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("Hello");
    }

    @Test
    void shouldStripMarkdownCodeFenceFromText() throws IOException {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      ObjectMapper om = new ObjectMapper();
      ObjectNode root = om.createObjectNode();
      ArrayNode content = root.putArray("content");
      ObjectNode block = content.addObject();
      block.put("type", "text");
      block.put("text", "```java\nline1\nline2\n```");
      String body = om.writeValueAsString(root);

      assertThat(client.validateAndExtractResponse(body)).isEqualTo("line1\nline2");
    }

    @Test
    void shouldIgnoreNullContentBlocks() throws IOException {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      // Jackson typically deserializes JSON null elements as nulls in list
      String json = "{\"content\":[null,{\"type\":\"text\",\"text\":\"ok\"}]}";

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("ok");
    }

    @Test
    void shouldThrowWhenContentFieldMissing() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{}"))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenTextBlockHasNullTextProperty() {
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
      String json = "{\"content\":[{\"type\":\"text\"}]}";

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no text content");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void shouldPostToConfiguredMessagesUrl() throws Exception {
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel(
          "claude", "system text", "user text");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.uri().toString()).isEqualTo("https://api.anthropic.test/v1/messages");
    }

    @Test
    void shouldSetContentTypeApiKeyAndVersionHeaders() {
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel(
          "claude", "system text", "user text");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type")).contains("application/json");
      assertThat(httpRequest.headers().firstValue("x-api-key")).contains("test-api-key");
      assertThat(httpRequest.headers().firstValue("anthropic-version")).contains("2023-06-01");
    }

    @Test
    void shouldSerializeExpectedRequestFields() throws Exception {
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel(
          "claude", "my system", "my user");

      String raw = requestBodyToString(client.buildHttpRequest(request));
      JsonNode root = new ObjectMapper().readTree(raw);

      assertThat(root.path("model").asText()).isEqualTo("claude-3-opus-20240229");
      assertThat(root.path("max_tokens").asInt()).isEqualTo(1024);
      assertThat(root.path("system").asText()).isEqualTo("my system");
      assertThat(root.path("messages").isArray()).isTrue();
      assertThat(root.path("messages")).hasSize(1);
      assertThat(root.path("messages").path(0).path("content").asText()).isEqualTo("my user");
    }

    @Test
    void shouldPreferDefaultModelOverRequestModelWhenDefaultIsNonBlank() throws Exception {
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "claude", "other-model", "sys", "user");

      String raw = requestBodyToString(client.buildHttpRequest(request));
      JsonNode root = new ObjectMapper().readTree(raw);

      assertThat(root.path("model").asText()).isEqualTo("claude-3-opus-20240229");
    }

    @Test
    void shouldApplyRequestTimeoutToHttpRequest() {
      Duration shortTimeout = Duration.ofMillis(500);
      ClaudeConfiguration config = baseConfig()
          .apiKey("k")
          .apiVersion("v")
          .defaultModel("m")
          .requestTimeout(shortTimeout)
          .maxTokenSize(100)
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("claude", "s", "u");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.timeout()).contains(shortTimeout);
    }
  }
}
