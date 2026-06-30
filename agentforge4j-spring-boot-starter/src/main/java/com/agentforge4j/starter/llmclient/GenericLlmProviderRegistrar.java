// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.RawConfigurationSource;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * Registers one {@link LlmClientConfiguration} bean per configured provider by discovering every
 * {@link LlmClientConfigurationAdapter} on the classpath (mirroring the {@code LlmClientFactory} ServiceLoader), binding
 * its {@code agentforge4j.llm.<providerId>.*} subtree, and asking the adapter to map it. Registering individual beans
 * matches the channel {@code BootstrapAutoConfiguration} consumes (a collected {@code List<LlmClientConfiguration>}), so
 * it coexists with any provider still on its legacy per-provider auto-config during migration.
 *
 * <p>Two adapters claiming the same provider id fail fast. A provider is migrated atomically (its adapter is added in
 * the same change its legacy trio is removed), so no provider id is ever served by both an adapter and a legacy bean.
 *
 * <p>Each key is resolved through a scalar {@link Binder} lookup of its fully-qualified name, so Spring's relaxed
 * binding applies and kebab-case, camelCase, and environment-variable forms all activate a provider identically — the
 * same compatibility the former per-provider {@code @ConfigurationProperties} records provided. (A map bind of the
 * subtree cannot reconstruct the canonical kebab key from a relaxed source, so it is used only to enumerate present
 * keys, never to resolve values.)
 *
 * <p>Activation reproduces the former per-provider {@code @ConditionalOnProperty} gates. The provider adapter declares
 * <em>which</em> key it gates on; the two compatibility rules sit where each naturally belongs:
 * <ul>
 *   <li>a presence-guarded key (for example {@code api-key} or {@code url}) configured as {@code "false"}
 *       (case-insensitive) counts as absent and does not activate — the default {@code @ConditionalOnProperty} (no
 *       {@code havingValue}) behaviour, applied here by the {@link #bindActivationSubtree activation view};</li>
 *   <li>an {@code enabled}-style flag activates only for {@code "true"} (case-insensitive); the adapter gates on
 *       {@link RawProviderConfiguration#isTrue}, which tolerates any other value ({@code yes}, {@code on}, {@code 1},
 *       blank, or otherwise malformed) as not-activated without throwing — matching
 *       {@code @ConditionalOnProperty(havingValue = "true")}.</li>
 * </ul>
 * These rules apply only to the activation decision; {@link LlmClientConfigurationAdapter#adapt} still sees the
 * unfiltered subtree when mapping values. A genuine misconfiguration surfaced by a non-gate accessor (for example a
 * malformed {@code getInt}/{@code getDuration}) is not caught here and fails context refresh, as it should.
 */
final class GenericLlmProviderRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware,
    BeanClassLoaderAware {

  private static final String PREFIX = "agentforge4j.llm.";

  private Environment environment;
  private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void setBeanClassLoader(ClassLoader beanClassLoader) {
    this.beanClassLoader = beanClassLoader;
  }

  @Override
  public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    register(ServiceLoader.load(LlmClientConfigurationAdapter.class, beanClassLoader),
        Binder.get(environment), registry);
  }

  /**
   * Binds each adapter's subtree and registers an individual {@link LlmClientConfiguration} bean for every configured
   * provider. Package-private for unit testing of the duplicate-provider guard without ServiceLoader pollution.
   *
   * @param adapters the discovered adapters
   * @param binder   the property binder over the application environment
   * @param registry the bean definition registry
   *
   * @throws IllegalStateException if two adapters claim the same provider id
   */
  static void register(Iterable<LlmClientConfigurationAdapter> adapters, Binder binder,
      BeanDefinitionRegistry registry) {
    Set<String> seenProviderIds = new HashSet<>();
    for (LlmClientConfigurationAdapter adapter : adapters) {
      String providerId = adapter.providerId();
      if (!seenProviderIds.add(providerId)) {
        throw new IllegalStateException(
            "Duplicate LlmClientConfigurationAdapter for provider id '%s'".formatted(providerId));
      }
      if (!isActivated(adapter, binder, providerId)) {
        continue;
      }
      LlmClientConfiguration configuration = adapter.adapt(bindSubtree(binder, providerId));
      AbstractBeanDefinition definition = BeanDefinitionBuilder
          .genericBeanDefinition(LlmClientConfiguration.class, () -> configuration)
          .getBeanDefinition();
      registry.registerBeanDefinition(beanName(providerId), definition);
    }
  }

  /**
   * Decides whether a provider activates, reproducing its former {@code @ConditionalOnProperty} gate (see the class
   * documentation). The adapter's {@link LlmClientConfigurationAdapter#isConfigured} declares the gating key; this method
   * supplies the Spring-compatible interpretation by binding the {@link #bindActivationSubtree activation view} of the
   * subtree, where a presence guard configured as {@code "false"} reads as absent. A
   * {@link com.agentforge4j.llm.LlmProviderConfigurationException} from {@code isConfigured} is a genuine
   * misconfiguration and is left to propagate (failing context refresh); the {@code enabled}-style tolerance lives in
   * {@link RawProviderConfiguration#isTrue}, not here.
   *
   * @param adapter    the provider adapter
   * @param binder     the property binder over the application environment
   * @param providerId the provider id whose subtree gates activation
   *
   * @return {@code true} when the provider should be registered
   */
  private static boolean isActivated(LlmClientConfigurationAdapter adapter, Binder binder, String providerId) {
    return adapter.isConfigured(bindActivationSubtree(binder, providerId));
  }

  /**
   * Builds the {@link RawProviderConfiguration} for a provider. Values are resolved per key through a scalar relaxed
   * {@link Binder} lookup of the fully-qualified name (so every relaxed form binds), while a map bind of the subtree
   * supplies the best-effort set of present keys for the enumeration views.
   */
  private static RawProviderConfiguration bindSubtree(Binder binder, String providerId) {
    String subtree = PREFIX + providerId;
    Map<String, String> enumeratedKeys = binder.bind(subtree, Bindable.mapOf(String.class, String.class))
        .orElseGet(Map::of);
    RawConfigurationSource source =
        key -> binder.bind(subtree + "." + key, Bindable.of(String.class)).orElse(null);
    return new RawProviderConfiguration(providerId, source, enumeratedKeys.keySet());
  }

  /**
   * Builds the activation view of a provider's subtree: identical to {@link #bindSubtree} except a bound value of
   * {@code "false"} (case-insensitive) is treated as absent — both excluded from the present-key enumeration and
   * resolved as {@code null} — so a presence-guarded provider configured with {@code <key>=false} stays inactive. This
   * is the default {@code @ConditionalOnProperty} (no {@code havingValue}) semantics and is applied only to the
   * activation decision, never to value mapping in {@link LlmClientConfigurationAdapter#adapt}.
   */
  private static RawProviderConfiguration bindActivationSubtree(Binder binder, String providerId) {
    String subtree = PREFIX + providerId;
    Map<String, String> enumeratedKeys = binder.bind(subtree, Bindable.mapOf(String.class, String.class))
        .orElseGet(Map::of);
    Set<String> presentKeys = new LinkedHashSet<>();
    for (Map.Entry<String, String> entry : enumeratedKeys.entrySet()) {
      if (!isDisablingFalse(entry.getValue())) {
        presentKeys.add(entry.getKey());
      }
    }
    RawConfigurationSource source = key -> {
      String value = binder.bind(subtree + "." + key, Bindable.of(String.class)).orElse(null);
      return isDisablingFalse(value) ? null : value;
    };
    return new RawProviderConfiguration(providerId, source, presentKeys);
  }

  /**
   * @return {@code true} when {@code value} is the literal {@code "false"} (case-insensitive), the one present value the
   *     default {@code @ConditionalOnProperty} treats as not-matched
   */
  private static boolean isDisablingFalse(String value) {
    return value != null && value.equalsIgnoreCase("false");
  }

  /**
   * Converts a kebab-case provider id into an idiomatic Spring bean name — for example {@code azure-openai} →
   * {@code azureOpenaiLlmClientConfiguration}, {@code openai} → {@code openaiLlmClientConfiguration}.
   * Deterministic and collision-free for any two distinct provider ids: dash-removal capitalization with no
   * merging step, so distinct inputs cannot produce the same output.
   */
  private static String beanName(String providerId) {
    StringBuilder name = new StringBuilder(providerId.length());
    boolean capitalizeNext = false;
    for (int i = 0; i < providerId.length(); i++) {
      char c = providerId.charAt(i);
      if (c == '-') {
        capitalizeNext = true;
        continue;
      }
      name.append(capitalizeNext ? Character.toUpperCase(c) : c);
      capitalizeNext = false;
    }
    return name.append("LlmClientConfiguration").toString();
  }
}
