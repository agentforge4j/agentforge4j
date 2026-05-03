package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
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
class VllmLlmClientIT {

  private static final String VALID_CHAT_COMPLETIONS_JSON = """
      {
        "choices": [
          {
            "message": {
              "content": "Hello from loopback"
            }
          }
        ]
      }
      """;

  @Test
  void should_return_assistant_text_on_successful_http_response() throws Exception {
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThat(client.execute(request)).isEqualTo("Hello from loopback");
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("VLLM", null, "system", "user");

      assertThat(client.execute(request)).isEqualTo("Hello from loopback");
    }
  }

  @Test
  void should_throw_when_provider_name_mismatched() {
    VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), FixedVllmConfiguration.defaults());
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("openai", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(503, "busy")) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vllm")
          .hasMessageContaining("503")
          .hasMessageContaining("busy");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, "{ not-json")) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vllm request failed");
    }
  }

  @Test
  void should_throw_when_success_body_is_empty_string() throws Exception {
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, "")) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vllm request failed");
    }
  }

  @Test
  void should_throw_when_choices_empty_in_success_response() throws Exception {
    String json = """
        { "choices": [] }
        """;
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, json)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vLLM response choices are empty");
    }
  }

  @Test
  void should_throw_when_first_choice_content_blank() throws Exception {
    String json = """
        {
          "choices": [
            { "message": { "content": "   " } }
          ]
        }
        """;
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, json)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("first choice content is blank");
    }
  }

  @Test
  void should_post_json_body_matching_chat_completions_contract() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (VllmLoopbackHttpServer http =
        new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON, captured)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .defaultModel("capture-model")
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "S", "U");

      client.execute(request);

      assertThat(captured.get())
          .contains("\"model\":\"capture-model\"")
          .contains("\"role\":\"system\"")
          .contains("\"content\":\"S\"")
          .contains("\"role\":\"user\"")
          .contains("\"content\":\"U\"")
          .contains("\"stream\":false");
    }
  }

  @Test
  void should_send_json_content_type_header() throws Exception {
    AtomicReference<String> fullRequest = new AtomicReference<>();
    try (VllmLoopbackHttpServer http =
        new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON, null, fullRequest)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "sys", "usr");

      client.execute(request);

      String raw = fullRequest.get();
      assertThat(raw).isNotNull();
      int headerEnd = raw.indexOf("\r\n\r\n");
      assertThat(headerEnd).isPositive();
      String headers = raw.substring(0, headerEnd);
      assertThat(headers.toLowerCase(Locale.ROOT))
          .contains("content-type: application/json");
    }
  }

  @Test
  void should_use_explicit_model_from_execution_request() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (VllmLoopbackHttpServer http =
        new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON, captured)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .defaultModel("default-model")
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("vllm", "explicit-model", "S", "U");

      client.execute(request);

      assertThat(captured.get()).contains("\"model\":\"explicit-model\"");
    }
  }

  @Test
  void should_wrap_connection_failure() {
    var config = FixedVllmConfiguration.builder()
        .url("http://127.0.0.1:1/v1/chat/completions")
        .build();
    VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("vllm request failed");
  }

  @Test
  void should_fail_when_http_request_deadline_exceeded() throws Exception {
    AtomicReference<String> ignored = new AtomicReference<>();
    try (VllmLoopbackHttpServer http =
        new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON, ignored, null, 2_000L)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .requestTimeout(Duration.ofMillis(200))
          .build();
      VllmLlmClient client = new VllmLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("vllm request failed");
    }
  }

  @Test
  void factory_created_client_should_execute_against_loopback() throws Exception {
    try (VllmLoopbackHttpServer http = new VllmLoopbackHttpServer(200, VALID_CHAT_COMPLETIONS_JSON)) {
      var config = FixedVllmConfiguration.builder()
          .url(http.baseUri().toString())
          .build();
      var client = new VllmLlmClientFactory().create(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("vllm", "system", "user");

      assertThat(client.execute(request)).isEqualTo("Hello from loopback");
    }
  }

  @Test
  void service_loader_should_provide_vllm_factory() {
    var found = ServiceLoader.load(LlmClientFactory.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(f -> "vllm".equals(f.getProviderName()))
        .findFirst();

    assertThat(found).isPresent();
    assertThat(found.orElseThrow()).isInstanceOf(VllmLlmClientFactory.class);
  }

  @Test
  void meta_inf_services_should_register_factory_implementation() throws Exception {
    String name = "META-INF/services/com.agentforge4j.llm.LlmClientFactory";
    try (var in = VllmLlmClientFactory.class.getModule().getResourceAsStream(name)) {
      assertThat(in).as("SPI descriptor present in module").isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
      assertThat(text).isEqualTo(VllmLlmClientFactory.class.getName());
    }
  }
}
