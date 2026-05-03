package com.agentforge4j.llm.mistral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.openai.dto.InputRole;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionMessageDto;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MistralLlmClientTest {

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_valid_configuration() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());

      assertThat(client.getProviderName()).isEqualTo("mistral");
      assertThat(client.getDefaultModel()).isEqualTo("mistral-small-latest");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      assertThatThrownBy(() -> new MistralLlmClient(null, FixedMistralConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void should_throw_when_configuration_null() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new MistralLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_base_url_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .baseUrl("  ")
          .build();

      assertThatThrownBy(() -> new MistralLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("baseUrl");
    }

    @Test
    void should_throw_when_api_key_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .apiKey(" ")
          .build();

      assertThatThrownBy(() -> new MistralLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiKey");
    }

    @Test
    void should_throw_when_default_model_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .defaultModel("")
          .build();

      assertThatThrownBy(() -> new MistralLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void should_throw_when_json_blank() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse(""))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void should_throw_when_error_object_has_message() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          {
            "error": { "message": "Invalid model", "code": "400", "type": "invalid_request" },
            "choices": []
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("mistral error")
          .hasMessageContaining("Invalid model");
    }

    @Test
    void should_ignore_error_object_when_message_is_blank() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          {
            "error": { "message": "   ", "code": null, "type": null },
            "choices": [
              { "message": { "role": "assistant", "content": "still ok" } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("still ok");
    }

    @Test
    void should_throw_when_choices_empty() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          { "error": null, "choices": [] }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("choices are empty");
    }

    @Test
    void should_throw_when_choices_null() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          { "error": null, "choices": null }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("choices are empty");
    }

    @Test
    void should_throw_when_choices_missing() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          { "error": null }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("choices are empty");
    }

    @Test
    void should_throw_when_first_choice_is_null() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          { "error": null, "choices": [null] }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("first choice is null");
    }

    @Test
    void should_throw_when_message_is_null() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          {
            "error": null,
            "choices": [ { "message": null } ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("content is blank");
    }

    @Test
    void should_throw_when_content_is_null() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          {
            "error": null,
            "choices": [
              { "message": { "role": "assistant", "content": null } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("content is blank");
    }

    @Test
    void should_throw_when_content_blank() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          {
            "error": null,
            "choices": [
              { "message": { "role": "assistant", "content": "   " } }
            ]
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("content is blank");
    }

    @Test
    void should_extract_content_and_strip_surrounding_whitespace() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      String json = """
          {
            "error": null,
            "choices": [
              { "message": { "role": "assistant", "content": "  trimmed  " } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("trimmed");
    }

    @Test
    void should_propagate_jackson_failure_for_unknown_json_fields() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{\"unexpected\":true}"))
          .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void should_strip_markdown_code_fence_from_content() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      ObjectNode root = mapper.createObjectNode();
      root.putNull("error");
      ArrayNode choices = root.putArray("choices");
      ObjectNode choice = choices.addObject();
      ObjectNode message = choice.putObject("message");
      message.put("role", "assistant");
      message.put("content", "```json\n{\"a\":1}\n```");
      String json = mapper.writeValueAsString(root);

      assertThat(client.validateAndExtractResponse(json)).isEqualTo("{\"a\":1}");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void should_target_v1_chat_completions_under_base_url() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .baseUrl("https://api.mistral.ai/")
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("mistral", "system prompt", "user input");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString()).isEqualTo("https://api.mistral.ai/v1/chat/completions");
      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    void should_normalize_base_url_without_trailing_slash() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .baseUrl("http://127.0.0.1:9")
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("mistral", "s", "u");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString()).isEqualTo("http://127.0.0.1:9/v1/chat/completions");
    }

    @Test
    void should_apply_configuration_request_timeout_to_http_request() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .requestTimeout(Duration.ofSeconds(88))
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("mistral", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(88));
    }

    @Test
    void should_send_bearer_token_and_json_content_type_headers() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .apiKey("sk-mistral-secret")
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("mistral", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type")).contains("application/json");
      assertThat(httpRequest.headers().firstValue("Authorization")).contains("Bearer sk-mistral-secret");
    }

    @Test
    void should_serialize_openai_style_body_with_default_model_when_request_model_null()
        throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedMistralConfiguration.builder()
          .defaultModel("mistral-large-latest")
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("mistral", "Be brief.", "Ping");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));
      var expected = new OpenAiChatCompletionRequestDto(
          "mistral-large-latest",
          List.of(
              new OpenAiChatCompletionMessageDto(InputRole.SYSTEM, "Be brief."),
              new OpenAiChatCompletionMessageDto(InputRole.USER, "Ping")));

      assertThat(mapper.readTree(body)).isEqualTo(mapper.valueToTree(expected));
    }

    @Test
    void should_use_request_model_when_provided() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClient client = new MistralLlmClient(mapper, FixedMistralConfiguration.defaults());
      LlmExecutionRequest request =
          new LlmExecutionRequest("mistral", "custom-model", "sys", "usr");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(mapper.readTree(body).path("model").asText()).isEqualTo("custom-model");
    }
  }

  @Nested
  class MistralLlmClientFactoryTests {

    @Test
    void should_create_client_for_mistral_configuration() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClientFactory factory = new MistralLlmClientFactory();

      LlmClient client = factory.create(mapper, FixedMistralConfiguration.defaults());

      assertThat(client).isInstanceOf(MistralLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("mistral");
      assertThat(factory.getProviderName()).isEqualTo("mistral");
    }

    @Test
    void should_throw_when_configuration_is_not_mistral() {
      ObjectMapper mapper = new ObjectMapper();
      MistralLlmClientFactory factory = new MistralLlmClientFactory();
      LlmClientConfiguration other = new LlmClientConfiguration() {
        @Override
        public String getProviderName() {
          return "openai";
        }

        @Override
        public String getDefaultModel() {
          return "gpt-4";
        }

        @Override
        public Duration getConnectTimeout() {
          return Duration.ofSeconds(1);
        }
      };

      assertThatThrownBy(() -> factory.create(mapper, other))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("MistralLlmClientFactory requires MistralConfiguration");
    }
  }

  @Nested
  class ExecuteRequestValidationTests {

    @Test
    void should_throw_when_provider_name_mismatched() {
      MistralLlmClient client =
          new MistralLlmClient(new ObjectMapper(), FixedMistralConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("does not match");
    }

    @Test
    void should_throw_when_execute_request_null() {
      MistralLlmClient client =
          new MistralLlmClient(new ObjectMapper(), FixedMistralConfiguration.defaults());

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
