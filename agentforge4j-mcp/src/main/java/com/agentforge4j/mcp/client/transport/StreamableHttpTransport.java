// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client.transport;

import com.agentforge4j.util.Validate;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * {@link McpTransport} that connects to a remote MCP server over Streamable HTTP.
 *
 * <p>Carries a fixed set of per-server request headers (for example {@code Authorization} for
 * hosted, auth-requiring servers), attached to every request. Header values are either literals
 * ({@code staticHeaders}) or secret-reference keys ({@code secretHeaders}) resolved at connect time
 * through an embedder-supplied {@code secretResolver}, mirroring the {@code HttpToolProvider}
 * secret model. Pass empty maps and a {@code null} resolver for a server with no headers. Any
 * variation of header values across callers is resolved by the embedding application, which supplies
 * the resolved configuration here.
 */
public final class StreamableHttpTransport extends AbstractSdkMcpTransport {

  private final String url;
  private final Map<String, String> staticHeaders;
  private final Map<String, String> secretHeaders;
  private final Function<String, String> secretResolver;

  /**
   * Creates a Streamable HTTP transport.
   *
   * @param url            the base URL of the remote MCP server (non-blank)
   * @param requestTimeout per-request timeout
   * @param staticHeaders  literal header name to value pairs (values non-blank); {@code null}
   *                       treated as empty
   * @param secretHeaders  header name to secret-reference key; resolved at connect via
   *                       {@code secretResolver}; {@code null} treated as empty
   * @param secretResolver resolves a secret-reference key to its value; required when
   *                       {@code secretHeaders} is non-empty
   * @param jsonMapper     the JSON mapper used by the SDK transport
   */
  public StreamableHttpTransport(String url, Duration requestTimeout,
      Map<String, String> staticHeaders, Map<String, String> secretHeaders,
      Function<String, String> secretResolver, McpJsonMapper jsonMapper) {
    super(jsonMapper, requestTimeout);
    this.url = Validate.notBlank(url, "url must not be blank");
    this.staticHeaders = staticHeaders != null ? Map.copyOf(staticHeaders) : Map.of();
    this.secretHeaders = secretHeaders != null ? Map.copyOf(secretHeaders) : Map.of();
    this.secretResolver = secretResolver;
    validateHeaders();
  }

  @Override
  protected McpClientTransport createSdkTransport() {
    Map<String, String> headers = resolveHeaders();
    HttpClientStreamableHttpTransport.Builder builder =
        HttpClientStreamableHttpTransport.builder(url).jsonMapper(jsonMapper())
            // Never follow redirects: the configured URL is egress-validated before connect, but the
            // SDK owns the socket, so a 30x from that URL to a private/cloud-metadata host would
            // otherwise be followed and bypass the guard. Mirrors HttpToolProviderFactory's client.
            .customizeClient(client -> client.followRedirects(HttpClient.Redirect.NEVER));
    if (!headers.isEmpty()) {
      builder.httpRequestCustomizer(
          (requestBuilder, httpMethod, uri, body, context) ->
              headers.forEach(requestBuilder::setHeader));
    }
    return builder.build();
  }

  /**
   * Resolves the final header set: every literal header plus each secret-reference header resolved
   * through the secret resolver. Resolution runs at connect time so rotated secrets are picked up
   * on reconnect.
   *
   * @return the resolved header name to value map (empty when no headers are configured)
   */
  Map<String, String> resolveHeaders() {
    if (staticHeaders.isEmpty() && secretHeaders.isEmpty()) {
      return Map.of();
    }
    Map<String, String> resolved = new LinkedHashMap<>(staticHeaders);
    secretHeaders.forEach((name, secretRef) -> {
      String value = secretResolver.apply(secretRef);
      Validate.notBlank(value, () -> new IllegalStateException(
          "secret '%s' resolved to a blank value".formatted(secretRef)));
      Validate.isTrue(containsNoCrLf(value), () -> new IllegalStateException(
          "secret '%s' resolved to a value containing CR/LF".formatted(secretRef)));
      resolved.put(name, value);
    });
    return Map.copyOf(resolved);
  }

  private void validateHeaders() {
    staticHeaders.forEach((name, value) -> {
      Validate.notBlank(name, "header name must not be blank");
      Validate.notBlank(value, "value for header '%s' must not be blank".formatted(name));
      Validate.isTrue(containsNoCrLf(name) && containsNoCrLf(value),
          "header '%s' must not contain CR/LF characters".formatted(name));
    });
    secretHeaders.forEach((name, secretRef) -> {
      Validate.notBlank(name, "header name must not be blank");
      Validate.notBlank(secretRef,
          "secret-reference key for header '%s' must not be blank".formatted(name));
      Validate.isTrue(containsNoCrLf(name),
          "header '%s' must not contain CR/LF characters".formatted(name));
    });
    Set<String> staticNames = caseInsensitiveNames(staticHeaders);
    caseInsensitiveNames(secretHeaders).forEach(name -> Validate.isTrue(
        !staticNames.contains(name),
        "header '%s' must not be both a literal and a secret-reference".formatted(name)));
    Validate.isTrue(secretHeaders.isEmpty() || secretResolver != null,
        "secretResolver is required when secret-reference headers are present");
  }

  /**
   * Rejects CR/LF early with a clear message. The JDK HTTP client refuses such values anyway, but
   * only deep inside the SDK at connect time; failing here points at the offending header instead.
   */
  private static boolean containsNoCrLf(String value) {
    return value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
  }

  /**
   * Collects header names into a case-insensitive set, rejecting names that differ only by case —
   * HTTP header names are case-insensitive, so such duplicates would silently overwrite each other
   * on the request.
   */
  private static Set<String> caseInsensitiveNames(Map<String, String> headers) {
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    headers.keySet().forEach(name -> Validate.isTrue(names.add(name),
        "duplicate header name '%s' (header names are case-insensitive)".formatted(name)));
    return names;
  }
}
