// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmClientFactory;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Anti-regression seam for the original finding: a ServiceLoader-registered {@link LlmClientFactory} that is not
 * configurable through the starter must fail the build. Every discovered factory must have a matching
 * {@link LlmClientConfigurationAdapter}, except for two explicit, named sets:
 *
 * <ul>
 *   <li>{@link #ADAPTERLESS_FACTORIES} — providers intentionally and permanently off the generic property-adapter path
 *       (the fake test fixture is wired programmatically, not from properties). A second entry here is a conscious,
 *       reviewable edit, never a silent category bypass — its exact membership is asserted.</li>
 *   <li>{@link #PENDING_MIGRATION} — real providers not yet ported to the generic path during the staged migration.
 *       This set shrinks each phase and MUST be empty once migration completes.</li>
 * </ul>
 */
class LlmProviderAdapterParityTest {

  /** Providers intentionally and permanently configured without a property adapter. */
  private static final Set<String> ADAPTERLESS_FACTORIES = Set.of("fake");

  /** Real providers awaiting migration to the generic adapter path; empty now that migration is complete. */
  private static final Set<String> PENDING_MIGRATION = Set.of();

  @Test
  void adapterlessAllowlistMembershipIsExact() {
    assertThat(ADAPTERLESS_FACTORIES).containsExactly("fake");
  }

  @Test
  void everyFactoryHasAnAdapterUnlessExplicitlyExempt() {
    Set<String> factoryIds = providerIds(ServiceLoader.load(LlmClientFactory.class),
        LlmClientFactory::getProviderName);
    Set<String> adapterIds = providerIds(ServiceLoader.load(LlmClientConfigurationAdapter.class),
        LlmClientConfigurationAdapter::providerId);

    Set<String> unbacked = new HashSet<>(factoryIds);
    unbacked.removeAll(adapterIds);
    unbacked.removeAll(ADAPTERLESS_FACTORIES);
    unbacked.removeAll(PENDING_MIGRATION);

    assertThat(unbacked)
        .as("every LlmClientFactory must have an LlmClientConfigurationAdapter unless exempted")
        .isEmpty();
  }

  @Test
  void noAdapterWithoutAMatchingFactory() {
    Set<String> factoryIds = providerIds(ServiceLoader.load(LlmClientFactory.class),
        LlmClientFactory::getProviderName);
    Set<String> adapterIds = providerIds(ServiceLoader.load(LlmClientConfigurationAdapter.class),
        LlmClientConfigurationAdapter::providerId);

    assertThat(adapterIds).isSubsetOf(factoryIds);
  }

  @Test
  void migrationSetsAreConsistentWithDiscoveredFactories() {
    Set<String> factoryIds = providerIds(ServiceLoader.load(LlmClientFactory.class),
        LlmClientFactory::getProviderName);

    Set<String> overlap = new HashSet<>(ADAPTERLESS_FACTORIES);
    overlap.retainAll(PENDING_MIGRATION);
    assertThat(overlap).as("a provider cannot be both permanently adapterless and pending migration").isEmpty();
    assertThat(factoryIds).containsAll(PENDING_MIGRATION);
    assertThat(factoryIds).containsAll(ADAPTERLESS_FACTORIES);
  }

  private static <T> Set<String> providerIds(ServiceLoader<T> loader,
      java.util.function.Function<T, String> id) {
    return StreamSupport.stream(loader.spliterator(), false).map(id).collect(Collectors.toSet());
  }
}
