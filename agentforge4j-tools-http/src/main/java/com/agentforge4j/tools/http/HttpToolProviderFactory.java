package com.agentforge4j.tools.http;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Realises {@link IntegrationType#HTTP_TOOL} integrations: builds an {@link HttpToolProvider} over
 * the {@link HttpEndpointDefinition}s carried in the {@code config} payload (a JSON array, one
 * object per capability). Secret-header references are resolved at invoke time through the
 * {@link ToolProviderFactoryContext#secretResolver()}; the resulting provider id is
 * {@code "http:" + definition.id()}.
 * <p>
 * Discovered via {@link java.util.ServiceLoader}; no connection is opened here — each request is
 * made lazily by the provider on invocation. This is the config-loaded sibling of the code-defined
 * {@code AgentForge4jBootstrap.defaults().withToolProviders(...)} path, which keeps working
 * unchanged.
 */
public final class HttpToolProviderFactory implements IntegrationToolProviderFactory {

  /**
   * Response-body read cap applied to endpoints that do not set {@code maxResponseBytes} (1 MiB).
   */
  static final long DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L;

  /**
   * Connection-establishment timeout for the shared HTTP client, mirroring the LLM HTTP clients.
   */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  @Override
  public IntegrationType supportedType() {
    return IntegrationType.HTTP_TOOL;
  }

  @Override
  public ToolProvider create(IntegrationDefinition definition, ToolProviderFactoryContext context) {
    Validate.notNull(definition, "definition must not be null");
    Validate.notNull(context, "context must not be null");
    Validate.isTrue(definition.type() == IntegrationType.HTTP_TOOL,
        "Integration '%s' has type %s; this factory only supports HTTP_TOOL"
            .formatted(definition.id(), definition.type()));
    ObjectMapper mapper = context.objectMapper();
    List<HttpEndpointDefinition> endpoints = HttpIntegrations.parseEndpoints(definition, mapper);
    return new HttpToolProvider(definition.id(), endpoints, context.secretResolver()::resolve,
        defaultHttpClient(), ToolExecutionOptions.defaults(), DEFAULT_MAX_RESPONSE_BYTES, mapper);
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
  }
}
