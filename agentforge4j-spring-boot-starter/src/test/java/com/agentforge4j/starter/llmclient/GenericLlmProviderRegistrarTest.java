// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;

class GenericLlmProviderRegistrarTest {

  private final Binder binder = Binder.get(new StandardEnvironment());
  private final BeanDefinitionRegistry registry = new DefaultListableBeanFactory();

  @Test
  void failsFastWhenTwoAdaptersClaimTheSameProviderId() {
    List<LlmClientConfigurationAdapter> adapters =
        List.of(new StubAdapter("dup", true), new StubAdapter("dup", true));

    assertThatThrownBy(() -> GenericLlmProviderRegistrar.register(adapters, binder, registry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dup");
  }

  @Test
  void registersOneBeanPerConfiguredAdapter() {
    List<LlmClientConfigurationAdapter> adapters =
        List.of(new StubAdapter("alpha", true), new StubAdapter("beta", true));

    GenericLlmProviderRegistrar.register(adapters, binder, registry);

    assertThat(registry.containsBeanDefinition("alphaLlmClientConfiguration")).isTrue();
    assertThat(registry.containsBeanDefinition("betaLlmClientConfiguration")).isTrue();
  }

  @Test
  void doesNotRegisterAnUnconfiguredAdapter() {
    GenericLlmProviderRegistrar.register(List.of(new StubAdapter("gamma", false)), binder, registry);

    assertThat(registry.containsBeanDefinition("gammaLlmClientConfiguration")).isFalse();
  }

  @Test
  void propagatesConfigurationExceptionFromIsConfigured() {
    // A misconfiguration surfaced by a non-boolean gate (getInt/getDuration/...) must fail context refresh, not be
    // silently swallowed as "provider absent".
    assertThatThrownBy(() -> GenericLlmProviderRegistrar.register(
        List.of(new ThrowingAdapter("boom")), binder, registry))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void registersSingleWordProviderUnderCamelCaseBeanName() {
    GenericLlmProviderRegistrar.register(List.of(new StubAdapter("openai", true)), binder, registry);

    assertThat(registry.containsBeanDefinition("openaiLlmClientConfiguration")).isTrue();
  }

  @Test
  void registersMultiWordProviderUnderCamelCaseBeanName() {
    GenericLlmProviderRegistrar.register(List.of(new StubAdapter("azure-openai", true)), binder, registry);

    assertThat(registry.containsBeanDefinition("azureOpenaiLlmClientConfiguration")).isTrue();
  }

  @Test
  void generatesUniqueBeanNamesForEveryDiscoveredProvider() {
    List<LlmClientConfigurationAdapter> stubs = new ArrayList<>();
    for (LlmClientConfigurationAdapter adapter : ServiceLoader.load(LlmClientConfigurationAdapter.class)) {
      stubs.add(new StubAdapter(adapter.providerId(), true));
    }

    GenericLlmProviderRegistrar.register(stubs, binder, registry);

    assertThat(registry.getBeanDefinitionCount())
        .as("each discovered provider id must map to a distinct bean name (no collisions)")
        .isEqualTo(stubs.size());
  }

  private record StubAdapter(String providerId, boolean configured)
      implements LlmClientConfigurationAdapter {

    @Override
    public boolean isConfigured(RawProviderConfiguration raw) {
      return configured;
    }

    @Override
    public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
      return new StubConfiguration(providerId);
    }
  }

  /** Stub whose activation gate fails for a non-boolean reason — the misconfiguration must propagate. */
  private record ThrowingAdapter(String providerId) implements LlmClientConfigurationAdapter {

    @Override
    public boolean isConfigured(RawProviderConfiguration raw) {
      throw new LlmProviderConfigurationException(
          "non-boolean gate misconfigured for provider '%s'".formatted(providerId));
    }

    @Override
    public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
      throw new AssertionError("adapt must not be called when isConfigured throws");
    }
  }

  private record StubConfiguration(String providerName) implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return providerName;
    }

    @Override
    public String getDefaultModel() {
      return "model";
    }

    @Override
    public Duration getConnectTimeout() {
      return Duration.ofSeconds(1);
    }
  }
}
