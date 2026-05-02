package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.openai.dto.InputItem;
import com.agentforge4j.llm.openai.dto.InputRole;
import com.agentforge4j.llm.openai.dto.OpenAiResponsesRequestDto;
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

class OpenAiLlmClientTest {

  private static final String VALID_RESPONSES_JSON = """
      {
        "error": null,
        "output": [
          {
            "type": "message",
            "content": [
              {
                "type": "output_text",
                "text": "Hello from OpenAI"
              }
            ]
          }
        ]
      }
      """;

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_valid_configuration() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());

      assertThat(client.getProviderName()).isEqualTo("openai");
      assertThat(client.getDefaultModel()).isEqualTo("gpt-4");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      assertThatThrownBy(() -> new OpenAiLlmClient(null, FixedOpenAiConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OpenAi ObjectMapper must not be null");
    }

    @Test
    void should_throw_when_configuration_null() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new OpenAiLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("LLM client configuration must not be null");
    }

    @Test
    void should_throw_when_api_key_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .apiKey(" ")
          .build();

      assertThatThrownBy(() -> new OpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiKey");
    }

    @Test
    void should_throw_when_url_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .url("  ")
          .build();

      assertThatThrownBy(() -> new OpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("URL");
    }

    @Test
    void should_throw_when_default_model_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .defaultModel("")
          .build();

      assertThatThrownBy(() -> new OpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void should_throw_when_json_blank() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse(""))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void should_throw_when_error_object_has_message() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          {
            "error": { "message": "Rate limit exceeded", "code": "429", "type": "rate_limit" },
            "output": []
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("OpenAI error")
          .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void should_ignore_error_object_when_message_is_blank() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          {
            "error": { "message": "   ", "code": null, "type": null },
            "output": [
              {
                "type": "message",
                "content": [ { "type": "output_text", "text": "still ok" } ]
              }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("still ok");
    }

    @Test
    void should_throw_when_output_empty() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          { "error": null, "output": [] }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("missing or empty output");
    }

    @Test
    void should_throw_when_output_null() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          { "error": null, "output": null }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("missing or empty output");
    }

    @Test
    void should_throw_when_root_deserializes_to_null() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("null"))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("deserialized to null");
    }

    @Test
    void should_throw_on_malformed_json() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{ not-json"))
          .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void should_throw_when_output_text_is_blank() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          {
            "error": null,
            "output": [
              {
                "type": "message",
                "content": [ { "type": "output_text", "text": "   " } ]
              }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("missing assistant output_text");
    }

    @Test
    void should_extract_first_output_text_preserving_inner_whitespace() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          {
            "error": null,
            "output": [
              {
                "type": "MESSAGE",
                "content": [ { "type": "OUTPUT_TEXT", "text": "  inner  " } ]
              }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("  inner  ");
    }

    @Test
    void should_skip_non_message_output_items() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          {
            "error": null,
            "output": [
              { "type": "reasoning", "content": [] },
              {
                "type": "message",
                "content": [ { "type": "output_text", "text": "picked" } ]
              }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("picked");
    }

    @Test
    void should_skip_non_output_text_content_blocks() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      String json = """
          {
            "error": null,
            "output": [
              {
                "type": "message",
                "content": [
                  { "type": "input_text", "text": "skip me" },
                  { "type": "output_text", "text": "use me" }
                ]
              }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("use me");
    }

    @Test
    void should_extract_from_valid_responses_payload() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());

      assertThat(client.validateAndExtractResponse(VALID_RESPONSES_JSON))
          .isEqualTo("Hello from OpenAI");
    }

    @Test
    void should_round_trip_sample_payload_via_object_mapper() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, FixedOpenAiConfiguration.defaults());
      ObjectNode root = mapper.createObjectNode();
      root.putNull("error");
      ArrayNode output = root.putArray("output");
      ObjectNode message = output.addObject();
      message.put("type", "message");
      ArrayNode content = message.putArray("content");
      ObjectNode textBlock = content.addObject();
      textBlock.put("type", "output_text");
      textBlock.put("text", "built");
      String json = mapper.writeValueAsString(root);

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("built");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void should_target_configured_url() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .url("https://api.example.com/v1/responses")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system prompt", "user input");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri()).isEqualTo(URI.create("https://api.example.com/v1/responses"));
      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    void should_send_bearer_token_and_json_content_type_headers() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .apiKey("secret-key-123")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type")).contains("application/json");
      assertThat(httpRequest.headers().firstValue("Authorization")).contains("Bearer secret-key-123");
    }

    @Test
    void should_serialize_responses_request_body() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .defaultModel("ada-model")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "Be brief.", "Ping");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));
      var expected = new OpenAiResponsesRequestDto(
          "ada-model",
          List.of(
              new InputItem(InputRole.SYSTEM, "Be brief."),
              new InputItem(InputRole.USER, "Ping")));

      assertThat(mapper.readTree(body)).isEqualTo(mapper.valueToTree(expected));
    }

    @Test
    void should_use_explicit_model_from_execution_request() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiConfiguration.builder()
          .defaultModel("default-model")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", "explicit-model", "S", "U");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(mapper.readTree(body).path("model").asText()).isEqualTo("explicit-model");
    }
  }

  private static String collectUtf8RequestBody(HttpRequest request) throws Exception {
    assertThat(request.bodyPublisher()).isPresent();
    HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
    var out = new ByteArrayOutputStream();
    var latch = new CountDownLatch(1);
    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
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
