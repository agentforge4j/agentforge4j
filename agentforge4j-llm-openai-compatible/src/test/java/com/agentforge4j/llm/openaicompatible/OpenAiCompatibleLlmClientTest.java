package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.openaicompatible.dto.InputRole;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleInputItem;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleResponsesRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class OpenAiCompatibleLlmClientTest {

  private static final String VALID_RESPONSES_JSON = """
      {
        "error": null,
        "output": [
          {
            "type": "message",
            "content": [
              {
                "type": "output_text",
                "text": "Hello from compatible"
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
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());

      assertThat(client.getProviderName()).isEqualTo("openai-compatible");
      assertThat(client.getDefaultModel()).isEqualTo("mistral");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      assertThatThrownBy(
          () -> new OpenAiCompatibleLlmClient(null, FixedOpenAiCompatibleConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("openai-compatible ObjectMapper must not be null");
    }

    @Test
    void should_throw_when_configuration_null() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("LLM client configuration must not be null");
    }

    @Test
    void should_throw_when_api_key_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .apiKey(" ")
          .build();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiKey");
    }

    @Test
    void should_throw_when_base_url_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl("  ")
          .build();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("baseUrl");
    }

    @Test
    void should_throw_when_default_model_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .defaultModel("")
          .build();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_auth_header_name_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .authHeaderName(" ")
          .build();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("authHeaderName");
    }

    @Test
    void should_throw_when_auth_header_prefix_null() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .authHeaderPrefix(null)
          .build();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("authHeaderPrefix");
    }

    @Test
    void should_throw_when_responses_path_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .responsesPath("   ")
          .build();

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("responsesPath");
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void should_throw_when_error_object_has_message() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());
      String json = """
          {
            "error": { "message": "Rate limit exceeded", "code": "429", "type": "rate_limit" },
            "output": []
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai-compatible error")
          .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void should_ignore_error_object_when_message_is_blank() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());
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
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());
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
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());
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
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("null"))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("deserialized to null");
    }

    @Test
    void should_throw_on_malformed_json() {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{ not-json"))
          .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void should_extract_assistant_text_on_valid_json() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OpenAiCompatibleLlmClient client =
          new OpenAiCompatibleLlmClient(mapper, FixedOpenAiCompatibleConfiguration.defaults());

      assertThat(client.validateAndExtractResponse(VALID_RESPONSES_JSON)).isEqualTo(
          "Hello from compatible");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void should_target_resolved_responses_uri() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl("https://api.example.com")
          .responsesPath("/v1/responses")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system prompt", "user input");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri()).isEqualTo(URI.create("https://api.example.com/v1/responses"));
      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    void should_send_configured_auth_headers_and_json_content_type() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .apiKey("secret-key-123")
          .authHeaderName("Authorization")
          .authHeaderPrefix("Bearer ")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type")).contains("application/json");
      assertThat(httpRequest.headers().firstValue("Authorization")).contains(
          "Bearer secret-key-123");
    }

    @Test
    void should_serialize_responses_request_body() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .defaultModel("ada-model")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "Be brief.", "Ping");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));
      var expected = new OpenAiCompatibleResponsesRequest(
          "ada-model",
          List.of(
              new OpenAiCompatibleInputItem(InputRole.SYSTEM, "Be brief."),
              new OpenAiCompatibleInputItem(InputRole.USER, "Ping")),
          request.maxOutputTokens()
      );

      assertThat(mapper.readTree(body)).isEqualTo(mapper.valueToTree(expected));
    }

    @Test
    void should_use_explicit_model_from_execution_request() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .defaultModel("default-model")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai-compatible", "explicit-model", "S", "U");

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
