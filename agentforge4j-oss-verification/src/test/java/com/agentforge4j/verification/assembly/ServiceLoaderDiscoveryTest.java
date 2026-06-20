// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.assembly;

import com.agentforge4j.llm.LlmClientFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the provider SPI seam: {@link LlmClientFactory} implementations are discovered via
 * {@link ServiceLoader} from {@code META-INF/services}. On this module's classpath only the fake
 * provider is present, so discovery must surface exactly the {@code fake} factory — confirming the
 * declarative registration mechanism that real providers (openai/claude/...) rely on.
 */
class ServiceLoaderDiscoveryTest {

  @Test
  void fakeProviderFactoryIsDiscoveredViaServiceLoader() {
    List<String> providerNames = new ArrayList<>();
    for (LlmClientFactory factory : ServiceLoader.load(LlmClientFactory.class)) {
      providerNames.add(factory.getProviderName());
    }

    assertThat(providerNames)
        .as("the fake LlmClientFactory must be ServiceLoader-discoverable on the classpath")
        .contains("fake");
  }
}
