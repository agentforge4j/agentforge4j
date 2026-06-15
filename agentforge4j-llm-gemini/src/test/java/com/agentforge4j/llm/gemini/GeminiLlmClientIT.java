package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
 * No mocks, no API keys to external providers, no network beyond localhost.
 */
class GeminiLlmClientIT {

  private static final String VALID_GENERATE_CONTENT_JSON = """
      {
        "candidates": [
          { "finishReason": "STOP", "content": { "parts": [ { "text": "Hello from gemini IT" } ] } }
        ]
      }
      """;

  @Test
  void should_return_model_text_on_successful_http_response() throws Exception {
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200,
        VALID_GENERATE_CONTENT_JSON)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system prompt", "user input", null, null, null);

      var response = client.execute(request);
      assertThat(response.text()).isEqualTo("Hello from gemini IT");
      assertThat(response.tokenUsage()).isNull();
    }
  }

  @Test
  void should_return_token_usage_when_usage_metadata_present() throws Exception {
    String body = readFixture("generate-with-usage.json");
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200, body)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system prompt", "user input", null, null, null);

      LlmExecutionResponse response = client.execute(request);
      assertThat(response.text()).isEqualTo("hello");
      assertThat(response.tokenUsage()).isNotNull();
      assertThat(response.tokenUsage().inputTokens()).isEqualTo(60);
      assertThat(response.tokenUsage().outputTokens()).isEqualTo(25);
    }
  }

  private static String readFixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = GeminiLlmClientIT.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200,
        VALID_GENERATE_CONTENT_JSON)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("GEMINI", null, "system", "user", null, null, null);

      assertThat(client.execute(request).text()).isEqualTo("Hello from gemini IT");
    }
  }

  @Test
  void should_throw_when_provider_name_mismatched() {
    GeminiLlmClient client =
        new GeminiLlmClient(new ObjectMapper(), FixedGeminiConfiguration.defaults());
    LlmExecutionRequest request =
        new LlmExecutionRequest("openai", null, "system", "user", null, null, null);

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(503, "upstream")) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("gemini")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200, "{ not-json")) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("gemini request failed");
    }
  }

  @Test
  void should_throw_when_success_body_is_empty_string() throws Exception {
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200, "")) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageMatching(
              "(?s).*(gemini request failed|LLM client json must not be null).*");
    }
  }

  @Test
  void should_throw_when_provider_error_message_present() throws Exception {
    String errorJson = """
        {"error":{"message":"bad request"},"candidates":[]}
        """;
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200, errorJson)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("gemini error")
          .hasMessageContaining("bad request");
    }
  }

  @Test
  void should_throw_when_candidates_empty() throws Exception {
    String json = "{ \"candidates\": [] }";
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200, json)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no candidates");
    }
  }

  @Test
  void should_post_generate_content_path_with_api_key_header_not_query() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (GeminiLoopbackHttpServer http =
        new GeminiLoopbackHttpServer(200, VALID_GENERATE_CONTENT_JSON, null, full)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .apiKey("loopback-key")
          .defaultModel("gemini-test-model")
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "S", "U", null, null, null);

      client.execute(request);

      String firstLine = full.get().lines().findFirst().orElse("");
      assertThat(firstLine)
          .startsWith("POST ")
          .contains("/v1beta/models/gemini-test-model:generateContent")
          .doesNotContain("key=");
      assertThat(full.get().lines()
          .anyMatch(line -> line.toLowerCase().startsWith("x-goog-api-key:")
              && line.contains("loopback-key")))
          .as("x-goog-api-key header present")
          .isTrue();
    }
  }

  @Test
  void should_post_json_body_with_system_instruction_and_user_role() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    try (GeminiLoopbackHttpServer http =
        new GeminiLoopbackHttpServer(200, VALID_GENERATE_CONTENT_JSON, body)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", "m", "You are helpful.", "Hello.", null, null, null);

      client.execute(request);

      assertThat(body.get())
          .contains("\"systemInstruction\"")
          .contains("\"You are helpful.\"")
          .contains("\"role\":\"user\"")
          .contains("\"Hello.\"");
    }
  }

  @Test
  void should_strip_trailing_slash_from_base_url() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (GeminiLoopbackHttpServer http =
        new GeminiLoopbackHttpServer(200, VALID_GENERATE_CONTENT_JSON, null, full)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri() + "/")
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "a", "b", null, null, null);

      client.execute(request);

      assertThat(full.get()).contains("POST /v1beta/models/");
    }
  }

  @Test
  void should_send_content_type_json_header() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (GeminiLoopbackHttpServer http =
        new GeminiLoopbackHttpServer(200, VALID_GENERATE_CONTENT_JSON, null, full)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "sys", "usr", null, null, null);

      client.execute(request);

      String headers = full.get().substring(0, full.get().indexOf("\r\n\r\n"));
      assertThat(headers.toLowerCase(Locale.ROOT)).contains("content-type: application/json");
    }
  }

  @Test
  void should_use_explicit_model_from_execution_request() throws Exception {
    AtomicReference<String> full = new AtomicReference<>();
    try (GeminiLoopbackHttpServer http =
        new GeminiLoopbackHttpServer(200, VALID_GENERATE_CONTENT_JSON, null, full)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .defaultModel("default-model")
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", "explicit-model", "S", "U", null, null, null);

      client.execute(request);

      assertThat(full.get()).contains("/v1beta/models/explicit-model:generateContent");
    }
  }

  @Test
  void should_wrap_connection_failure() {
    var config = FixedGeminiConfiguration.builder()
        .baseUrl("http://127.0.0.1:1")
        .build();
    GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
    LlmExecutionRequest request =
        new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("gemini request failed");
  }

  @Test
  void should_fail_when_http_request_deadline_exceeded() throws Exception {
    AtomicReference<String> ignored = new AtomicReference<>();
    try (GeminiLoopbackHttpServer http =
        new GeminiLoopbackHttpServer(200, VALID_GENERATE_CONTENT_JSON, ignored, null, 2_000L)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .requestTimeout(Duration.ofMillis(200))
          .build();
      GeminiLlmClient client = new GeminiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("gemini request failed");
    }
  }

  @Test
  void factory_created_client_should_execute_against_loopback() throws Exception {
    try (GeminiLoopbackHttpServer http = new GeminiLoopbackHttpServer(200,
        VALID_GENERATE_CONTENT_JSON)) {
      var config = FixedGeminiConfiguration.builder()
          .baseUrl(http.baseUri().toString())
          .build();
      var client = new GeminiLlmClientFactory().create(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("gemini", null, "system", "user", null, null, null);

      assertThat(client.execute(request).text()).isEqualTo("Hello from gemini IT");
    }
  }

  @Test
  void service_loader_should_provide_gemini_factory() {
    var found = ServiceLoader.load(LlmClientFactory.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(f -> "gemini".equals(f.getProviderName()))
        .findFirst();

    assertThat(found).isPresent();
    assertThat(found.orElseThrow()).isInstanceOf(GeminiLlmClientFactory.class);
  }

  @Test
  void meta_inf_services_should_register_factory_implementation() throws Exception {
    String name = "META-INF/services/com.agentforge4j.llm.LlmClientFactory";
    try (var in = GeminiLlmClientFactory.class.getModule().getResourceAsStream(name)) {
      assertThat(in).as("SPI descriptor present in module").isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
      assertThat(text).isEqualTo(GeminiLlmClientFactory.class.getName());
    }
  }
}
