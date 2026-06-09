package com.agentforge4j.mcp.client.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamableHttpTransportTest {

  private static final String URL = "http://127.0.0.1:1/mcp";
  private static final Duration TIMEOUT = Duration.ofSeconds(1);
  private static final McpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(new ObjectMapper());

  // --- construction-time validation -----------------------------------------------------------

  @Test
  void rejectsSecretHeadersWithoutResolver() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of(), Map.of("Authorization", "token-ref"), null, JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("secretResolver is required");
  }

  @Test
  void rejectsHeaderDeclaredAsBothLiteralAndSecret() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of("Authorization", "Bearer literal"),
        Map.of("authorization", "token-ref"), reference -> "Bearer resolved", JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both a literal and a secret-reference");
  }

  @Test
  void rejectsBlankSecretReference() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of(), Map.of("Authorization", "  "), reference -> "x", JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("secret-reference key");
  }

  @Test
  void rejectsBlankHeaderName() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of("", "value"), Map.of(), null, JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("header name");
  }

  @Test
  void rejectsBlankLiteralHeaderValue() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of("X-Api-Version", "  "), Map.of(), null, JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value for header");
  }

  @Test
  void rejectsLiteralHeaderNamesDifferingOnlyByCase() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of("X-Token", "a", "x-token", "b"), Map.of(), null, JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate header name");
  }

  @Test
  void rejectsSecretHeaderNamesDifferingOnlyByCase() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of(), Map.of("Authorization", "ref-a", "AUTHORIZATION", "ref-b"),
        reference -> "Bearer resolved", JSON_MAPPER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate header name");
  }

  @Test
  void rejectsNullJsonMapper() {
    assertThatThrownBy(() -> new StreamableHttpTransport(URL, TIMEOUT,
        Map.of(), Map.of(), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jsonMapper");
  }

  // --- header resolution ----------------------------------------------------------------------

  @Test
  void resolvesNoHeadersWhenNoneConfigured() {
    StreamableHttpTransport transport =
        new StreamableHttpTransport(URL, TIMEOUT, Map.of(), Map.of(), null, JSON_MAPPER);
    assertThat(transport.resolveHeaders()).isEmpty();
  }

  @Test
  void passesLiteralHeadersThrough() {
    StreamableHttpTransport transport = new StreamableHttpTransport(URL, TIMEOUT,
        Map.of("X-Api-Version", "2024-01"), Map.of(), null, JSON_MAPPER);
    assertThat(transport.resolveHeaders()).containsEntry("X-Api-Version", "2024-01");
  }

  @Test
  void resolvesSecretReferenceHeadersThroughResolver() {
    Function<String, String> resolver = reference ->
        "token-ref".equals(reference) ? "Bearer SECRET" : null;
    StreamableHttpTransport transport = new StreamableHttpTransport(URL, TIMEOUT,
        Map.of("X-Api-Version", "2024-01"), Map.of("Authorization", "token-ref"), resolver,
        JSON_MAPPER);

    Map<String, String> resolved = transport.resolveHeaders();

    assertThat(resolved).containsEntry("X-Api-Version", "2024-01");
    assertThat(resolved).containsEntry("Authorization", "Bearer SECRET");
  }

  @Test
  void throwsWhenSecretResolvesToBlank() {
    StreamableHttpTransport transport = new StreamableHttpTransport(URL, TIMEOUT,
        Map.of(), Map.of("Authorization", "token-ref"), reference -> "  ", JSON_MAPPER);
    assertThatThrownBy(transport::resolveHeaders)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("resolved to a blank value");
  }

  // --- headers reach the wire -----------------------------------------------------------------

  @Test
  void sendsResolvedHeadersOnTheWire() throws Exception {
    try (HeaderCapturingHttpServer server = new HeaderCapturingHttpServer()) {
      StreamableHttpTransport transport = new StreamableHttpTransport(server.baseUrl(), TIMEOUT,
          Map.of("X-Api-Version", "2024-01"), Map.of("Authorization", "token-ref"),
          reference -> "Bearer SECRET", JSON_MAPPER);
      try {
        // The handshake will not complete against the stub server; the request is sent first.
        transport.start();
      } catch (RuntimeException ignored) {
        // expected: the loopback server is not a real MCP server.
      } finally {
        transport.close();
      }

      List<Map<String, String>> captured = server.capturedHeaders();
      assertThat(captured).isNotEmpty();
      assertThat(captured).anySatisfy(headers -> {
        assertThat(headers).containsEntry("authorization", "Bearer SECRET");
        assertThat(headers).containsEntry("x-api-version", "2024-01");
      });
    }
  }

  @Test
  void sendsNoAuthorizationHeaderWhenNoneConfigured() throws Exception {
    try (HeaderCapturingHttpServer server = new HeaderCapturingHttpServer()) {
      StreamableHttpTransport transport = new StreamableHttpTransport(
          server.baseUrl(), TIMEOUT, Map.of(), Map.of(), null, JSON_MAPPER);
      try {
        transport.start();
      } catch (RuntimeException ignored) {
        // expected
      } finally {
        transport.close();
      }

      List<Map<String, String>> captured = server.capturedHeaders();
      assertThat(captured).isNotEmpty();
      assertThat(captured).allSatisfy(headers ->
          assertThat(headers).doesNotContainKey("authorization"));
    }
  }
}
