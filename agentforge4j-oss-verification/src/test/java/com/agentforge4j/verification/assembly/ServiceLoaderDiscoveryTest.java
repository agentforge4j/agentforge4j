// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.assembly;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.TokenEstimatorResolver;
import com.agentforge4j.llm.api.TokenEstimator;
import com.agentforge4j.verification.support.FixedCountTokenEstimator;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves provider/estimator SPI seams: {@link LlmClientFactory} and {@link TokenEstimator}
 * implementations are discovered via {@link ServiceLoader} from {@code META-INF/services}. On this
 * module's classpath only the fake registrations are present, so discovery must surface exactly them —
 * confirming the declarative registration mechanism that real providers (openai/claude/...) and
 * provider-supplied token estimators rely on.
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

  @Test
  void registeredTokenEstimatorIsDiscoveredViaServiceLoader() {
    // Unlike TokenEstimatorResolverTest (which only proves the no-provider fallback), this module's
    // classpath registers FixedCountTokenEstimator via META-INF/services, so resolution must return it
    // rather than falling back to DefaultTokenEstimator.
    TokenEstimator estimator = TokenEstimatorResolver.resolve();

    assertThat(estimator)
        .as("the registered FixedCountTokenEstimator must be ServiceLoader-discoverable and preferred "
            + "over the shipped default")
        .isInstanceOf(FixedCountTokenEstimator.class);
    assertThat(estimator.estimate("anything")).isEqualTo(FixedCountTokenEstimator.FIXED_COUNT);
  }
}
