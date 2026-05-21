package com.agentforge4j.llm.ollama;

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
 * Integration tests for {@link OllamaLlmClient} using a local loopback HTTP server (no mocks, no
 * API keys, no external network).
 */
class OllamaLlmClientIT {

  private static final String VALID_CHAT_JSON = """
      {
        "error": null,
        "message": {
          "role": "assistant",
          "content": "Hello from loopback"
        }
      }
      """;

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
      this.thread = new Thread(this::serveOnce, "ollama-loopback-http");
      this.thread.setDaemon(true);
      this.thread.start();
    }

    String baseUrl() {
      return "http://127.0.0.1:" + serverSocket.getLocalPort();
    }

    String chatUrl() {
      return baseUrl() + "/api/chat";
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

  private static OllamaConfiguration configForUrl(String chatUrl,
      java.time.Duration requestTimeout) {
    return new OllamaConfiguration() {
      @Override
      public String getDefaultModel() {
        return "llama2";
      }

      @Override
      public java.time.Duration getConnectTimeout() {
        return java.time.Duration.ofSeconds(5);
      }

      @Override
      public java.time.Duration getRequestTimeout() {
        return requestTimeout;
      }

      @Override
      public String getUrl() {
        return chatUrl;
      }
    };
  }

  @Test
  void should_return_message_content_on_successful_http_response() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_CHAT_JSON)) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "system prompt", "user input");

      var response = client.execute(request);
      assertThat(response.text()).isEqualTo("Hello from loopback");
      assertThat(response.tokenUsage()).isNull();
    }
  }

  @Test
  void should_return_token_usage_when_eval_counts_present() throws Exception {
    String body = readFixture("chat-with-usage.json");
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, body)) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "system prompt", "user input");

      LlmExecutionResponse response = client.execute(request);
      assertThat(response.text()).isEqualTo("Hello");
      assertThat(response.tokenUsage()).isNotNull();
      assertThat(response.tokenUsage().inputTokens()).isEqualTo(50);
      assertThat(response.tokenUsage().outputTokens()).isEqualTo(12);
    }
  }

  private static String readFixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = OllamaLlmClientIT.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void should_match_request_provider_case_insensitively() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_CHAT_JSON)) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          new LlmExecutionRequest("OLLAMA", null, "system prompt", "user input");

      assertThat(client.execute(request).text()).isEqualTo("Hello from loopback");
    }
  }

  @Test
  void should_throw_when_provider_name_mismatched() {
    OllamaLlmClient client = new OllamaLlmClient(new ObjectMapper(),
        configForUrl("http://127.0.0.1:9/api/chat", java.time.Duration.ofSeconds(1)));
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("openai", "system prompt", "user input");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void should_throw_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(503, "busy")) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "system prompt", "user input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("ollama")
          .hasMessageContaining("503")
          .hasMessageContaining("busy");
    }
  }

  @Test
  void should_throw_when_http_200_body_contains_ollama_error_field() throws Exception {
    String errorBody = """
        {
          "error": "model not found",
          "message": null
        }
        """;
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, errorBody)) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "system prompt", "user input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("Ollama error")
          .hasMessageContaining("model not found");
    }
  }

  @Test
  void should_wrap_json_parse_failure_in_llm_invocation_exception() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, "{ not-json")) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "system prompt", "user input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("ollama request failed");
    }
  }

  @Test
  void should_post_json_body_matching_chat_api_contract() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_CHAT_JSON, captured)) {
      OllamaLlmClient client = new OllamaLlmClient(new ObjectMapper(),
          configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "S", "U");

      client.execute(request);

      assertThat(captured.get())
          .contains("\"model\":\"llama2\"")
          .contains("\"stream\":false")
          .contains("\"role\":\"system\"")
          .contains("\"content\":\"S\"")
          .contains("\"role\":\"user\"")
          .contains("\"content\":\"U\"");
    }
  }

  @Test
  void should_send_json_content_type_header() throws Exception {
    AtomicReference<String> fullRequest = new AtomicReference<>();
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_CHAT_JSON, null,
        fullRequest)) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("ollama", "sys", "usr");

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
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_CHAT_JSON, captured)) {
      OllamaLlmClient client =
          new OllamaLlmClient(new ObjectMapper(),
              configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30)));
      LlmExecutionRequest request =
          new LlmExecutionRequest("ollama", "explicit-model", "sys", "usr");

      client.execute(request);

      assertThat(captured.get()).contains("\"model\":\"explicit-model\"");
    }
  }

  @Test
  void factory_create_produces_client_end_to_end() throws Exception {
    try (LoopbackHttpServer http = new LoopbackHttpServer(200, VALID_CHAT_JSON)) {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
      OllamaConfiguration config = configForUrl(http.chatUrl(), java.time.Duration.ofSeconds(30));
      var client = factory.create(new ObjectMapper(), config);

      assertThat(client.execute(LlmExecutionRequest.withDefaultModel("ollama", "a", "b")).text())
          .isEqualTo("Hello from loopback");
    }
  }
}
