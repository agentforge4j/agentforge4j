package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.vllm.dto.InputRole;
import com.agentforge4j.llm.vllm.dto.VllmMessage;
import com.agentforge4j.llm.vllm.dto.VllmRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VllmLlmClientTest {

  private static final String VALID_CHAT_COMPLETIONS_JSON = """
      {
        "choices": [
          {
            "message": {
              "content": "Hello from vLLM"
            }
          }
        ]
      }
      """;

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_valid_configuration() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());

      assertThat(client.getProviderName()).isEqualTo("vllm");
      assertThat(client.getDefaultModel()).isEqualTo("meta-llama/Llama-2-7b-chat-hf");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      assertThatThrownBy(() -> new VllmLlmClient(null, FixedVllmConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("vLLM ObjectMapper must not be null");
    }

    @Test
    void should_throw_when_configuration_null() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new VllmLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("LLM client configuration must not be null");
    }

    @Test
    void should_throw_when_url_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedVllmConfiguration.builder()
          .url("  ")
          .build();

      assertThatThrownBy(() -> new VllmLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("vLLM URL must be provided");
    }

    @Test
    void should_throw_when_default_model_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedVllmConfiguration.builder()
          .defaultModel("")
          .build();

      assertThatThrownBy(() -> new VllmLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void should_throw_when_choices_empty() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      String json = """
          { "choices": [] }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vLLM response choices are empty");
    }

    @Test
    void should_throw_when_choices_null() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      String json = """
          { "choices": null }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vLLM response choices are empty");
    }

    @Test
    void should_throw_when_root_deserializes_to_null() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("null"))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vLLM response choices are empty");
    }

    @Test
    void should_throw_on_malformed_json() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{ not-json"))
          .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void should_throw_when_first_choice_content_blank() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      String json = """
          {
            "choices": [
              { "message": { "content": "   " } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("first choice content is blank");
    }

    @Test
    void should_extract_first_choice_preserving_inner_whitespace() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      String json = """
          {
            "choices": [
              { "message": { "content": "  padded  " } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("padded");
    }

    @Test
    void should_strip_markdown_code_fence_from_content() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      String json = """
          {
            "choices": [
              {
                "message": {
                  "content": "```java\\nreturn 1;\\n```"
                }
              }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("return 1;");
    }

    @Test
    void should_extract_from_valid_chat_completions_payload() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());

      assertThat(client.validateAndExtractResponse(VALID_CHAT_COMPLETIONS_JSON))
          .isEqualTo("Hello from vLLM");
    }

    @Test
    void should_round_trip_sample_payload_via_object_mapper() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      ObjectNode root = mapper.createObjectNode();
      ArrayNode choices = root.putArray("choices");
      ObjectNode choice = choices.addObject();
      ObjectNode message = choice.putObject("message");
      message.put("content", "built");
      String json = mapper.writeValueAsString(root);

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("built");
    }

    @Test
    void should_ignore_unknown_json_properties_on_response() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      String json = """
          {
            "id": "chatcmpl-test",
            "object": "chat.completion",
            "choices": [
              { "message": { "content": "ok", "role": "assistant" }, "finish_reason": "stop" }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("ok");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void should_target_configured_url() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedVllmConfiguration.builder()
          .url("http://localhost:8000/v1/chat/completions")
          .build();
      VllmLlmClient client = new VllmLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system prompt", "user input");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri()).isEqualTo(
          URI.create("http://localhost:8000/v1/chat/completions"));
      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    void should_send_json_content_type_header() {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    void should_serialize_chat_completions_request_body() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedVllmConfiguration.builder()
          .defaultModel("default-model")
          .build();
      VllmLlmClient client = new VllmLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "Be brief.", "Ping");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));
      var expected = new VllmRequest(
          "default-model",
          List.of(
              new VllmMessage(InputRole.SYSTEM, "Be brief."),
              new VllmMessage(InputRole.USER, "Ping")),
          false);

      assertThat(mapper.readTree(body)).isEqualTo(mapper.valueToTree(expected));
    }

    @Test
    void should_use_explicit_model_from_execution_request() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedVllmConfiguration.builder()
          .defaultModel("default-model")
          .build();
      VllmLlmClient client = new VllmLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("vllm", "explicit-model", "S", "U");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(mapper.readTree(body).path("model").asText()).isEqualTo("explicit-model");
    }

    @Test
    void should_set_stream_false() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "a", "b");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(mapper.readTree(body).path("stream").asBoolean()).isFalse();
    }
  }

  @Nested
  class PromptCacheConformanceTests {

    @Test
    void shouldProduceIdenticalRequestBodyRegardlessOfBoundaries() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      LlmExecutionRequest withoutBoundaries =
          new LlmExecutionRequest("vllm", "model-a", "system", "user");
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(50, 100, null);
      LlmExecutionRequest withBoundaries = new LlmExecutionRequest(
          "vllm", "model-a", "system", "user", null, boundaries);

      String withoutBody = collectUtf8RequestBody(client.buildHttpRequest(withoutBoundaries));
      String withBody = collectUtf8RequestBody(client.buildHttpRequest(withBoundaries));

      assertThat(withBody).isEqualTo(withoutBody);
    }

    @Test
    void shouldOmitExplicitCacheMarkersWhenBoundariesPresent() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      VllmLlmClient client = new VllmLlmClient(mapper, FixedVllmConfiguration.defaults());
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(100, 200, null);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "vllm", "model-a", "system", "user", null, boundaries);

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(body).doesNotContain("cache_control");
      assertThat(body).doesNotContain("cachePoint");
    }
  }

  private static String collectUtf8RequestBody(HttpRequest request) throws Exception {
    assertThat(request.bodyPublisher()).isPresent();
    HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
    var out = new ByteArrayOutputStream();
    var latch = new CountDownLatch(1);
    publisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription s) {
        s.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(ByteBuffer b) {
        byte[] chunk = new byte[b.remaining()];
        b.get(chunk);
        out.writeBytes(chunk);
      }

      @Override
      public void onError(Throwable t) {
        latch.countDown();
      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });
    assertThat(latch.await(5, TimeUnit.SECONDS)).as("body publisher should complete").isTrue();
    return out.toString(StandardCharsets.UTF_8);
  }
}
