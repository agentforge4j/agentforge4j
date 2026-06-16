// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link MistralLlmClient} using a local loopback HTTP server and the real
 * JDK {@link java.net.http.HttpClient} stack (no mocks, no external providers).
 */
class MistralLlmClientIT {

  /**
   * Minimal loopback HTTP/1.1 server: reads one full request (including body), stores it for
   * assertions, then returns a configurable response.
   */
  static final class CapturingOneShotHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread thread;
    private volatile String capturedRawRequest;
    private volatile IOException serveFailure;

    CapturingOneShotHttpServer(int statusCode, String responseBody) throws IOException {
      serverSocket = new ServerSocket(0);
      byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
      thread = new Thread(() -> serveOnce(statusCode, bodyBytes), "mistral-capture-http-it");
      thread.setDaemon(true);
      thread.start();
    }

    URI baseUrl() {
      return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
    }

    String capturedRawRequest() {
      return capturedRawRequest;
    }

    private void serveOnce(int statusCode, byte[] bodyBytes) {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(30_000);
        capturedRawRequest = readFullHttpRequestAsString(socket.getInputStream());
        String reasonPhrase = switch (statusCode) {
          case 200 -> "OK";
          case 503 -> "Service Unavailable";
          default -> "Error";
        };
        String headers = "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n"
            + "Content-Length: " + bodyBytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(bodyBytes);
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

    private static String readFullHttpRequestAsString(InputStream in) throws IOException {
      byte[] buf = new byte[4096];
      var captured = new ByteArrayOutputStream();
      int total = 0;
      while (total < 512_000) {
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
          remaining -= r;
          captured.write(buf, 0, r);
        }
        return captured.toString(StandardCharsets.UTF_8);
      }
      return captured.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws Exception {
      serverSocket.close();
      thread.join(30_000);
      if (serveFailure != null) {
        throw serveFailure;
      }
    }
  }

  private static String validCompletionJson(String content) {
    return """
        {
          "error": null,
          "choices": [
            { "message": { "role": "assistant", "content": "%s" } }
          ]
        }
        """.formatted(content.replace("\\", "\\\\").replace("\"", "\\\""));
  }

  @Test
  void execute_posts_to_v1_chat_completions_with_expected_headers_and_body() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String responseJson = validCompletionJson("hello from fake mistral");
    try (CapturingOneShotHttpServer http = new CapturingOneShotHttpServer(200, responseJson)) {
      var config = FixedMistralConfiguration.builder()
          .baseUrl(http.baseUrl().toString())
          .apiKey("it-secret-key")
          .defaultModel("mistral-small-latest")
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("mistral", null, "You are helpful.", "Say hi.", null, null, null);

      var response = client.execute(request);

      assertThat(response.text()).isEqualTo("hello from fake mistral");
      assertThat(response.tokenUsage()).isNull();
      String raw = http.capturedRawRequest();
      assertThat(raw).contains("POST /v1/chat/completions ");
      assertThat(raw.toLowerCase(Locale.ROOT)).contains("authorization: bearer it-secret-key");
      assertThat(raw.toLowerCase(Locale.ROOT)).contains("content-type: application/json");

      int headerEnd = raw.indexOf("\r\n\r\n");
      assertThat(headerEnd).isGreaterThan(0);
      String body = raw.substring(headerEnd + 4);
      JsonNode tree = mapper.readTree(body);
      assertThat(tree.path("model").asText()).isEqualTo("mistral-small-latest");
      assertThat(tree.path("messages")).hasSize(2);
      assertThat(tree.path("messages").path(0).path("role").asText()).isEqualTo("system");
      assertThat(tree.path("messages").path(0).path("content").asText()).isEqualTo(
          "You are helpful.");
      assertThat(tree.path("messages").path(1).path("role").asText()).isEqualTo("user");
      assertThat(tree.path("messages").path(1).path("content").asText()).isEqualTo("Say hi.");
    }
  }

  @Test
  void execute_returns_token_usage_when_usage_block_present() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String responseJson = readFixture("chat-with-usage.json");
    try (CapturingOneShotHttpServer http = new CapturingOneShotHttpServer(200, responseJson)) {
      var config = FixedMistralConfiguration.builder()
          .baseUrl(http.baseUrl().toString())
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("mistral", null, "system", "user", null, null, null);

      LlmExecutionResponse response = client.execute(request);
      assertThat(response.text()).isEqualTo("with usage");
      assertThat(response.tokenUsage()).isNotNull();
      assertThat(response.tokenUsage().inputTokens()).isEqualTo(9);
      assertThat(response.tokenUsage().outputTokens()).isEqualTo(4);
    }
  }

  private static String readFixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = MistralLlmClientIT.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void execute_matches_request_provider_case_insensitively() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (CapturingOneShotHttpServer http = new CapturingOneShotHttpServer(200,
        validCompletionJson("pong"))) {
      var config = FixedMistralConfiguration.builder()
          .baseUrl(http.baseUrl().toString())
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request = new LlmExecutionRequest("MISTRAL", null, "system", "user", null, null, null);

      assertThat(client.execute(request).text()).isEqualTo("pong");
    }
  }

  @Test
  void execute_throws_llm_invocation_exception_on_non_2xx_status() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (CapturingOneShotHttpServer http = new CapturingOneShotHttpServer(503,
        "upstream unavailable")) {
      var config = FixedMistralConfiguration.builder()
          .baseUrl(http.baseUrl().toString())
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("mistral", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("mistral")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream unavailable");
    }
  }

  @Test
  void execute_throws_when_http_200_but_choices_empty() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String body = """
        { "error": null, "choices": [] }
        """;
    try (CapturingOneShotHttpServer http = new CapturingOneShotHttpServer(200, body)) {
      var config = FixedMistralConfiguration.builder()
          .baseUrl(http.baseUrl().toString())
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("mistral", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("choices are empty");
    }
  }

  @Test
  void execute_throws_when_http_200_but_json_is_not_object() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (CapturingOneShotHttpServer http = new CapturingOneShotHttpServer(200,
        "\"not-an-object\"")) {
      var config = FixedMistralConfiguration.builder()
          .baseUrl(http.baseUrl().toString())
          .build();
      MistralLlmClient client = new MistralLlmClient(mapper, config);
      LlmExecutionRequest request =
          new LlmExecutionRequest("mistral", null, "system", "user", null, null, null);

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("mistral request failed");
    }
  }

  @Test
  void execute_surfaces_connection_refused_as_llm_invocation_exception() {
    int port;
    try (ServerSocket reserved = new ServerSocket(0)) {
      port = reserved.getLocalPort();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    ObjectMapper mapper = new ObjectMapper();
    var config = FixedMistralConfiguration.builder()
        .baseUrl("http://127.0.0.1:" + port)
        .build();
    MistralLlmClient client = new MistralLlmClient(mapper, config);
    LlmExecutionRequest request =
        new LlmExecutionRequest("mistral", null, "system", "user", null, null, null);

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("mistral request failed")
        .hasCauseInstanceOf(IOException.class);
  }
}
