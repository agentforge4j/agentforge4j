package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.gemini.dto.GeminiContent;
import com.agentforge4j.llm.gemini.dto.GeminiPart;
import com.agentforge4j.llm.gemini.dto.InputRole;
import com.agentforge4j.llm.gemini.dto.GeminiRequest;
import com.agentforge4j.llm.gemini.dto.GeminiSystemInstruction;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

class GeminiLlmClientTest {

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_valid_configuration() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());

      assertThat(client.getProviderName()).isEqualTo("gemini");
      assertThat(client.getDefaultModel()).isEqualTo("gemini-1.5-flash");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      assertThatThrownBy(() -> new GeminiLlmClient(null, FixedGeminiConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void should_throw_when_configuration_null() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new GeminiLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_api_key_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedGeminiConfiguration.builder()
          .apiKey("  ")
          .build();

      assertThatThrownBy(() -> new GeminiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiKey");
    }

    @Test
    void should_throw_when_base_url_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedGeminiConfiguration.builder()
          .baseUrl("")
          .build();

      assertThatThrownBy(() -> new GeminiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("baseUrl");
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void should_throw_when_json_blank() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse(""))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void should_throw_when_json_whitespace_only() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("   \n\t  "))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void should_throw_when_provider_error_has_message() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "error": { "code": 400, "message": "API key not valid", "status": "INVALID_ARGUMENT" }
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("gemini error")
          .hasMessageContaining("API key not valid");
    }

    @Test
    void should_ignore_error_when_message_is_blank_and_candidates_present() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "error": { "message": "   ", "code": null, "status": null },
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [ { "text": "ok" } ] } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("ok");
    }

    @Test
    void should_throw_when_candidates_empty() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          { "candidates": [] }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no candidates");
    }

    @Test
    void should_throw_when_finish_reason_safety() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "SAFETY", "content": { "parts": [ { "text": "x" } ] } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("safety");
    }

    @Test
    void should_throw_when_finish_reason_safety_mixed_case() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "Safety", "content": { "parts": [ { "text": "x" } ] } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("safety");
    }

    @Test
    void should_throw_when_candidates_null() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = "{ \"candidates\": null }";

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no candidates");
    }

    @Test
    void should_throw_when_candidate_content_null() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "STOP", "content": null }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("content is null");
    }

    @Test
    void should_throw_when_candidate_parts_empty() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [] } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("parts are empty");
    }

    @Test
    void should_throw_when_first_part_text_null() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [ { "text": null } ] } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("text is blank");
    }

    @Test
    void should_succeed_when_error_present_but_message_null_and_candidates_valid() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "error": { "message": null, "code": 400 },
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [ { "text": "ok" } ] } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("ok");
    }

    @Test
    void should_extract_text_and_strip_whitespace() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [ { "text": "  hello  " } ] } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("hello");
    }

    @Test
    void should_ignore_unknown_top_level_fields() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "promptFeedback": { "ignored": true },
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [ { "text": "hi" } ] } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("hi");
    }

    @Test
    void should_throw_on_malformed_json() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("not-json"))
          .isInstanceOf(JsonParseException.class);
    }

    @Test
    void should_throw_when_response_text_blank() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      String json = """
          {
            "candidates": [
              { "finishReason": "STOP", "content": { "parts": [ { "text": "   " } ] } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("text is blank");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void should_target_generate_content_with_model_and_encoded_api_key() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedGeminiConfiguration.builder()
          .baseUrl("https://generativelanguage.googleapis.com/")
          .apiKey("k+1")
          .defaultModel("gemini-pro")
          .build();
      GeminiLlmClient client = new GeminiLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", "custom-model", "sys", "user");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString())
          .isEqualTo(
              "https://generativelanguage.googleapis.com/v1beta/models/custom-model:generateContent?key=k%2B1");
      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(30));
      assertThat(httpRequest.headers().firstValue("Content-Type"))
          .contains("application/json");
    }

    @Test
    void should_use_default_model_when_request_model_blank() {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("gemini", "s", "u");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString())
          .contains("/models/gemini-1.5-flash:generateContent");
    }

    @Test
    void should_serialize_request_body_with_system_instruction_and_user_content() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      GeminiLlmClient client = new GeminiLlmClient(mapper, FixedGeminiConfiguration.defaults());
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", "m", "You are helpful.", "Hello.");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));
      var expected = new GeminiRequest(
          new GeminiSystemInstruction(List.of(new GeminiPart("You are helpful."))),
          List.of(new GeminiContent(InputRole.USER, List.of(new GeminiPart("Hello.")))));

      assertThat(mapper.readTree(body)).isEqualTo(mapper.valueToTree(expected));
    }
  }

  @Nested
  class ExecuteRequestValidationTests {

    @Test
    void should_throw_when_provider_name_mismatched() {
      GeminiLlmClient client =
          new GeminiLlmClient(new ObjectMapper(), FixedGeminiConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("does not match");
    }

    @Test
    void should_throw_when_execute_request_null() {
      GeminiLlmClient client =
          new GeminiLlmClient(new ObjectMapper(), FixedGeminiConfiguration.defaults());

      assertThatThrownBy(() -> client.execute(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Request must not be null");
    }
  }

  private static String collectUtf8RequestBody(HttpRequest request) throws Exception {
    assertThat(request.bodyPublisher()).isPresent();
    HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
    var out = new ByteArrayOutputStream();
    var latch = new CountDownLatch(1);
    publisher.subscribe(new Flow.Subscriber<>() {
      private Flow.Subscription subscription;

      @Override
      public void onSubscribe(Flow.Subscription s) {
        subscription = s;
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
