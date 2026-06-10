package com.agentforge4j.tools.http;

import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * {@link ToolProvider} that fulfils logical capabilities with single governed HTTP calls described
 * by code-defined {@link HttpEndpointDefinition}s. It is the tool-SPI-native successor to the
 * legacy endpoint-call command: capability resolution stays with the resolver; this provider maps
 * one capability invocation to one request, applies a transient-only retry/timeout policy that can
 * only tighten the runtime options, and maps the response to a {@link ToolResult}.
 *
 * <p>All definitions are validated at construction (fail-fast) so a bad configuration is rejected
 * at wiring time, not mid-run. Secret header values are resolved through the supplied resolver at
 * invoke time and are never inlined into a definition or routed through arguments.
 */
public final class HttpToolProvider implements ToolProvider {

  private static final System.Logger LOG = System.getLogger(HttpToolProvider.class.getName());

  private static final Set<Integer> RETRYABLE_HTTP_STATUS = Set.of(429, 500, 502, 503, 504);
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
  private static final int ERROR_BODY_LIMIT = 512;

  private final String providerId;
  private final Map<String, HttpEndpointDefinition> definitionsByCapability;
  private final List<ToolDescriptor> descriptors;
  private final Function<String, String> secretResolver;
  private final HttpClient httpClient;
  private final ToolExecutionOptions defaultOptions;
  private final long defaultMaxResponseBytes;
  private final ObjectMapper objectMapper;

  /**
   * Creates a provider over a fixed set of endpoint definitions, validating them up front.
   *
   * @param configuredName          non-blank name; {@link #providerId()} is {@code "http:" + name}
   * @param definitions             endpoint definitions; non-null, each validated (§2.8)
   * @param secretResolver          secret-reference key to value resolver, used at invoke time
   * @param httpClient              the JDK HTTP client used for all calls
   * @param defaultOptions          fallback execution options when none are supplied at invoke
   * @param defaultMaxResponseBytes response body read cap for definitions that do not set one
   *
   * @throws IllegalArgumentException if the name is blank or any definition is invalid
   */
  public HttpToolProvider(String configuredName, List<HttpEndpointDefinition> definitions,
      Function<String, String> secretResolver, HttpClient httpClient,
      ToolExecutionOptions defaultOptions, long defaultMaxResponseBytes,
      ObjectMapper objectMapper) {
    Validate.notBlank(configuredName, "configuredName must not be blank");
    this.providerId = "http:" + configuredName;
    Validate.notNull(definitions, "definitions must not be null");
    this.secretResolver = Validate.notNull(secretResolver, "secretResolver must not be null");
    this.httpClient = Validate.notNull(httpClient, "httpClient must not be null");
    this.defaultOptions = Validate.notNull(defaultOptions, "defaultOptions must not be null");
    Validate.isGreaterThanZero(defaultMaxResponseBytes,
        "defaultMaxResponseBytes must be greater than zero");
    this.defaultMaxResponseBytes = defaultMaxResponseBytes;
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.definitionsByCapability = validateAndIndex(definitions);
    this.descriptors = buildDescriptors(this.definitionsByCapability.values());
  }

  @Override
  public String providerId() {
    return providerId;
  }

  @Override
  public List<ToolDescriptor> listTools() {
    return List.copyOf(descriptors);
  }

