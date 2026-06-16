// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.util.Validate;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * In-memory {@link ModelTierResolver} backed by a static provider→tier→model map. Used as the OSS
 * default resolver: the shipped defaults from {@link ShippedModelTierDefaults}, optionally merged
 * with operator-supplied overrides.
 *
 * <p>Resolution is a pure map lookup; a provider/tier combination with no entry returns
 * {@code null}, leaving the decision to throw to the runtime.
 */
public final class ConfigModelTierResolver implements ModelTierResolver {

  private final Map<String, Map<ModelTier, String>> mappings;

  /**
   * Creates a resolver over the given provider→tier→model map. The map is deep-copied with provider
   * keys normalized to lowercase; {@code null} model values are dropped.
   *
   * @param mappings provider name to (tier to model) mappings; must not be {@code null}
   */
  public ConfigModelTierResolver(Map<String, Map<ModelTier, String>> mappings) {
    Validate.notNull(mappings, "mappings must not be null");
    this.mappings = deepCopyNormalized(mappings);
  }

  /**
   * Creates a resolver over the shipped OSS defaults only.
   *
   * @return resolver backed by {@link ShippedModelTierDefaults}; never {@code null}
   */
  public static ConfigModelTierResolver withShippedDefaults() {
    return new ConfigModelTierResolver(ShippedModelTierDefaults.asMap());
  }

  /**
   * Creates a resolver over the shipped OSS defaults with operator overrides merged on top
   * (override entries win per provider/tier). A {@code null} or empty overrides map yields the
   * shipped defaults unchanged.
   *
   * @param overrides operator-supplied provider→tier→model overrides; may be {@code null}
   *
   * @return resolver backed by the merged map; never {@code null}
   */
  public static ConfigModelTierResolver withShippedDefaultsAndOverrides(
      Map<String, Map<ModelTier, String>> overrides) {
    Map<String, Map<ModelTier, String>> merged = ShippedModelTierDefaults.asMap();
    if (overrides != null) {
      overrides.entrySet().stream()
          .filter(entry -> entry.getValue() != null)
          .forEach(entry -> addTierModel(entry, merged));
    }
    return new ConfigModelTierResolver(merged);
  }

  private static void addTierModel(Entry<String, Map<ModelTier, String>> providerEntry,
      Map<String, Map<ModelTier, String>> merged) {
    Map<ModelTier, String> target = merged.computeIfAbsent(
        providerEntry.getKey(), key -> new EnumMap<>(ModelTier.class));
    providerEntry.getValue().entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .forEach(entry -> target.put(entry.getKey(), entry.getValue()));
  }

  @Override
  public String resolve(String provider, ModelTier tier) {
    Validate.notBlank(provider, "provider must not be blank");
    Validate.notNull(tier, "tier must not be null");
    Map<ModelTier, String> byTier = mappings.get(normalize(provider));
    if (byTier == null) {
      return null;
    }
    return byTier.get(tier);
  }

  private static Map<String, Map<ModelTier, String>> deepCopyNormalized(
      Map<String, Map<ModelTier, String>> source) {
    Map<String, Map<ModelTier, String>> copy = new HashMap<>();
    for (Map.Entry<String, Map<ModelTier, String>> entry : source.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      Map<ModelTier, String> byTier = new EnumMap<>(ModelTier.class);
      entry.getValue().entrySet().stream()
          .filter(tierEntry -> tierEntry.getKey() != null && tierEntry.getValue() != null)
          .forEach(tierEntry -> byTier.put(tierEntry.getKey(), tierEntry.getValue()));
      copy.put(normalize(entry.getKey()), byTier);
    }
    return Map.copyOf(copy);
  }

  private static String normalize(String provider) {
    return provider.trim().toLowerCase(Locale.ROOT);
  }
}
