package com.agentforge4j.integrations;

import java.util.Optional;

/**
 * {@link IntegrationRegistry} that exposes no integrations: {@link #resolve(String)} is always
 * empty and permission checks always return {@code false}. Safe default when integrations are not
 * configured.
 */
public final class NoOpIntegrationRegistry implements IntegrationRegistry {

  /**
   * Shared singleton instance.
   */
  public static final NoOpIntegrationRegistry INSTANCE = new NoOpIntegrationRegistry();

  private NoOpIntegrationRegistry() {
  }

  /**
   * {@inheritDoc}
   *
   * @param integrationId ignored
   */
  @Override
  public Optional<AgentIntegration> resolve(String integrationId) {
    return Optional.empty();
  }

  /**
   * {@inheritDoc}
   *
   * @param integrationId ignored
   * @param operation     ignored
   */
  @Override
  public boolean isOperationAllowed(String integrationId, String operation) {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @param integrationId ignored
   */
  @Override
  public boolean isEnabled(String integrationId) {
    return false;
  }
}
