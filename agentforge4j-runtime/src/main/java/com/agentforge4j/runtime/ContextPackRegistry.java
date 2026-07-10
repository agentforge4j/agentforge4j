// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.util.Validate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Run-scoped, immutable lookup of loaded {@link ContextPack}s by name. Packs are immutable per run,
 * so one registry instance is safe to share for a whole run's lifetime.
 */
public final class ContextPackRegistry {

  /**
   * Empty registry, used when no context packs are configured. Every lookup resolves to empty.
   */
  public static final ContextPackRegistry EMPTY = new ContextPackRegistry(Map.of());

  private final Map<String, ContextPack> byName;

  private ContextPackRegistry(Map<String, ContextPack> byName) {
    this.byName = Map.copyOf(byName);
  }

  /**
   * Builds a registry from loaded packs.
   *
   * @param packs the loaded packs; must not be {@code null}
   *
   * @return the registry; never {@code null}
   *
   * @throws IllegalArgumentException if two packs share a name
   */
  public static ContextPackRegistry of(List<ContextPack> packs) {
    Validate.notNull(packs, "packs must not be null");
    Map<String, ContextPack> byName = new LinkedHashMap<>();
    for (ContextPack pack : packs) {
      ContextPack previous = byName.putIfAbsent(pack.name(), pack);
      Validate.isTrue(previous == null,
          "Duplicate context pack name '%s'".formatted(pack.name()));
    }
    return new ContextPackRegistry(byName);
  }

  /**
   * Looks up a pack by name.
   *
   * @param name the pack name; must not be blank
   *
   * @return the pack, or empty when no pack with that name is registered
   */
  public Optional<ContextPack> get(String name) {
    Validate.notBlank(name, "name must not be blank");
    return Optional.ofNullable(byName.get(name));
  }

  /**
   * Returns the names of every registered pack.
   *
   * @return an immutable set of pack names; never {@code null}
   */
  public Set<String> names() {
    return byName.keySet();
  }
}
