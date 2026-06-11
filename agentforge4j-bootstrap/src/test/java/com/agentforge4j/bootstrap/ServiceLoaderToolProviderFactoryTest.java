package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.agentforge4j.core.spi.integration.IntegrationCapability;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceLoaderToolProviderFactoryTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void create_routesDefinitionToContributionMatchingItsType() {
    ToolProvider stdioProvider = mock(ToolProvider.class);
    ToolProvider httpProvider = mock(ToolProvider.class);
    ServiceLoaderToolProviderFactory factory = new ServiceLoaderToolProviderFactory(List.of(
        new FixedContribution(IntegrationType.MCP_STDIO, stdioProvider),
        new FixedContribution(IntegrationType.MCP_STREAMABLE_HTTP, httpProvider)), objectMapper);

    assertThat(factory.create(definition("github", IntegrationType.MCP_STDIO)))
        .isSameAs(stdioProvider);
    assertThat(factory.create(definition("jira", IntegrationType.MCP_STREAMABLE_HTTP)))
        .isSameAs(httpProvider);
  }

  @Test
  void create_passesTheSharedMapperToTheContributionContext() {
    CapturingContribution contribution = new CapturingContribution();
    ServiceLoaderToolProviderFactory factory =
        new ServiceLoaderToolProviderFactory(List.of(contribution), objectMapper);

    factory.create(definition("github", IntegrationType.MCP_STDIO));

    assertThat(contribution.seenContext).isNotNull();
    assertThat(contribution.seenContext.objectMapper()).isSameAs(objectMapper);
  }

  @Test
  void create_failsFastNamingTypeAndIntegrationWhenNoContributionIsRegistered() {
    ServiceLoaderToolProviderFactory factory = new ServiceLoaderToolProviderFactory(List.of(
        new FixedContribution(IntegrationType.MCP_STDIO, mock(ToolProvider.class))), objectMapper);

    assertThatThrownBy(() -> factory.create(definition("airtable", IntegrationType.HTTP_TOOL)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP_TOOL")
        .hasMessageContaining("airtable");
  }

  @Test
  void constructor_failsOnDuplicateContributionsForOneTypeNamingBothClasses() {
    FixedContribution first = new FixedContribution(IntegrationType.MCP_STDIO,
        mock(ToolProvider.class));
    OtherStdioContribution second = new OtherStdioContribution();

    assertThatThrownBy(() -> new ServiceLoaderToolProviderFactory(List.of(first, second),
        objectMapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MCP_STDIO")
        .hasMessageContaining(FixedContribution.class.getName())
        .hasMessageContaining(OtherStdioContribution.class.getName());
  }

  @Test
  void discover_toleratesAnEmptyClasspathButFailsOnFirstUnroutableDefinition() {
    // bootstrap declares no concrete provider module, so discovery here finds no contributions —
    // the aggregator must still construct and only fail when asked to realise a definition.
    ServiceLoaderToolProviderFactory factory =
        ServiceLoaderToolProviderFactory.discover(objectMapper);

    assertThatThrownBy(() -> factory.create(definition("github", IntegrationType.MCP_STDIO)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MCP_STDIO");
  }

  private static IntegrationDefinition definition(String id, IntegrationType type) {
    return new IntegrationDefinition(id, "Display", type, "{}",
        List.of(new IntegrationCapability("domain.action", null, false)), true);
  }

  private static final class FixedContribution implements IntegrationToolProviderFactory {

    private final IntegrationType type;
    private final ToolProvider provider;

    private FixedContribution(IntegrationType type, ToolProvider provider) {
      this.type = type;
      this.provider = provider;
    }

    @Override
    public IntegrationType supportedType() {
      return type;
    }

    @Override
    public ToolProvider create(IntegrationDefinition definition,
        ToolProviderFactoryContext context) {
      return provider;
    }
  }

  private static final class CapturingContribution implements IntegrationToolProviderFactory {

    private ToolProviderFactoryContext seenContext;

    @Override
    public IntegrationType supportedType() {
      return IntegrationType.MCP_STDIO;
    }

    @Override
    public ToolProvider create(IntegrationDefinition definition,
        ToolProviderFactoryContext context) {
      this.seenContext = context;
      return mock(ToolProvider.class);
    }
  }

  private static final class OtherStdioContribution implements IntegrationToolProviderFactory {

    @Override
    public IntegrationType supportedType() {
      return IntegrationType.MCP_STDIO;
    }

    @Override
    public ToolProvider create(IntegrationDefinition definition,
        ToolProviderFactoryContext context) {
      throw new UnsupportedOperationException("not used");
    }
  }
}
