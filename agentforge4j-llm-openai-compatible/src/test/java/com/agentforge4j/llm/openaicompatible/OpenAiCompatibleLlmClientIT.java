package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests: real {@link java.net.http.HttpClient} wiring against a local loopback server.
 * No mocks, no API keys, no external network beyond localhost.
 */
class OpenAiCompatibleLlmClientIT {

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

  @Test
  void should_return_assistant_text_on_successful_http_response() throws Exception {
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from compatible");
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("OPENAI-COMPATIBLE", null, "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from compatible");
    }
  }

  @Test
  void should_throw_when_provider_name_mismatched() {
    OpenAiCompatibleLlmClient client =
        new OpenAiCompatibleLlmClient(new ObjectMapper(),
            FixedOpenAiCompatibleConfiguration.defaults());
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("openai", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(503, "upstream")) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai-compatible")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, "{ not-json")) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai-compatible request failed");
    }
  }

  @Test
  void should_throw_when_success_body_is_empty_string() throws Exception {
    try (OpenAiCompatibleLoopbackHttpServer http = new OpenAiCompatibleLoopbackHttpServer(200,
        "")) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai-compatible request failed");
    }
  }

  @Test
  void should_throw_when_api_error_message_present() throws Exception {
    String errorJson = """
        {"error":{"message":"bad request"},"output":[]}
        """;
    try (OpenAiCompatibleLoopbackHttpServer http = new OpenAiCompatibleLoopbackHttpServer(200,
        errorJson)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai-compatible error")
          .hasMessageContaining("bad request");
    }
  }

  @Test
  void should_throw_when_output_empty() throws Exception {
    String json = """
        { "error": null, "output": [] }
        """;
    try (OpenAiCompatibleLoopbackHttpServer http = new OpenAiCompatibleLoopbackHttpServer(200,
        json)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("missing or empty output");
    }
  }

  @Test
  void should_throw_when_assistant_output_text_missing() throws Exception {
    String json = """
        {
          "error": null,
          "output": [
            {
              "type": "message",
              "content": [ { "type": "text", "text": "ignored" } ]
            }
          ]
        }
        """;
    try (OpenAiCompatibleLoopbackHttpServer http = new OpenAiCompatibleLoopbackHttpServer(200,
        json)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("missing assistant output_text");
    }
  }

  @Test
  void should_post_json_body_and_default_responses_path() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON, captured)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .defaultModel("capture-model")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "S", "U");

      client.execute(request);

      assertThat(captured.get())
          .contains("\"model\":\"capture-model\"")
          .contains("\"role\":\"system\"")
          .contains("\"content\":\"S\"")
          .contains("\"role\":\"user\"")
          .contains("\"content\":\"U\"");
    }
  }

  @Test
  void should_post_to_custom_responses_path() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON, null, full)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .responsesPath("custom/responses")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "sys", "usr");

      client.execute(request);

      String raw = full.get();
      assertThat(raw).isNotNull();
      assertThat(raw.lines().findFirst().orElse(""))
          .startsWith("POST /custom/responses ");
    }
  }

  @Test
  void should_strip_trailing_slash_from_base_url() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON, null, full)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri() + "/")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "a", "b");

      client.execute(request);

      assertThat(full.get()).contains("POST /v1/responses ");
    }
  }

  @Test
  void should_send_configured_auth_header_and_prefix() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON, null, full)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .apiKey("raw-secret")
          .authHeaderName("X-Api-Key")
          .authHeaderPrefix("")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "sys", "usr");

      client.execute(request);

      String headers = full.get().substring(0, full.get().indexOf("\r\n\r\n"));
      assertThat(headers.toLowerCase(Locale.ROOT))
          .contains("x-api-key: raw-secret")
          .contains("content-type: application/json");
    }
  }

  @Test
  void should_use_explicit_model_from_execution_request() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON, captured)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .defaultModel("default-model")
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai-compatible", "explicit-model", "S", "U");

      client.execute(request);

      assertThat(captured.get()).contains("\"model\":\"explicit-model\"");
    }
  }

  @Test
  void should_wrap_connection_failure() {
    var config = FixedOpenAiCompatibleConfiguration.builder()
        .baseUrl("http://127.0.0.1:1")
        .build();
    OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("openai-compatible request failed");
  }

  @Test
  void should_fail_when_http_request_deadline_exceeded() throws Exception {
    AtomicReference<String> ignored = new AtomicReference<>();
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON, ignored, null, 2_000L)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .requestTimeout(Duration.ofMillis(200))
          .build();
      OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai-compatible request failed");
    }
  }

  @Test
  void factory_created_client_should_execute_against_loopback() throws Exception {
    try (OpenAiCompatibleLoopbackHttpServer http =
        new OpenAiCompatibleLoopbackHttpServer(200, VALID_RESPONSES_JSON)) {
      var config = FixedOpenAiCompatibleConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      var client = new OpenAiCompatibleLlmClientFactory().create(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai-compatible", "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from compatible");
    }
  }

  @Test
  void service_loader_should_provide_openai_compatible_factory() {
    var found = ServiceLoader.load(LlmClientFactory.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(f -> "openai-compatible".equals(f.getProviderName()))
        .findFirst();

    assertThat(found).isPresent();
    assertThat(found.orElseThrow()).isInstanceOf(OpenAiCompatibleLlmClientFactory.class);
  }

  @Test
  void meta_inf_services_should_register_factory_implementation() throws Exception {
    String name = "META-INF/services/com.agentforge4j.llm.LlmClientFactory";
    try (var in = OpenAiCompatibleLlmClientFactory.class.getModule().getResourceAsStream(name)) {
      assertThat(in).as("SPI descriptor present in module").isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
      assertThat(text).isEqualTo(OpenAiCompatibleLlmClientFactory.class.getName());
    }
  }
}
