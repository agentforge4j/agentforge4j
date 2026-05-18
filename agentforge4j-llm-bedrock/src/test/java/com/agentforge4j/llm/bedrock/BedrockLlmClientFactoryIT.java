package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link BedrockRuntimeClientFactory} and {@link BedrockLlmClientFactory} against a local
 * HTTP server (no AWS, no API keys).
 */
class BedrockLlmClientFactoryIT {

  @Test
  void runtimeClientHitsLocalEndpointOverride() throws Exception {
    byte[] payload = """
        {"content":[{"type":"text","text":"from-local-runtime"}]}
        """.getBytes(UTF_8);
    HttpServer server = newServer(payload, 0);
    server.start();
    try {
      int port = server.getAddress().getPort();
      BedrockConfiguration cfg = localConfig(port);
      try (BedrockRuntimeClient client = BedrockRuntimeClientFactory.create(cfg)) {
        var response = client.invokeModel(InvokeModelRequest.builder()
            .modelId(cfg.getDefaultModel())
            .body(SdkBytes.fromUtf8String("{}"))
            .build());
        assertThat(response.body().asUtf8String()).contains("from-local-runtime");
      }
    } finally {
      server.stop(0);
    }
  }

  @Test
  void factoryCreateProducesWorkingClientAgainstLocalServer() throws Exception {
    byte[] payload = """
        {"content":[{"type":"text","text":"from-factory-it"}]}
        """.getBytes(UTF_8);
    HttpServer server = newServer(payload, 0);
    server.start();
    try {
      int port = server.getAddress().getPort();
      BedrockConfiguration cfg = localConfig(port);
      BedrockLlmClientFactory factory = new BedrockLlmClientFactory();
      LlmClient client = factory.create(new ObjectMapper(), cfg);
      String out = client.execute(new LlmExecutionRequest(
          "bedrock", null, "You are concise.", "Say hello."));
      assertThat(out).isEqualTo("from-factory-it");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void apiCallTimeoutWhenServerDelaysResponse() throws Exception {
    byte[] payload = "{\"content\":[{\"type\":\"text\",\"text\":\"late\"}]}".getBytes(UTF_8);
    HttpServer server = newServer(payload, 3_000);
    server.start();
    try {
      int port = server.getAddress().getPort();
      BedrockConfiguration cfg = FixedBedrockConfiguration.builder()
          .endpointOverride(URI.create("http://127.0.0.1:" + port))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create("local-access-key", "local-secret-key")))
          .connectTimeout(Duration.ofSeconds(2))
          .requestTimeout(Duration.ofMillis(600))
          .build();
      BedrockLlmClientFactory factory = new BedrockLlmClientFactory();
      LlmClient client = factory.create(new ObjectMapper(), cfg);
      assertThatThrownBy(() -> client.execute(
          new LlmExecutionRequest("bedrock", null, "s", "u")))
          .isInstanceOf(SdkClientException.class);
    } finally {
      server.stop(0);
    }
  }

  private static FixedBedrockConfiguration localConfig(int port) {
    return FixedBedrockConfiguration.builder()
        .endpointOverride(URI.create("http://127.0.0.1:" + port))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("local-access-key", "local-secret-key")))
        .connectTimeout(Duration.ofSeconds(5))
        .requestTimeout(Duration.ofSeconds(5))
        .build();
  }

  private static HttpServer newServer(byte[] responseBody, int delayBeforeResponseMs)
      throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
          exchange.sendResponseHeaders(405, -1);
          return;
        }
        try (InputStream in = exchange.getRequestBody()) {
          in.readAllBytes();
        }
        if (delayBeforeResponseMs > 0) {
          try {
            Thread.sleep(delayBeforeResponseMs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(500, -1);
            return;
          }
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
          os.write(responseBody);
        }
      }
    });
    server.setExecutor(Executors.newCachedThreadPool());
    return server;
  }
}
