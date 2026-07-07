// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.tools.http;

import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import com.agentforge4j.util.net.HttpEgressGuard;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
        .withCapability("weather.get_current")
        .withDisplayName("Get weather")
        .withDescription("Current weather")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/weather/{city}")
        .withInputSchema(objectSchema("city"))
        .withBodyMode(BodyMode.NONE)
        .build();
    HttpToolProvider provider = provider(definition);

    List<ToolDescriptor> tools = provider.listTools();

    assertThat(tools).hasSize(1);
    ToolDescriptor descriptor = tools.get(0);
    assertThat(descriptor.capability()).isEqualTo("weather.get_current");
    assertThat(descriptor.displayName()).isEqualTo("Get weather");
    assertThat(descriptor.inputSchema()).contains("\"city\"");
    assertThat(descriptor.source().providerId()).isEqualTo("http:test");
    assertThat(descriptor.source().remoteToolName()).isEqualTo("weather.get_current");
    assertThat(descriptor.riskMetadata().mutating()).isFalse();
    assertThat(provider.providerId()).isEqualTo("http:test");
    assertThat(provider.health().state()).isEqualTo(HealthStatus.State.UP);
    assertThat(provider.health().detail()).isEqualTo("structural");
  }

  @Test
  void listToolsCarriesRealisedRiskMetadataFromMutatingFlag() {
    HttpEndpointDefinition readOnly = HttpEndpointDefinition.builder()
        .withCapability("reports.fetch")
        .withDisplayName("reports")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/reports")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();
    HttpEndpointDefinition writing = HttpEndpointDefinition.builder()
        .withCapability("reports.create")
        .withDisplayName("reports")
        .withMutating(true)
        .withMethod(HttpMethod.POST)
        .withUrlTemplate("https://example.com/reports")
        .withInputSchema(objectSchema("name"))
        .withBodyMode(BodyMode.JSON)
        .build();
    HttpToolProvider provider = provider(readOnly, writing);

    assertThat(descriptor(provider, "reports.fetch").riskMetadata().mutating()).isFalse();
    assertThat(descriptor(provider, "reports.create").riskMetadata().mutating()).isTrue();
  }

  // --- request mapping ------------------------------------------------------------------------

  @Test
  void getEncodesPathSegmentAndQuery() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":true}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("items.get")
          .withDisplayName("items")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/items/{id}")
          .withInputSchema(objectSchema("id", "q"))
          .withQueryArgs(Set.of("q"))
          .withBodyMode(BodyMode.NONE)
          .build();
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
  void pathArgumentCannotInjectAuthorityViaAtSign() throws Exception {
    // An '@' in a value must not become userinfo@host: it is percent-encoded into the path, so the
    // request still reaches the configured (loopback) host the egress guard validated, never the
    // injected one. This is the parser-differential / argument-injection SSRF regression guard.
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":true}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("items.get")
          .withDisplayName("items")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/items/{id}")
          .withInputSchema(objectSchema("id"))
          .withBodyMode(BodyMode.NONE)
          .build();
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "items.get"),
          "{\"id\":\"evil@169.254.169.254\"}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      assertThat(server.captured().get(0).target()).isEqualTo("/items/evil%40169.254.169.254");
    }
  }

  @Test
  void pathArgumentEncodesPercentAndSlashRatherThanTraversing() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":true}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("items.get")
          .withDisplayName("items")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/items/{id}")
          .withInputSchema(objectSchema("id"))
          .withBodyMode(BodyMode.NONE)
          .build();
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "items.get"),
          "{\"id\":\"..%2f..%2fadmin\"}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      // The literal '%' is itself encoded to %25, so no decoded '../' traversal reaches the server.
      assertThat(server.captured().get(0).target()).isEqualTo("/items/..%252f..%252fadmin");
    }
  }

  @Test
  void pathArgumentEncodesCrlfRatherThanInjectingIt() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":true}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("items.get")
          .withDisplayName("items")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/items/{id}")
          .withInputSchema(objectSchema("id"))
          .withBodyMode(BodyMode.NONE)
          .build();
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "items.get"),
          "{\"id\":\"x\\r\\nHost: evil\"}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      String target = server.captured().get(0).target();
      assertThat(target).contains("%0D%0A");
      assertThat(target).doesNotContain("\r").doesNotContain("\n");
    }
  }

  @Test
  void invocationWhoseMappedHostIsBlockedIsRefusedByTheEgressGuard() {
    // With the fail-closed guard, a mapped URL whose host resolves to a blocked address is refused
    // before any connection — surfaced as a tool failure naming the blocked host.
    HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
        .withCapability("meta.get")
        .withDisplayName("meta")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("http://169.254.169.254/latest/{path}")
        .withInputSchema(objectSchema("path"))
        .withBodyMode(BodyMode.NONE)
        .build();
    HttpToolProvider provider = new HttpToolProvider("test", List.of(definition), secrets::get,
        httpClient, new HttpEgressGuard(false), noRetry, 1_048_576L, new ObjectMapper());

    ToolResult result = provider.invoke(descriptor(provider, "meta.get"),
        "{\"path\":\"meta-data\"}", ctx, noRetry);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("169.254.169.254", "non-public");
  }

  @Test
  void postSendsRemainingArgsAsJsonBody() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"id\":7}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("orders.create")
          .withDisplayName("orders")
          .withMutating(true)
          .withMethod(HttpMethod.POST)
          .withUrlTemplate(server.baseUri() + "/orders")
          .withInputSchema(objectSchema("sku", "qty"))
          .withBodyMode(BodyMode.JSON)
          .build();
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
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("health.ping")
          .withDisplayName("ping")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/ping")
          .withInputSchema(objectSchema())
          .withBodyMode(BodyMode.NONE)
          .build();
      HttpToolProvider provider = provider(definition);

      ToolResult result = provider.invoke(descriptor(provider, "health.ping"), "{}", ctx, noRetry);

      assertThat(result.success()).isTrue();
      assertThat(server.captured().get(0).body()).isEmpty();
    }
  }

  @Test
  void secretHeaderIsResolvedAtInvokeAndNeverInlined() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("secure.get")
          .withDisplayName("secure")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/secure")
          .withInputSchema(objectSchema())
          .withBodyMode(BodyMode.NONE)
          .withSecretHeaders(Map.of("Authorization", "auth.token"))
          .build();
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
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("small.get")
          .withDisplayName("small")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/small")
          .withInputSchema(objectSchema())
          .withBodyMode(BodyMode.NONE)
          .withMaxResponseBytes(8L)
          .build();
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
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("slow.get")
          .withDisplayName("slow")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/slow")
          .withInputSchema(objectSchema())
          .withBodyMode(BodyMode.NONE)
          .withTimeout(Duration.ofMillis(200))
          .build();
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
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("dup.cap")
        .withDisplayName("a")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/a")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();
    HttpEndpointDefinition second = HttpEndpointDefinition.builder()
        .withCapability("dup.cap")
        .withDisplayName("b")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/b")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();

    assertThatThrownBy(() -> provider(first, second))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate capability");
  }

  @Test
  void rejectsJsonBodyOnGet() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.get")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/g")
        .withInputSchema(objectSchema("x"))
        .withBodyMode(BodyMode.JSON)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bodyMode JSON");
  }

  @Test
  void rejectsRetryNonIdempotentOnGet() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.retry")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/g")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .withRetryNonIdempotent(true)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryNonIdempotent");
  }

  @Test
  void rejectsOrphanPropertyWhenBodyNone() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.orphan")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/g")
        .withInputSchema(objectSchema("x"))
        .withBodyMode(BodyMode.NONE)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no path/query target");
  }

  @Test
  void rejectsBlankSecretRef() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.secret")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/g")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .withSecretHeaders(Map.of("Authorization", "  "))
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void rejectsHeaderInBothStaticAndSecret() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.overlap")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/g")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .withStaticHeaders(Map.of("Authorization", "static"))
        .withSecretHeaders(Map.of("Authorization", "auth.token"))
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both staticHeaders and secretHeaders");
  }

  @Test
  void rejectsNonAbsoluteUrl() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.url")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("/relative/path")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("absolute http/https");
  }

  @Test
  void rejectsPlaceholderNotDeclaredInSchema() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.ph")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/{id}")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a declared inputSchema property");
  }

  @Test
  void rejectsPlaceholderInHost() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.host")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://{host}/g")
        .withInputSchema(objectSchema("host"))
        .withBodyMode(BodyMode.NONE)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("placeholders before the path");
  }

  @Test
  void rejectsPlaceholderInPort() {
    HttpEndpointDefinition first = HttpEndpointDefinition.builder()
        .withCapability("bad.port")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com:{port}/g")
        .withInputSchema(objectSchema("port"))
        .withBodyMode(BodyMode.NONE)
        .build();
    assertThatThrownBy(() -> provider(first))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("placeholders before the path");
  }

  @Test
  void allowsPlaceholderInPathAndQuery() {
    HttpToolProvider ok = provider(HttpEndpointDefinition.builder()
        .withCapability("ok.ph")
        .withDisplayName("g")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items/{id}?tag={tag}")
        .withInputSchema(objectSchema("id", "tag"))
        .withBodyMode(BodyMode.NONE)
        .build());
    assertThat(ok.listTools()).hasSize(1);
  }

  // --- resolver integration -------------------------------------------------------------------

  @Test
  void resolvesAndRunsThroughIntegrationResolverAlongsideAnotherProvider() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":1}"))) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability("items.get")
          .withDisplayName("items")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/items")
          .withInputSchema(objectSchema())
          .withBodyMode(BodyMode.NONE)
          .build();
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
    HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
        .withCapability("shared.cap")
        .withDisplayName("items")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();
    HttpToolProvider httpProvider = provider(definition);
    ToolProvider clashing = new StubProvider("stub:other", "shared.cap");

    assertThatThrownBy(() -> new IntegrationToolProviderResolver(
        new InMemoryIntegrationRepository(), unusedFactory(), List.of(httpProvider, clashing)))
        .isInstanceOf(CapabilityResolutionException.class);
  }

  // --- helpers --------------------------------------------------------------------------------

  /**
   * The pre-built providers feed the resolver directly, so the factory is never called.
   */
  private static ToolProviderFactory unusedFactory() {
    return definition -> {
      throw new AssertionError("factory must not be called for pre-built providers");
    };
  }

  private HttpToolProvider provider(HttpEndpointDefinition... definitions) {
    return new HttpToolProvider("test", List.of(definitions), secrets::get, httpClient,
        new HttpEgressGuard(true), noRetry, 1_048_576L, new ObjectMapper());
  }

  private ToolResult invokeSimpleGet(LoopbackHttpServer server, String arguments) {
    return invokeSimpleGet(server, arguments, noRetry);
  }

  private ToolResult invokeSimpleGet(LoopbackHttpServer server, String arguments,
      ToolExecutionOptions options) {
    HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
        .withCapability("simple.get")
        .withDisplayName("simple")
        .withMutating(false)
        .withMethod(HttpMethod.GET)
        .withUrlTemplate(server.baseUri() + "/simple")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();
    HttpToolProvider provider = provider(definition);
    return provider.invoke(descriptor(provider, "simple.get"), arguments, ctx, options);
  }

  private HttpEndpointDefinition postDefinition(LoopbackHttpServer server, boolean retryNonIdempotent) {
    return HttpEndpointDefinition.builder()
        .withCapability("orders.create")
        .withDisplayName("orders")
        .withMutating(true)
        .withMethod(HttpMethod.POST)
        .withUrlTemplate(server.baseUri() + "/orders")
        .withInputSchema(objectSchema("sku"))
        .withBodyMode(BodyMode.JSON)
        .withRetryNonIdempotent(retryNonIdempotent)
        .build();
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
          new ToolSource(id, capability, ToolSourceKind.REMOTE_HTTP),
          ToolRiskMetadata.conservative()));
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
