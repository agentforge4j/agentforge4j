// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.LlmSecretResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeProviderServiceLoaderTest {

  private static final LlmSecretResolver TEST_RESOLVER =
      reference -> new LlmSecret(reference.literalValue());

  @Test
  void fakeProvider_isDiscoveredAndResolved_viaProductionServiceLoaderPath() {
    FakeResponseSource source = new RegistryFakeResponseSource(new FakeRunLifecycle());

    // DefaultLlmClientResolver.discover performs the real ServiceLoader<LlmClientFactory> lookup and
    // pairs the discovered factory with the supplied FakeConfiguration by provider id.
    DefaultLlmClientResolver resolver = DefaultLlmClientResolver.discover(
        new ObjectMapper(), List.of(new FakeConfiguration(source)), TEST_RESOLVER);

    assertThat(resolver.isProviderAvailable("fake")).isTrue();
    LlmClient client = resolver.resolve("fake");
    assertThat(client).isInstanceOf(FakeLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("fake");
  }
}
