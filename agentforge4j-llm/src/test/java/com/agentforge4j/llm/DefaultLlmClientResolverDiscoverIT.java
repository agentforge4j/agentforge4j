// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DefaultLlmClientResolver#discover} using real {@link java.util.ServiceLoader} wiring (no
 * mocks). Stub factories are registered under {@code META-INF/services}.
 */
class DefaultLlmClientResolverDiscoverIT {

  private static final LlmSecretResolver TEST_RESOLVER = reference -> new LlmSecret(reference.literalValue());

  @Disabled
  @Test
  void discover_instantiates_configured_spi_factory_and_execute_works() {
    ObjectMapper mapper = new ObjectMapper();
    Collection<LlmClientConfiguration> configs = List.of(
        TestFixtures.testConfig(ServiceLoaderStubLlmClientFactory.PROVIDER, "stub-model"));

    DefaultLlmClientResolver resolver = DefaultLlmClientResolver.discover(mapper, configs, TEST_RESOLVER);

    LlmClient client = resolver.resolve(ServiceLoaderStubLlmClientFactory.PROVIDER);
    assertThat(client.getProviderName()).isEqualTo(ServiceLoaderStubLlmClientFactory.PROVIDER);

    LlmExecutionResponse result = client.execute(
        new LlmExecutionRequest(ServiceLoaderStubLlmClientFactory.PROVIDER, null, "sys",
            "ping", null, null, null));

    assertThat(result.text()).isEqualTo("stub:ping");
    assertThat(result.tokenUsage()).isNull();
  }

  @Disabled
  @Test
  void discover_skips_spi_factories_that_have_no_configuration_entry() {
    ObjectMapper mapper = new ObjectMapper();
    Collection<LlmClientConfiguration> configs = List.of(
        TestFixtures.testConfig(ServiceLoaderStubLlmClientFactory.PROVIDER, "stub-model"));

    DefaultLlmClientResolver resolver = DefaultLlmClientResolver.discover(mapper, configs, TEST_RESOLVER);

    assertThatThrownBy(() -> resolver.resolve(OrphanDiscoverLlmClientFactory.PROVIDER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown LLM providerName");
  }
}
