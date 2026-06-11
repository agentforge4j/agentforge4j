package com.agentforge4j.tools.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.runtime.tool.InMemoryIntegrationRepository;
import com.agentforge4j.runtime.tool.IntegrationToolProviderResolver;
import com.agentforge4j.tools.http.LoopbackHttpServer.Captured;
import com.agentforge4j.tools.http.LoopbackHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpToolProviderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final Map<String, String> secrets = Map.of("auth.token", "Bearer SECRET");
  private final ToolInvocationContext ctx =
      new ToolInvocationContext("run-1", "1", "agent-1", new ToolScope("wf-1", "run-1"));
  private final ToolExecutionOptions noRetry =
      new ToolExecutionOptions(Duration.ofSeconds(5), 0, Duration.ZERO);
  private final ToolExecutionOptions withRetry =
      new ToolExecutionOptions(Duration.ofSeconds(5), 2, Duration.ofMillis(1));

  // --- descriptors ----------------------------------------------------------------------------

  @Test
  void listToolsMapsDefinitionsToDescriptors() {
    HttpEndpointDefinition definition = new HttpEndpointDefinition(
        "weather.get_current", "Get weather", "Current weather", HttpMethod.GET,
        "https://example.com/weather/{city}", objectSchema("city"), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);
    HttpToolProvider provider = provider(definition);

    List<ToolDescriptor> tools = provider.listTools();

    assertThat(tools).hasSize(1);
    ToolDescriptor descriptor = tools.get(0);
    assertThat(descriptor.capability()).isEqualTo("weather.get_current");
    assertThat(descriptor.displayName()).isEqualTo("Get weather");
    assertThat(descriptor.inputSchema()).contains("\"city\"");
    assertThat(descriptor.source().providerId()).isEqualTo("http:test");
    assertThat(descriptor.source().remoteToolName()).isEqualTo("weather.get_current");
    assertThat(provider.providerId()).isEqualTo("http:test");
    assertThat(provider.health().state()).isEqualTo(HealthStatus.State.UP);
    assertThat(provider.health().detail()).isEqualTo("structural");
  }

  // --- request mapping ------------------------------------------------------------------------

  @Test
  void getEncodesPathSegmentAndQuery() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":true}"))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "items.get", "items", null, HttpMethod.GET, server.baseUri() + "/items/{id}",
          objectSchema("id", "q"), null, Set.of("q"), BodyMode.NONE, Map.of(), Map.of(),
          null, -1, false, null);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "items.get"),
          "{\"id\":\"a/b c\",\"q\":\"x y\"}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("{\"ok\":true}");
      Captured request = server.captured().get(0);
      assertThat(request.method()).isEqualTo("GET");
      assertThat(request.target()).isEqualTo("/items/a%2Fb%20c?q=x+y");
    }
  }

  @Test
  void postSendsRemainingArgsAsJsonBody() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"id\":7}"))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "orders.create", "orders", null, HttpMethod.POST, server.baseUri() + "/orders",
          objectSchema("sku", "qty"), null, Set.of(), BodyMode.JSON, Map.of(), Map.of(),
          null, -1, false, null);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "orders.create"),
          "{\"sku\":\"X1\",\"qty\":\"2\"}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      Captured request = server.captured().get(0);
      assertThat(request.method()).isEqualTo("POST");
      assertThat(request.headers()).containsEntry("Content-Type", "application/json");
      JsonNode body = MAPPER.readTree(request.body());
      assertThat(body.get("sku").asText()).isEqualTo("X1");
      assertThat(body.get("qty").asText()).isEqualTo("2");
    }
  }

  @Test
  void getWithBodyModeNoneSendsNoBody() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{}"))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "health.ping", "ping", null, HttpMethod.GET, server.baseUri() + "/ping",
          objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "health.ping"), "{}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      assertThat(server.captured().get(0).body()).isEmpty();
    }
  }

  @Test
  void secretHeaderIsResolvedAtInvokeAndNeverInlined() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{}"))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "secure.get", "secure", null, HttpMethod.GET, server.baseUri() + "/secure",
          objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(),
          Map.of("Authorization", "auth.token"), null, -1, false, null);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "secure.get"), "{}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      // The definition holds only the secret-reference key, not the value.
      assertThat(definition.secretHeaders()).containsEntry("Authorization", "auth.token");
      assertThat(server.captured().get(0).headers()).containsEntry("Authorization", "Bearer SECRET");
    }
  }

  // --- response mapping -----------------------------------------------------------------------

  @Test
  void successJsonBodyBecomesRawOutput() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "[1,2,3]"))) {
      ToolResult result = invokeSimpleGet(server, "{}");
      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("[1,2,3]");
    }
  }

  @Test
  void successEmptyBodyBecomesJsonNull() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(new Response(200, "", null, 0L))) {
      ToolResult result = invokeSimpleGet(server, "{}");
      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("null");
    }
  }

  @Test
  void successNonJsonBodyIsWrapped() throws Exception {
    try (LoopbackHttpServer server =
        new LoopbackHttpServer(new Response(200, "hello world", "text/plain", 0L))) {
      ToolResult result = invokeSimpleGet(server, "{}");
      assertThat(result.success()).isTrue();
      JsonNode output = MAPPER.readTree(result.output());
      assertThat(output.get("contentType").asText()).isEqualTo("text/plain");
      assertThat(output.get("text").asText()).isEqualTo("hello world");
    }
  }

  @Test
  void nonSuccessStatusBecomesFailureWithStatusInMessage() throws Exception {
    try (LoopbackHttpServer server =
        new LoopbackHttpServer(new Response(404, "not found", "text/plain", 0L))) {
      ToolResult result = invokeSimpleGet(server, "{}");
      assertThat(result.success()).isFalse();
      assertThat(result.output()).isNull();
      assertThat(result.errorMessage()).contains("HTTP 404").contains("not found");
    }
  }

  @Test
  void oversizedResponseBecomesFailure() throws Exception {
    try (LoopbackHttpServer server =
        new LoopbackHttpServer(Response.json(200, "{\"big\":\"xxxxxxxxxxxxxxxx\"}"))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "small.get", "small", null, HttpMethod.GET, server.baseUri() + "/small",
          objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, 8L);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "small.get"), "{}", ctx, noRetry);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("exceeded");
    }
  }

  // --- retry / timeout ------------------------------------------------------------------------

  @Test
  void idempotentGetRetriesOnRetryableStatus() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(
        new Response(503, "down", null, 0L), Response.json(200, "{\"ok\":1}"))) {
      ToolResult result = invokeSimpleGet(server, "{}", withRetry);
      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("{\"ok\":1}");
      assertThat(server.captured()).hasSize(2);
    }
  }

  @Test
  void postIsNotRetriedByDefault() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(
        new Response(503, "down", null, 0L), Response.json(200, "{\"ok\":1}"))) {
      HttpEndpointDefinition definition = postDefinition(server, false);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "orders.create"),
          "{\"sku\":\"X\"}", ctx, withRetry);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("HTTP 503");
      assertThat(server.captured()).hasSize(1);
    }
  }

  @Test
  void postIsRetriedWhenOptedIn() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(
        new Response(503, "down", null, 0L), Response.json(200, "{\"ok\":1}"))) {
      HttpEndpointDefinition definition = postDefinition(server, true);
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "orders.create"),
          "{\"sku\":\"X\"}", ctx, withRetry);

      assertThat(result.success()).isTrue();
      assertThat(server.captured()).hasSize(2);
    }
  }

  @Test
  void endpointTimeoutRestrictsRuntimeOption() throws Exception {
    try (LoopbackHttpServer server =
        new LoopbackHttpServer(new Response(200, "{}", "application/json", 1_500L))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "slow.get", "slow", null, HttpMethod.GET, server.baseUri() + "/slow",
          objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(), Map.of(),
          Duration.ofMillis(200), -1, false, null);
      HttpToolProvider provider = provider(definition);

      // noRetry has a 5s timeout; the 200ms endpoint ceiling must win and fail fast.
      ToolResult result = provider.invoke(descriptor(provider, "slow.get"), "{}", ctx, noRetry);

      assertThat(result.success()).isFalse();
      assertThat(result.latencyMillis()).isLessThan(1_500L);
    }
  }

  // --- constructor validation (§2.8) ----------------------------------------------------------

  @Test
  void rejectsDuplicateCapability() {
    HttpEndpointDefinition first = new HttpEndpointDefinition(
        "dup.cap", "a", null, HttpMethod.GET, "https://example.com/a", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);
    HttpEndpointDefinition second = new HttpEndpointDefinition(
        "dup.cap", "b", null, HttpMethod.GET, "https://example.com/b", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);

    assertThatThrownBy(() -> provider(first, second))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate capability");
  }

  @Test
  void rejectsJsonBodyOnGet() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.get", "g", null, HttpMethod.GET, "https://example.com/g", objectSchema("x"), null,
        Set.of(), BodyMode.JSON, Map.of(), Map.of(), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bodyMode JSON");
  }

  @Test
  void rejectsRetryNonIdempotentOnGet() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.retry", "g", null, HttpMethod.GET, "https://example.com/g", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, true, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryNonIdempotent");
  }

  @Test
  void rejectsOrphanPropertyWhenBodyNone() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.orphan", "g", null, HttpMethod.GET, "https://example.com/g", objectSchema("x"), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no path/query target");
  }

  @Test
  void rejectsBlankSecretRef() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.secret", "g", null, HttpMethod.GET, "https://example.com/g", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of("Authorization", "  "), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void rejectsHeaderInBothStaticAndSecret() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.overlap", "g", null, HttpMethod.GET, "https://example.com/g", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of("Authorization", "static"),
        Map.of("Authorization", "auth.token"), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both staticHeaders and secretHeaders");
  }

  @Test
  void rejectsNonAbsoluteUrl() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.url", "g", null, HttpMethod.GET, "/relative/path", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("absolute http/https");
  }

  @Test
  void rejectsPlaceholderNotDeclaredInSchema() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.ph", "g", null, HttpMethod.GET, "https://example.com/{id}", objectSchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a declared inputSchema property");
  }

  @Test
  void rejectsPlaceholderInHost() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.host", "g", null, HttpMethod.GET, "https://{host}/g", objectSchema("host"), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("placeholders before the path");
  }

  @Test
  void rejectsPlaceholderInPort() {
    assertThatThrownBy(() -> provider(new HttpEndpointDefinition(
        "bad.port", "g", null, HttpMethod.GET, "https://example.com:{port}/g",
        objectSchema("port"), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("placeholders before the path");
  }

  @Test
  void allowsPlaceholderInPathAndQuery() {
    HttpToolProvider ok = provider(new HttpEndpointDefinition(
        "ok.ph", "g", null, HttpMethod.GET, "https://example.com/items/{id}?tag={tag}",
        objectSchema("id", "tag"), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null));
    assertThat(ok.listTools()).hasSize(1);
  }

  // --- resolver integration -------------------------------------------------------------------

  @Test
  void resolvesAndRunsThroughIntegrationResolverAlongsideAnotherProvider() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":1}"))) {
      HttpEndpointDefinition definition = new HttpEndpointDefinition(
          "items.get", "items", null, HttpMethod.GET, server.baseUri() + "/items",
          objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);
      HttpToolProvider httpProvider = provider(definition);
      ToolProvider other = new StubProvider("stub:other", "other.cap");

      IntegrationToolProviderResolver resolver = new IntegrationToolProviderResolver(
          new InMemoryIntegrationRepository(), unusedFactory(), List.of(httpProvider, other));
      ResolvedTool resolved = resolver.resolve("items.get", new ToolScope("wf-1", "run-1"));

      assertThat(resolved.provider()).isSameAs(httpProvider);
      ToolResult result = resolved.provider().invoke(resolved.descriptor(), "{}", ctx, noRetry);
      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("{\"ok\":1}");
    }
  }

  @Test
  void duplicateCapabilityAcrossProvidersFailsFast() {
    HttpEndpointDefinition definition = new HttpEndpointDefinition(
        "shared.cap", "items", null, HttpMethod.GET, "https://example.com/items",
        objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);
    HttpToolProvider httpProvider = provider(definition);
    ToolProvider clashing = new StubProvider("stub:other", "shared.cap");

    assertThatThrownBy(() -> new IntegrationToolProviderResolver(
        new InMemoryIntegrationRepository(), unusedFactory(), List.of(httpProvider, clashing)))
        .isInstanceOf(CapabilityResolutionException.class);
  }

  // --- helpers --------------------------------------------------------------------------------

  /** The pre-built providers feed the resolver directly, so the factory is never called. */
  private static ToolProviderFactory unusedFactory() {
    return definition -> {
      throw new AssertionError("factory must not be called for pre-built providers");
    };
  }

  private HttpToolProvider provider(HttpEndpointDefinition... definitions) {
    return new HttpToolProvider("test", List.of(definitions), secrets::get, httpClient, noRetry,
        1_048_576L, new ObjectMapper());
  }

  private ToolResult invokeSimpleGet(LoopbackHttpServer server, String arguments) {
    return invokeSimpleGet(server, arguments, noRetry);
  }

  private ToolResult invokeSimpleGet(LoopbackHttpServer server, String arguments,
      ToolExecutionOptions options) {
    HttpEndpointDefinition definition = new HttpEndpointDefinition(
        "simple.get", "simple", null, HttpMethod.GET, server.baseUri() + "/simple",
        objectSchema(), null, Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, -1, false, null);
    HttpToolProvider provider = provider(definition);
    return provider.invoke(descriptor(provider, "simple.get"), arguments, ctx, options);
  }

  private HttpEndpointDefinition postDefinition(LoopbackHttpServer server, boolean retryNonIdempotent) {
    return new HttpEndpointDefinition(
        "orders.create", "orders", null, HttpMethod.POST, server.baseUri() + "/orders",
        objectSchema("sku"), null, Set.of(), BodyMode.JSON, Map.of(), Map.of(), null, -1,
        retryNonIdempotent, null);
  }

  private static ToolDescriptor descriptor(HttpToolProvider provider, String capability) {
    return provider.listTools().stream()
        .filter(descriptor -> descriptor.capability().equals(capability))
        .findFirst().orElseThrow();
  }

  private static JsonNode objectSchema(String... properties) {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode props = schema.putObject("properties");
    for (String property : properties) {
      props.putObject(property).put("type", "string");
    }
    return schema;
  }

  private static final class StubProvider implements ToolProvider {

    private final String id;
    private final String capability;

    private StubProvider(String id, String capability) {
      this.id = id;
      this.capability = capability;
    }

    @Override
    public String providerId() {
      return id;
    }

    @Override
    public List<ToolDescriptor> listTools() {
      return List.of(new ToolDescriptor(capability, capability, null, null, null,
          new ToolSource(id, capability)));
    }

    @Override
    public ToolResult invoke(ToolDescriptor descriptor, String arguments,
        ToolInvocationContext context, ToolExecutionOptions options) {
      return ToolResult.success("\"stub\"", 0L);
    }

    @Override
    public HealthStatus health() {
      return new HealthStatus(HealthStatus.State.UP, "stub");
    }
  }
}
