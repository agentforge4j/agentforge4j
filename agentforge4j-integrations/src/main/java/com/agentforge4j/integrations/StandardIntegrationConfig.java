package com.agentforge4j.integrations;

import java.util.List;

/**
 * Immutable enable flag and allowed-operation list for one integration id.
 * <p>
 * The compact constructor defaults {@code allowedOperations} to {@link List#of()} when
 * {@code null}, stores an unmodifiable copy otherwise, and invokes {@link #validate()}.
 *
 * @param enabled whether the integration id is treated as enabled
 * @param allowedOperations operation names permitted when enabled; never {@code null} after
 *                          construction
 */
public record StandardIntegrationConfig(
    boolean enabled,
    List<String> allowedOperations
) implements IntegrationConfig {

  public StandardIntegrationConfig {
    List<String> normalized;
    if (allowedOperations == null) {
      normalized = List.of();
    } else {
      rejectInvalidEntries(allowedOperations);
      normalized = List.copyOf(allowedOperations);
    }
    allowedOperations = normalized;
  }

  @Override
  public void validate() {
    rejectInvalidEntries(allowedOperations);
  }

  private static void rejectInvalidEntries(List<String> allowedOperations) {
    for (String operation : allowedOperations) {
      if (operation == null || operation.isBlank()) {
        throw new IllegalArgumentException(
            "allowedOperations must not contain null or blank entries");
      }
    }
  }
}
