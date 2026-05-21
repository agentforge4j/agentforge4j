package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAiLlmClientTest {

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_valid_configuration() {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());

      assertThat(client.getProviderName()).isEqualTo("azure-openai");
      assertThat(client.getDefaultModel()).isEqualTo("gpt-4");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      assertThatThrownBy(
          () -> new AzureOpenAiLlmClient(null, FixedAzureOpenAiConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void should_throw_when_configuration_null() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new AzureOpenAiLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_endpoint_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint("  ")
          .build();

      assertThatThrownBy(() -> new AzureOpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("endpoint");
    }

    @Test
    void should_throw_when_api_version_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .apiVersion("")
          .build();

      assertThatThrownBy(() -> new AzureOpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiVersion");
    }

    @Test
    void should_throw_when_api_key_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .apiKey(" ")
          .build();

      assertThatThrownBy(() -> new AzureOpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiKey");
    }

    @Test
    void should_throw_when_deployment_name_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .deploymentName("")
          .build();

      assertThatThrownBy(() -> new AzureOpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("deploymentName");
    }

    @Test
    void should_throw_when_default_model_blank() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .defaultModel("")
          .build();

      assertThatThrownBy(() -> new AzureOpenAiLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void should_throw_when_json_blank() {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse(""))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void should_throw_when_error_object_has_message() {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
      String json = """
          {
            "error": { "message": "Deployment not found", "code": "404", "type": "invalid_request" },
            "choices": []
          }
          """;

      assertThatThrownBy(() -> client.validateAndExtractResponse(json))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("azure-openai error")
          .hasMessageContaining("Deployment not found");
    }

    @Test
    void should_ignore_error_object_when_message_is_blank() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
      String json = """
          {
            "error": { "message": "   ", "code": null, "type": null },
            "choices": [
              { "message": { "role": "assistant", "content": "still ok" } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json).text()).isEqualTo("still ok");
    }

    @Test
    void should_throw_when_choices_empty() {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
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
    void should_extract_content_and_strip_surrounding_whitespace() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
      String json = """
          {
            "error": null,
            "choices": [
              { "message": { "role": "assistant", "content": "  trimmed  " } }
            ]
          }
          """;

      assertThat(client.validateAndExtractResponse(json).text()).isEqualTo("trimmed");
    }

    @Test
    void should_propagate_jackson_failure_for_unknown_json_fields() {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());

      assertThatThrownBy(() -> client.validateAndExtractResponse("{\"unexpected\":true}"))
          .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void should_strip_markdown_code_fence_from_content() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
      ObjectNode root = mapper.createObjectNode();
      root.putNull("error");
      ArrayNode choices = root.putArray("choices");
      ObjectNode choice = choices.addObject();
      ObjectNode message = choice.putObject("message");
      message.put("role", "assistant");
      message.put("content", "```json\n{\"a\":1}\n```");
      String json = mapper.writeValueAsString(root);

      assertThat(client.validateAndExtractResponse(json).text()).isEqualTo("{\"a\":1}");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void should_target_deployments_chat_completions_with_encoded_api_version() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint("https://my.openai.azure.com/")
          .deploymentName("my-dep")
          .apiVersion("2024-02-15&x=y")
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system prompt", "user input");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString())
          .isEqualTo(
              "https://my.openai.azure.com/openai/deployments/my-dep/chat/completions?api-version=2024-02-15%26x%3Dy");
      assertThat(httpRequest.method()).isEqualTo("POST");
      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    void should_normalize_endpoint_without_trailing_slash() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint("https://eastus.api.cognitive.microsoft.com")
          .deploymentName("d1")
          .apiVersion("2023-05-15")
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "s", "u");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString())
          .isEqualTo(
              "https://eastus.api.cognitive.microsoft.com/openai/deployments/d1/chat/completions?api-version=2023-05-15");
    }

    @Test
    void should_apply_configuration_request_timeout_to_http_request() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .requestTimeout(Duration.ofSeconds(77))
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(77));
    }

    @Test
    void should_send_api_key_and_json_content_type_headers() {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .apiKey("secret-key-123")
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "sys", "usr");

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type")).contains("application/json");
      assertThat(httpRequest.headers().firstValue("api-key")).contains("secret-key-123");
    }

    @Test
    void should_serialize_chat_completion_body_using_deployment_as_model_field() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      var config = FixedAzureOpenAiConfiguration.builder()
          .deploymentName("ada-deployment")
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(mapper, config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "Be brief.", "Ping");

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));
      var root = mapper.createObjectNode();
      root.put("model", "ada-deployment");
      var messages = root.putArray("messages");
      var sys = messages.addObject();
      sys.put("role", "system");
      sys.put("content", "Be brief.");
      var usr = messages.addObject();
      usr.put("role", "user");
      usr.put("content", "Ping");

      assertThat(mapper.readTree(body)).isEqualTo(root);
    }
  }

  @Nested
  class ExecuteRequestValidationTests {

    @Test
    void should_throw_when_provider_name_mismatched() {
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(new ObjectMapper(), FixedAzureOpenAiConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("does not match");
    }

    @Test
    void should_throw_when_execute_request_null() {
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(new ObjectMapper(), FixedAzureOpenAiConfiguration.defaults());

      assertThatThrownBy(() -> client.execute(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Request must not be null");
    }
  }

  @Nested
  class PromptCacheConformanceTests {

    @Test
    void shouldProduceDeterministicRequestBodyForIdenticalInput() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "sys", "usr");

      String first = collectUtf8RequestBody(client.buildHttpRequest(request));
      String second = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldOmitExplicitCacheMarkersWhenBoundariesPresent() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      AzureOpenAiLlmClient client =
          new AzureOpenAiLlmClient(mapper, FixedAzureOpenAiConfiguration.defaults());
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(100, 200, null);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "azure-openai", "m", "system prompt", "user", null, boundaries);

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
    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
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
