package com.agentforge4j.core.spi.integration;

import java.util.List;

/**
 * Read access to the configured integrations that feed capability resolution. This is the single
 * source of integrations for the resolver. Implementations live downstream (OSS loader/in-memory;
 * or persistence-backed in an embedding application).
 */
public interface IntegrationRepository {

  /**
   * Returns every active integration.
   *
   * @return the active integrations; never {@code null}
   */
  List<IntegrationDefinition> findActive();

  /**
   * Looks up an integration by id.
   *
   * @param id integration id
   *
   * @return the integration with this id, or {@code null} if none is registered
   */
  IntegrationDefinition findById(String id);
}
