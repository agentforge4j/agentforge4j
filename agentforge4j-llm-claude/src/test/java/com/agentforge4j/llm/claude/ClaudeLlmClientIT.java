package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for {@link ClaudeLlmClient} using a local loopback HTTP server (no mocks, no API
 * keys, no external network).
 */
class ClaudeLlmClientIT {

  private static final String VALID_MESSAGES_JSON = """
      {"content":[{"type":"text","text":"Hello from Claude"}]}
      """;

  /**
   * Minimal loopback HTTP/1.1 server so tests need no extra module dependencies.
   */
  static final class LoopbackHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread thread;
    private final int statusCode;
    private final byte[] responseBodyBytes;
    private final AtomicReference<String> capturedBodyUtf8;
    private final AtomicReference<String> capturedFullRequestUtf8;
    private volatile IOException serveFailure;

    LoopbackHttpServer(int statusCode, String responseBody) {
      this(statusCode, responseBody, null, null);
    }

    LoopbackHttpServer(int statusCode, String responseBody,
        AtomicReference<String> capturedBodyUtf8) {
      this(statusCode, responseBody, capturedBodyUtf8, null);
    }

    LoopbackHttpServer(int statusCode, String responseBody,
        AtomicReference<String> capturedBodyUtf8,
        AtomicReference<String> capturedFullRequestUtf8) {
      this.statusCode = statusCode;
      this.responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
      this.capturedBodyUtf8 = capturedBodyUtf8;
      this.capturedFullRequestUtf8 = capturedFullRequestUtf8;
      try {
        this.serverSocket = new ServerSocket(0);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      this.thread = new Thread(this::serveOnce, "claude-loopback-http");
      this.thread.setDaemon(true);
      this.thread.start();
    }

    String baseUrl() {
      return "http://127.0.0.1:" + serverSocket.getLocalPort();
    }

    private void serveOnce() {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(15_000);
        String requestText = drainHttpRequest(socket.getInputStream());
        if (capturedFullRequestUtf8 != null) {
          capturedFullRequestUtf8.set(requestText);
        }
        if (capturedBodyUtf8 != null) {
          int sep = requestText.indexOf("\r\n\r\n");
          if (sep >= 0) {
            capturedBodyUtf8.set(requestText.substring(sep + 4));
          }
        }
        String reasonPhrase = switch (statusCode) {
          case 200 -> "OK";
          case 400 -> "Bad Request";
          case 401 -> "Unauthorized";
          case 503 -> "Service Unavailable";
          default -> "Error";
        };
        String headers = "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n"
            + "Content-Length: " + responseBodyBytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(responseBodyBytes);
        out.flush();
      } catch (IOException e) {
        serveFailure = e;
      } finally {
        try {
          serverSocket.close();
        } catch (IOException ignored) {
        }
      }
    }

    private static String drainHttpRequest(InputStream in) throws IOException {
      byte[] buf = new byte[4096];
      var captured = new ByteArrayOutputStream();
      int total = 0;
      while (total < 262_144) {
        int n = in.read(buf);
        if (n < 0) {
          break;
        }
        captured.write(buf, 0, n);
        total += n;
        byte[] all = captured.toByteArray();
        String raw = new String(all, StandardCharsets.US_ASCII);
        int sep = raw.indexOf("\r\n\r\n");
        if (sep < 0) {
          continue;
        }
        int contentLength = 0;
        String headerSection = raw.substring(0, sep);
        for (String line : headerSection.split("\r\n")) {
          String lower = line.toLowerCase(Locale.ROOT);
          if (lower.startsWith("content-length:")) {
            contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
          }
        }
        int alreadyInBody = all.length - (sep + 4);
        int remaining = contentLength - alreadyInBody;
        while (remaining > 0) {
          int toRead = Math.min(buf.length, remaining);
          int r = in.read(buf, 0, toRead);
          if (r < 0) {
            break;
          }
          captured.write(buf, 0, r);
          remaining -= r;
        }
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
      }
      return "";
    }

    @Override
    public void close() throws Exception {
      serverSocket.close();
      thread.join(15_000);
      if (serveFailure != null) {
        throw serveFailure;
      }
    }
  }

  @Test
  void should_return_assistant_text_on_successful_http_response() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_MESSAGES_JSON)) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "system", "user");

      LlmExecutionResponse response = client.execute(request);

      assertThat(response.text()).isEqualTo("Hello from Claude");
      assertThat(response.tokenUsage()).isNull();
    }
  }

  @Test
  void should_return_token_usage_when_usage_block_present() throws Exception {
    String body = readFixture("messages-with-usage.json");
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, body)) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "system", "user");

      LlmExecutionResponse response = client.execute(request);

      assertThat(response.text()).isEqualTo("Hello");
      assertThat(response.tokenUsage()).isNotNull();
      assertThat(response.tokenUsage().inputTokens()).isEqualTo(11);
      assertThat(response.tokenUsage().outputTokens()).isEqualTo(7);
    }
  }

  private static String readFixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = ClaudeLlmClientIT.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_MESSAGES_JSON)) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("CLAUDE", null, "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("Hello from Claude");
    }
  }

  @Test
  void should_throw_when_provider_name_mismatched() {
    ClaudeLlmClient client =
        new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("openai", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(503, "upstream")) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("claude")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, "{ not-json")) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("claude request failed");
    }
  }

  @Test
  void should_fail_when_http_body_is_empty_on_success_status() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, "")) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("json");
    }
  }

  @Test
  void should_fail_when_response_has_no_usable_text_blocks() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200,
        "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_01\"}]}")) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("no text content");
    }
  }

  @Test
  void should_post_json_body_matching_messages_api_contract() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_MESSAGES_JSON, captured)) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .defaultModel("capture-model")
          .maxTokenSize(2048)
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "S", "U");

      client.execute(request);

      assertThat(captured.get())
          .contains("\"model\":\"capture-model\"")
          .contains("\"max_tokens\":2048")
          .contains("\"system\":[{\"type\":\"text\",\"text\":\"S\"}]")
          .contains("\"content\":\"U\"")
          .contains("\"role\":");
    }
  }

  @Test
  void should_send_claude_auth_headers_and_json_content_type() throws Exception {
    AtomicReference<String> fullRequest = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_MESSAGES_JSON, null,
        fullRequest)) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .apiKey("secret-key-789")
          .apiVersion("2023-06-01")
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("claude", "sys", "usr");

      client.execute(request);

      String raw = fullRequest.get();
      assertThat(raw).isNotNull();
      int headerEnd = raw.indexOf("\r\n\r\n");
      assertThat(headerEnd).isPositive();
      String headers = raw.substring(0, headerEnd);
      String lower = headers.toLowerCase(Locale.ROOT);
      assertThat(lower)
          .contains("content-type: application/json")
          .contains("x-api-key: secret-key-789")
          .contains("anthropic-version: 2023-06-01");
    }
  }

  @Test
  void should_use_config_default_model_when_execution_request_specifies_other_model()
      throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_MESSAGES_JSON, captured)) {
      var config = FixedClaudeConfiguration.builder()
          .url(http.baseUrl())
          .defaultModel("default-model")
          .build();
      ClaudeLlmClient client = new ClaudeLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("claude", "explicit-model", "sys", "usr");

      client.execute(request);

      assertThat(captured.get()).contains("\"model\":\"explicit-model\"");
    }
  }
}
