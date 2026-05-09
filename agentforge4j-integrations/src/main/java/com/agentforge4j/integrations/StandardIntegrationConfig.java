package com.agentforge4j.integrations;

import java.util.List;

/**
 * Immutable enable flag and allowed-operation list for one integration id.
 * <p>
 * When {@code enabled} is {@code true}, the compact constructor invokes {@link #validate()} and
 * stores an unmodifiable copy of {@code allowedOperations}, or {@link List#of()} when the list is
 * {@code null}. When {@code enabled} is {@code false}, {@link #validate()} is not invoked and
 * {@code allowedOperations} is left as supplied (may be {@code null}).
 *
 * @param enabled whether the integration id is treated as enabled
 * @param allowedOperations operation names permitted when {@code enabled}; ignored for defensive
 * copying when {@code enabled} is {@code false}
 */
public record StandardIntegrationConfig(
    boolean enabled,
    List<String> allowedOperations
) implements IntegrationConfig {

  public StandardIntegrationConfig {
    if (enabled) {
      validate();
      allowedOperations = allowedOperations != null
          ? List.copyOf(allowedOperations)
          : List.of();
    }
  }

  /**
   * No-op; permits any state for this record.
   */
  @Override
  public void validate() {
  }
}
