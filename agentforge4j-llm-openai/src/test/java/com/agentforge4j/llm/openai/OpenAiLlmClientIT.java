// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

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
 * End-to-end tests for {@link OpenAiLlmClient} using a local loopback HTTP server (no mocks, no API
 * keys, no external network).
 */
class OpenAiLlmClientIT {

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
      this.thread = new Thread(this::serveOnce, "openai-loopback-http");
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
        return captured.toString(StandardCharsets.UTF_8);
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
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_RESPONSES_JSON)) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", null, "system", "user", null, null, null);

      var response = client.execute(request);
      assertThat(response.text()).isEqualTo("Hello from OpenAI");
      assertThat(response.tokenUsage()).isNull();
    }
  }

  @Test
  void should_return_token_usage_when_usage_block_present() throws Exception {
    String body = readFixture("responses-with-usage.json");
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, body)) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", null, "system", "user", null, null, null);

      LlmExecutionResponse response = client.execute(request);

      assertThat(response.text()).isEqualTo("Hello from OpenAI");
      assertThat(response.tokenUsage()).isNotNull();
      assertThat(response.tokenUsage().inputTokens()).isEqualTo(120);
      assertThat(response.tokenUsage().outputTokens()).isEqualTo(45);
    }
  }

  private static String readFixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = OpenAiLlmClientIT.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_RESPONSES_JSON)) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("OPENAI", null, "system", "user", null, null, null);

      assertThat(client.execute(request).text()).isEqualTo("Hello from OpenAI");
    }
  }

  @Test
  void should_throw_when_provider_name_mismatched() {
    OpenAiLlmClient client =
        new OpenAiLlmClient(new ObjectMapper(), FixedOpenAiConfiguration.defaults());
    LlmExecutionRequest request =
        new LlmExecutionRequest("azure-openai", null, "system", "user", null, null, null);

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(503, "upstream")) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, "{ not-json")) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai request failed");
    }
  }

  @Test
  void should_post_json_body_matching_responses_api_contract() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_RESPONSES_JSON, captured)) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .defaultModel("capture-model")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", null, "S", "U", null, null, null);

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
  void should_send_bearer_and_json_content_type_headers() throws Exception {
    AtomicReference<String> fullRequest = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_RESPONSES_JSON, null,
        fullRequest)) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .apiKey("secret-key-123")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", null, "sys", "usr", null, null, null);

      client.execute(request);

      String raw = fullRequest.get();
      assertThat(raw).isNotNull();
      int headerEnd = raw.indexOf("\r\n\r\n");
      assertThat(headerEnd).isPositive();
      String headers = raw.substring(0, headerEnd);
      assertThat(headers.toLowerCase(Locale.ROOT))
          .contains("authorization: bearer secret-key-123")
          .contains("content-type: application/json");
    }
  }

  @Test
  void should_use_explicit_model_from_execution_request() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_RESPONSES_JSON, captured)) {
      var config = FixedOpenAiConfiguration.builder()
          .url(http.baseUrl())
          .defaultModel("default-model")
          .build();
      OpenAiLlmClient client = new OpenAiLlmClient(new ObjectMapper(), config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("openai", "explicit-model", "sys", "usr", null, null, null);

      client.execute(request);

      assertThat(captured.get()).contains("\"model\":\"explicit-model\"");
    }
  }
}
