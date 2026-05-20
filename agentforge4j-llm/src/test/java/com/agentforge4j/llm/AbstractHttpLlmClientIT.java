package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AbstractHttpLlmClient} using a local loopback HTTP server and the
 * real JDK {@link java.net.http.HttpClient} stack (no mocks).
 */
class AbstractHttpLlmClientIT {

  static class UriTargetingHttpLlmClient extends AbstractHttpLlmClient {

    private final URI endpoint;

    UriTargetingHttpLlmClient(LlmClientConfiguration config, URI endpoint) {
      super(config);
      this.endpoint = endpoint;
    }

    @Override
    protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
      return HttpRequest.newBuilder(endpoint)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build();
    }

    @Override
    protected String validateAndExtractResponse(String json) throws IOException {
      return json;
    }
  }

  static final class FailingExtractHttpLlmClient extends UriTargetingHttpLlmClient {

    FailingExtractHttpLlmClient(LlmClientConfiguration config, URI endpoint) {
      super(config, endpoint);
    }

    @Override
    protected String validateAndExtractResponse(String json) throws IOException {
      throw new IOException("malformed provider payload");
    }
  }

  /**
   * Minimal loopback HTTP/1.1 server that accepts one connection, drains the request, and returns a
   * fixed response (avoids a {@code jdk.httpserver} module dependency in {@code module-info}).
   */
  static final class OneShotHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread thread;
    private volatile IOException serveFailure;

    OneShotHttpServer(int statusCode, String responseBody) throws IOException {
      serverSocket = new ServerSocket(0);
      byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
      thread = new Thread(() -> serveOnce(statusCode, bodyBytes), "llm-one-shot-http-it");
      thread.setDaemon(true);
      thread.start();
    }

    URI endpointForPath(String path) {
      return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + path);
    }

    private void serveOnce(int statusCode, byte[] bodyBytes) {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(15_000);
        drainHttpRequest(socket.getInputStream());
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

    private static void drainHttpRequest(InputStream in) throws IOException {
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
          remaining -= r;
        }
        return;
      }
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
  void execute_returns_body_on_successful_http_response() throws Exception {
    try (OneShotHttpServer http = new OneShotHttpServer(200, "{\"reply\":\"ok\"}")) {
      URI endpoint = http.endpointForPath("/llm");
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
      UriTargetingHttpLlmClient client = new UriTargetingHttpLlmClient(config, endpoint);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      LlmExecutionResponse result = client.execute(request);

      assertThat(result.text()).isEqualTo("{\"reply\":\"ok\"}");
      assertThat(result.tokenUsage()).isNull();
    }
  }

  @Test
  void execute_matches_request_provider_case_insensitively() throws Exception {
    try (OneShotHttpServer http = new OneShotHttpServer(200, "pong")) {
      URI endpoint = http.endpointForPath("/llm");
      LlmClientConfiguration config = TestFixtures.testConfig("OpenAI", "gpt-4");
      UriTargetingHttpLlmClient client = new UriTargetingHttpLlmClient(config, endpoint);
      LlmExecutionRequest request =
          new LlmExecutionRequest("OPENAI", null, "system", "user");

      assertThat(client.execute(request).text()).isEqualTo("pong");
    }
  }

  @Test
  void execute_throws_llm_invocation_exception_on_non_2xx_status() throws Exception {
    try (OneShotHttpServer http = new OneShotHttpServer(503, "upstream unavailable")) {
      URI endpoint = http.endpointForPath("/llm");
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
      UriTargetingHttpLlmClient client = new UriTargetingHttpLlmClient(config, endpoint);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai")
          .hasMessageContaining("503")
          .hasMessageContaining("upstream unavailable");
    }
  }

  @Test
  void execute_wraps_io_exception_from_response_validation() throws Exception {
    try (OneShotHttpServer http = new OneShotHttpServer(200, "{}")) {
      URI endpoint = http.endpointForPath("/llm");
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
      FailingExtractHttpLlmClient client = new FailingExtractHttpLlmClient(config, endpoint);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("openai request failed")
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Test
  void execute_surfaces_connection_refused_as_llm_invocation_exception() throws Exception {
    int port;
    try (ServerSocket reserved = new ServerSocket(0)) {
      port = reserved.getLocalPort();
    }
    URI endpoint = URI.create("http://127.0.0.1:" + port + "/closed");
    LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
    UriTargetingHttpLlmClient client = new UriTargetingHttpLlmClient(config, endpoint);
    LlmExecutionRequest request =
        LlmExecutionRequest.withDefaultModel("openai", "system", "user");

    assertThatThrownBy(() -> client.execute(request))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("openai request failed")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void execute_handles_empty_response_body_on_200() throws Exception {
    try (OneShotHttpServer http = new OneShotHttpServer(200, "")) {
      URI endpoint = http.endpointForPath("/v1/chat");
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
      UriTargetingHttpLlmClient client = new UriTargetingHttpLlmClient(config, endpoint);
      LlmExecutionRequest request =
          LlmExecutionRequest.withDefaultModel("openai", "system", "user");

      assertThat(client.execute(request).text()).isEmpty();
    }
  }
}