  @Override
  public ToolResult invoke(ToolDescriptor descriptor, String arguments, ToolInvocationContext ctx,
      ToolExecutionOptions options) {
    Validate.notNull(descriptor, "descriptor must not be null");
    Validate.notNull(descriptor.source(), "descriptor source must not be null");
    ToolExecutionOptions effectiveOptions = ObjectUtils.getIfNull(options, defaultOptions);
    long startNanos = System.nanoTime();
    try {
      HttpEndpointDefinition definition = Validate.notNull(
          definitionsByCapability.get(descriptor.source().remoteToolName()),
          () -> new IllegalArgumentException("No HTTP endpoint for capability '%s'"
              .formatted(descriptor.source().remoteToolName())));
      ObjectNode args = parseArguments(arguments);
      PreparedRequest prepared = mapRequest(definition, args);
      Duration effectiveTimeout = effectiveTimeout(definition, effectiveOptions);
      int effectiveRetries = effectiveMaxRetries(definition, effectiveOptions);
      HttpRequest request = buildHttpRequest(definition, prepared, effectiveTimeout);
      HttpResponse<InputStream> response =
          sendWithRetry(request, effectiveTimeout, effectiveRetries,
              effectiveOptions.retryBackoff());
      return mapResponse(definition, response, latencyMillis(startNanos));
    } catch (MappingException | OversizedResponseException e) {
      return ToolResult.failure(e.getMessage(), latencyMillis(startNanos));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ToolResult.failure("HTTP request interrupted", latencyMillis(startNanos));
    } catch (IOException e) {
      return ToolResult.failure("HTTP request failed: %s".formatted(String.valueOf(e.getMessage())),
          latencyMillis(startNanos));
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "HTTP tool invocation failed", e);
      return ToolResult.failure("HTTP tool invocation failed: %s"
          .formatted(String.valueOf(e.getMessage())), latencyMillis(startNanos));
    }
  }

  @Override
  public HealthStatus health() {
    return new HealthStatus(HealthStatus.State.UP, "structural");
  }

  // --- construction-time validation -----------------------------------------------------------

  private Map<String, HttpEndpointDefinition> validateAndIndex(
      List<HttpEndpointDefinition> definitions) {
    Map<String, HttpEndpointDefinition> indexed = new LinkedHashMap<>();
    for (HttpEndpointDefinition definition : definitions) {
      Validate.notNull(definition, "definitions must not contain null");
      validateDefinition(definition);
      HttpEndpointDefinition existing = indexed.putIfAbsent(definition.capability(), definition);
      Validate.isTrue(existing == null,
          "Duplicate capability '%s' within provider '%s'".formatted(definition.capability(),
              providerId));
    }
    return Map.copyOf(indexed);
  }

  private void validateDefinition(HttpEndpointDefinition definition) {
    validateUrlTemplate(definition.urlTemplate());
    Validate.isTrue(definition.bodyMode() != BodyMode.JSON || allowsBody(definition.method()),
        "bodyMode JSON is illegal on %s for capability '%s'".formatted(definition.method(),
            definition.capability()));
    Validate.isTrue(!definition.retryNonIdempotent() || isOptInRetryable(definition.method()),
        "retryNonIdempotent is only meaningful on POST/PATCH for capability '%s'"
            .formatted(definition.capability()));
    validateSecretHeaders(definition);
    validateDisposition(definition);
  }

  private static void validateUrlTemplate(String urlTemplate) {
    String probe = PLACEHOLDER.matcher(urlTemplate).replaceAll("x");
    Validate.isTrue(probe.indexOf('{') < 0 && probe.indexOf('}') < 0,
        "urlTemplate has malformed placeholders: %s".formatted(urlTemplate));
    final URI uri;
    try {
      uri = new URI(probe);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(
          "urlTemplate is not a valid URL: %s".formatted(urlTemplate));
    }
    String scheme = uri.getScheme();
    Validate.isTrue(uri.isAbsolute() && ("http".equals(scheme) || "https".equals(scheme)),
        "urlTemplate must be an absolute http/https URL: %s".formatted(urlTemplate));
    validateNoPlaceholderBeforePath(urlTemplate);
  }

  /**
   * Rejects templates with a placeholder in the scheme or authority (everything before the path) so
   * invoke-time values can never choose the target host or port — values may only vary the path and
   * query of the configured endpoint.
   */
  private static void validateNoPlaceholderBeforePath(String urlTemplate) {
    int firstPlaceholder = urlTemplate.indexOf('{');
    if (firstPlaceholder < 0) {
      return;
    }
    int authorityStart = urlTemplate.indexOf("://") + 3;
    int pathStart = urlTemplate.length();
    for (int i = authorityStart; i < urlTemplate.length(); i++) {
      char c = urlTemplate.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        pathStart = i;
        break;
      }
    }
    Validate.isTrue(firstPlaceholder >= pathStart,
        "urlTemplate must not contain placeholders before the path (host and port are fixed): %s"
            .formatted(urlTemplate));
  }

  private void validateSecretHeaders(HttpEndpointDefinition definition) {
    Set<String> staticLower = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    staticLower.addAll(definition.staticHeaders().keySet());
    definition.secretHeaders().forEach((header, secretRef) -> {
      Validate.notBlank(secretRef,
          "secretHeaders ref for '%s' must not be blank (capability '%s')"
              .formatted(header, definition.capability()));
      Validate.isTrue(!staticLower.contains(header),
          "header '%s' is declared in both staticHeaders and secretHeaders (capability '%s')"
              .formatted(header, definition.capability()));
    });
  }

  private void validateDisposition(HttpEndpointDefinition definition) {
    Set<String> properties = schemaProperties(definition.inputSchema());
    Set<String> placeholders = placeholderNames(definition.urlTemplate());
    for (String placeholder : placeholders) {
      Validate.isTrue(properties.contains(placeholder),
          "URL placeholder '{%s}' is not a declared inputSchema property (capability '%s')"
              .formatted(placeholder, definition.capability()));
    }
    for (String queryArg : definition.queryArgs()) {
      Validate.isTrue(properties.contains(queryArg),
          "queryArg '%s' is not a declared inputSchema property (capability '%s')"
              .formatted(queryArg, definition.capability()));
    }
    for (String property : properties) {
      boolean inPath = placeholders.contains(property);
      boolean inQuery = definition.queryArgs().contains(property);
      Validate.isTrue(!(inPath && inQuery),
          "inputSchema property '%s' maps to both a path placeholder and a queryArg (capability '%s')"
              .formatted(property, definition.capability()));
      if (!inPath && !inQuery) {
        Validate.isTrue(definition.bodyMode() == BodyMode.JSON,
            "inputSchema property '%s' has no path/query target and bodyMode is NONE (capability '%s')"
                .formatted(property, definition.capability()));
      }
    }
  }

  private List<ToolDescriptor> buildDescriptors(Iterable<HttpEndpointDefinition> definitions) {
    List<ToolDescriptor> result = new ArrayList<>();
    for (HttpEndpointDefinition definition : definitions) {
      result.add(new ToolDescriptor(
          definition.capability(),
          definition.displayName(),
          definition.description(),
          writeJson(definition.inputSchema()),
          definition.outputSchema() != null ? writeJson(definition.outputSchema()) : null,
          new ToolSource(providerId, definition.capability())));
    }
    return List.copyOf(result);
  }

  // --- request mapping ------------------------------------------------------------------------

  private ObjectNode parseArguments(String arguments) {
    if (StringUtils.isBlank(arguments)) {
      return objectMapper.createObjectNode();
    }
    final JsonNode node;
    try {
      node = objectMapper.readTree(arguments);
    } catch (IOException e) {
      throw new MappingException("arguments are not valid JSON");
    }
    if (!(node instanceof ObjectNode object)) {
      throw new MappingException("arguments must be a JSON object");
    }
    return object;
  }

  private PreparedRequest mapRequest(HttpEndpointDefinition definition, ObjectNode args) {
    String url = fillPlaceholders(definition.urlTemplate(), args);
    url = appendQueryArgs(url, definition.queryArgs(), args);
    String body = null;
    if (definition.bodyMode() == BodyMode.JSON) {
      body = buildJsonBody(definition, args);
    }
    Map<String, String> headers = resolveHeaders(definition);
    final URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new MappingException("mapped URL is not a valid URI: %s".formatted(url));
    }
    return new PreparedRequest(uri, body, headers);
  }

  private String fillPlaceholders(String urlTemplate, ObjectNode args) {
    int queryStart = urlTemplate.indexOf('?');
    Matcher matcher = PLACEHOLDER.matcher(urlTemplate);
    StringBuilder builder = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      JsonNode value = args.get(name);
      if (value == null || value.isNull()) {
        throw new MappingException("missing argument '%s' for URL placeholder".formatted(name));
      }
      boolean inQuery = queryStart >= 0 && matcher.start() > queryStart;
      String encoded = inQuery ? encodeQuery(scalar(value)) : encodePath(scalar(value));
      matcher.appendReplacement(builder, Matcher.quoteReplacement(encoded));
    }
    matcher.appendTail(builder);
    return builder.toString();
  }

  private String appendQueryArgs(String url, Set<String> queryArgs, ObjectNode args) {
    if (queryArgs.isEmpty()) {
      return url;
    }
    StringBuilder query = new StringBuilder();
    for (String name : new TreeSet<>(queryArgs)) {
      JsonNode value = args.get(name);
      if (value == null || value.isNull()) {
        continue;
      }
      if (!query.isEmpty()) {
        query.append('&');
      }
      query.append(encodeQuery(name)).append('=').append(encodeQuery(scalar(value)));
    }
    if (query.isEmpty()) {
      return url;
    }
    char separator = url.indexOf('?') >= 0 ? '&' : '?';
    return url + separator + query;
  }

  private String buildJsonBody(HttpEndpointDefinition definition, ObjectNode args) {
    Set<String> consumed = new TreeSet<>(placeholderNames(definition.urlTemplate()));
    consumed.addAll(definition.queryArgs());
    ObjectNode body = objectMapper.createObjectNode();
    args.properties().iterator().forEachRemaining(entry -> {
      if (!consumed.contains(entry.getKey())) {
        body.set(entry.getKey(), entry.getValue());
      }
    });
    return writeJson(body);
  }

  private Map<String, String> resolveHeaders(HttpEndpointDefinition definition) {
    Map<String, String> headers = new LinkedHashMap<>(definition.staticHeaders());
    definition.secretHeaders().forEach((header, secretRef) -> {
      String resolved = secretResolver.apply(secretRef);
      Validate.notBlank(resolved,
          () -> new MappingException("secret '%s' resolved to a blank value".formatted(secretRef)));
      headers.put(header, resolved);
    });
    return headers;
  }

  private HttpRequest buildHttpRequest(HttpEndpointDefinition definition, PreparedRequest prepared,
      Duration effectiveTimeout) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(prepared.uri()).timeout(effectiveTimeout);
    prepared.headers().forEach(builder::header);
    final HttpRequest.BodyPublisher publisher;
    if (definition.bodyMode() == BodyMode.JSON && prepared.body() != null) {
      publisher = HttpRequest.BodyPublishers.ofString(prepared.body(), StandardCharsets.UTF_8);
      if (!hasHeaderIgnoreCase(prepared.headers(), "Content-Type")) {
        builder.header("Content-Type", "application/json");
      }
    } else {
      publisher = HttpRequest.BodyPublishers.noBody();
    }
    builder.method(definition.method().name(), publisher);
    return builder.build();
  }

  // --- execution ------------------------------------------------------------------------------

  private HttpResponse<InputStream> sendWithRetry(HttpRequest request, Duration effectiveTimeout,
      int maxRetries, Duration retryBackoff) throws IOException, InterruptedException {
    // The timeout is per attempt and the backoff ceiling equals it, so total wall-clock may
    // approach (maxRetries + 1) * timeout plus sleeps. The execution service's authoritative
    // hard timeout (see ToolExecutionOptions) bounds the overall invocation, not this provider.
    long base = Math.max(0L, retryBackoff.toMillis());
    long ceiling = Math.max(base, effectiveTimeout.toMillis());
    long lastSleep = base;
    IOException lastFailure = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      boolean hasMore = attempt < maxRetries;
      try {
        HttpResponse<InputStream> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (hasMore && RETRYABLE_HTTP_STATUS.contains(response.statusCode())) {
          drainQuietly(response.body());
          lastSleep = backoff(base, lastSleep, ceiling);
          continue;
        }
        return response;
      } catch (IOException e) {
        lastFailure = e;
        if (!hasMore) {
          throw e;
        }
        LOG.log(System.Logger.Level.WARNING,
            "HTTP call failed (attempt {0}/{1}): {2}. Retrying.",
            attempt + 1, maxRetries + 1, String.valueOf(e));
        lastSleep = backoff(base, lastSleep, ceiling);
      }
    }
    throw lastFailure != null ? lastFailure : new IOException("HTTP request failed");
  }

  private static long backoff(long base, long lastSleep, long ceiling) throws InterruptedException {
    if (base <= 0L) {
      return 0L;
    }
    long sleepMs = Math.min(ceiling, randomBetweenBaseAndTriple(base, lastSleep));
    Thread.sleep(sleepMs);
    return sleepMs;
  }

  private static long randomBetweenBaseAndTriple(long base, long lastSleep) {
    long triple = cappedMultiplyByThree(lastSleep);
    long upperInclusive = Math.max(base, triple);
    if (upperInclusive == base) {
      return base;
    }
    long hiExclusive = upperInclusive + 1;
    if (hiExclusive <= upperInclusive) {
      return ThreadLocalRandom.current().nextLong(base, Long.MAX_VALUE);
    }
    return ThreadLocalRandom.current().nextLong(base, hiExclusive);
  }

  private static long cappedMultiplyByThree(long value) {
    if (value > Long.MAX_VALUE / 3) {
      return Long.MAX_VALUE;
    }
    return value * 3;
  }

  // --- response mapping -----------------------------------------------------------------------

  private ToolResult mapResponse(HttpEndpointDefinition definition,
      HttpResponse<InputStream> response, long latencyMillis) throws IOException {
    long maxBytes = ObjectUtils.getIfNull(definition.maxResponseBytes(), defaultMaxResponseBytes);
    byte[] body = readCapped(response.body(), maxBytes);
    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      if (body.length == 0) {
        // Empty 2xx body (e.g. 204 No Content): the JSON literal null keeps the output parseable.
        return ToolResult.success("null", latencyMillis);
      }
      String text = new String(body, StandardCharsets.UTF_8);
      if (parsesAsJson(text)) {
        return ToolResult.success(text, latencyMillis);
      }
      String contentType = response.headers().firstValue("Content-Type").orElse(null);
      ObjectNode wrapped = objectMapper.createObjectNode();
      wrapped.put("contentType", contentType);
      wrapped.put("text", text);
      return ToolResult.success(writeJson(wrapped), latencyMillis);
    }
    String truncated = truncate(new String(body, StandardCharsets.UTF_8), ERROR_BODY_LIMIT);
    return ToolResult.failure(
        "HTTP %d: %s; %s".formatted(status, reasonPhrase(status), truncated), latencyMillis);
  }

  private byte[] readCapped(InputStream stream, long maxBytes) throws IOException {
    try (InputStream in = stream) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      long total = 0L;
      int read;
      while ((read = in.read(chunk)) >= 0) {
        total += read;
        if (total > maxBytes) {
          throw new OversizedResponseException(maxBytes);
        }
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    }
  }

  private boolean parsesAsJson(String text) {
    try {
      objectMapper.readTree(text);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private Duration effectiveTimeout(HttpEndpointDefinition definition,
      ToolExecutionOptions options) {
    Duration runtime = options.timeout();
    if (definition.timeout() == null) {
      return runtime;
    }
    return definition.timeout().compareTo(runtime) <= 0 ? definition.timeout() : runtime;
  }

  private int effectiveMaxRetries(HttpEndpointDefinition definition, ToolExecutionOptions options) {
    if (isOptInRetryable(definition.method()) && !definition.retryNonIdempotent()) {
      return 0;
    }
    int runtime = options.maxRetries();
    return definition.maxRetries() >= 0 ? Math.min(definition.maxRetries(), runtime) : runtime;
  }

  private static boolean allowsBody(HttpMethod method) {
    return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
  }

  private static boolean isOptInRetryable(HttpMethod method) {
    return method == HttpMethod.POST || method == HttpMethod.PATCH;
  }

  private static Set<String> schemaProperties(JsonNode inputSchema) {
    JsonNode properties = inputSchema.get("properties");
    if (properties == null || !properties.isObject()) {
      return Set.of();
    }
    Set<String> names = new TreeSet<>();
    properties.fieldNames().forEachRemaining(names::add);
    return Set.copyOf(names);
  }

  private static Set<String> placeholderNames(String urlTemplate) {
    Set<String> names = new TreeSet<>();
    Matcher matcher = PLACEHOLDER.matcher(urlTemplate);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return Set.copyOf(names);
  }

  private static String scalar(JsonNode value) {
    Validate.isTrue(!value.isContainerNode(),
        () -> new MappingException("placeholder/query value must be a scalar"));
    return value.asText();
  }

  private static String encodePath(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String encodeQuery(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String name) {
    for (String key : headers.keySet()) {
      if (key.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (IOException e) {
      throw new MappingException("failed to serialize JSON: %s".formatted(e.getMessage()));
    }
  }

  private static String truncate(String value, int limit) {
    if (value.length() <= limit) {
      return value;
    }
    return value.substring(0, limit) + "...";
  }

  private static long latencyMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private static String reasonPhrase(int status) {
    return switch (status) {
      case 400 -> "Bad Request";
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 409 -> "Conflict";
      case 429 -> "Too Many Requests";
      case 500 -> "Internal Server Error";
      case 502 -> "Bad Gateway";
      case 503 -> "Service Unavailable";
      case 504 -> "Gateway Timeout";
      default -> "HTTP error";
    };
  }

  private static void drainQuietly(InputStream stream) {
    try (InputStream in = stream) {
      in.readAllBytes();
    } catch (IOException ignored) {
      // Draining a to-be-retried response is best effort.
    }
  }

  /**
   * Mapped request: target URI, optional JSON body text, and the final header set.
   */
  private record PreparedRequest(URI uri, String body, Map<String, String> headers) {

  }

  /**
   * Signals a deterministic request/response mapping failure that yields a failed
   * {@link ToolResult} without any partial HTTP effect being treated as success.
   */
  private static final class MappingException extends RuntimeException {

    private MappingException(String message) {
      super(message);
    }
  }

  /**
   * Signals that the response body exceeded the configured read cap.
   */
  private static final class OversizedResponseException extends RuntimeException {

    private OversizedResponseException(long maxBytes) {
      super("response body exceeded %d bytes".formatted(maxBytes));
    }
  }
}
