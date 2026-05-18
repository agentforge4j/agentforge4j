package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests: real {@link java.net.http.HttpClient} wiring against a local loopback server.
 * No mocks, no API keys, no external network beyond localhost.
 */
class AzureOpenAiLlmClientIT {

  private static final String VALID_CHAT_COMPLETION_JSON = """
      {
        "error": null,
        "choices": [
          {
            "message": {
              "role": "assistant",
              "content": "Hello from Azure"
            }
          }
        ]
      }
      """;

  @Test
  void should_return_assistant_content_on_successful_http_response() throws Exception {
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(200,
        VALID_CHAT_COMPLETION_JSON)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .deploymentName("gpt-4-deployment")
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from Azure");
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(200,
        VALID_CHAT_COMPLETION_JSON)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("AZURE-OPENAI", null, "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from Azure");
    }
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(503, "upstream")) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("azure-openai")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream");
    }
  }

  @Test
  void should_include_status_and_body_on_http_400() throws Exception {
    String errorJson = """
        {"error":{"message":"bad request"},"choices":[]}
        """;
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(400, errorJson)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("HTTP error")
          .hasMessageContaining("400")
          .hasMessageContaining("bad request");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(200,
        "{ not-json")) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("azure-openai request failed");
    }
  }

  @Test
  void should_throw_when_success_body_is_empty_string() throws Exception {
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(200, "")) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("LLM client json must not be blank");
    }
  }

  @Test
  void should_throw_when_success_body_is_valid_completion_json_but_semantically_empty()
      throws Exception {
    String emptyChoices = """
        {"error":null,"choices":[]}
        """;
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(200,
        emptyChoices)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("choices are empty");
    }
  }

  @Test
  void should_post_json_body_matching_chat_completions_contract() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (AzureOpenAiLoopbackHttpServer http =
        new AzureOpenAiLoopbackHttpServer(200, VALID_CHAT_COMPLETION_JSON, captured)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .deploymentName("capture-dep")
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "S", "U");

      client.execute(request);

      assertThat(captured.get())
          .contains("\"model\":\"capture-dep\"")
          .contains("\"role\":\"system\"")
          .contains("\"content\":\"S\"")
          .contains("\"role\":\"user\"")
          .contains("\"content\":\"U\"");
    }
  }

  @Test
  void should_wrap_connection_failure() {
    var config = FixedAzureOpenAiConfiguration.builder()
        .endpoint("http://127.0.0.1:1")
        .build();
    AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("azure-openai request failed");
  }

  @Test
  void should_fail_when_http_request_deadline_exceeded() throws Exception {
    AtomicReference<String> ignored = new AtomicReference<>();
    try (AzureOpenAiLoopbackHttpServer http =
        new AzureOpenAiLoopbackHttpServer(200, VALID_CHAT_COMPLETION_JSON, ignored, 2_000L)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .requestTimeout(Duration.ofMillis(200))
          .build();
      AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("azure-openai request failed");
    }
  }

  @Test
  void factory_created_client_should_execute_against_loopback() throws Exception {
    try (AzureOpenAiLoopbackHttpServer http = new AzureOpenAiLoopbackHttpServer(200,
        VALID_CHAT_COMPLETION_JSON)) {
      var config = FixedAzureOpenAiConfiguration.builder()
          .endpoint(http.baseEndpoint().toString())
          .build();
      var client = new AzureOpenAiLlmClientFactory().create(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("azure-openai", "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from Azure");
    }
  }
}
