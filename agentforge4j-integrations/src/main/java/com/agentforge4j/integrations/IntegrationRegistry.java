package com.agentforge4j.integrations;

import java.util.Optional;

/**
 * Resolves {@link AgentIntegration} instances and answers enablement and permission checks keyed by
 * integration id. The workflow runtime consults this when executing integration-related commands.
 * Use {@link NoOpIntegrationRegistry} when no integrations are configured.
 */
public interface IntegrationRegistry {

  /**
   * Looks up an integration by id when the registry exposes it for use.
   *
   * @param integrationId stable key from workflow or agent configuration
   * @return the integration when available for the current deployment, otherwise empty
   */
  Optional<AgentIntegration> resolve(String integrationId);

  /**
   * Reports whether the named operation may run for the integration id.
   *
   * @param integrationId stable key from workflow or agent configuration
   * @param operation     logical operation name understood by the integration
   * @return {@code true} when the integration is available and permits {@code operation}
   */
  boolean isOperationAllowed(String integrationId, String operation);

  /**
   * Reports whether the integration id is available for use in the current deployment.
   *
   * @param integrationId stable key from workflow or agent configuration
   * @return {@code true} when configuration is present, {@link IntegrationConfig#enabled()} is
   * {@code true}, and a matching integration is registered
   */
  boolean isEnabled(String integrationId);
}
