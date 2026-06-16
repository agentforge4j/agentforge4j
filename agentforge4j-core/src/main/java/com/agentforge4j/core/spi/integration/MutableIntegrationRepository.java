// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.integration;

/**
 * An {@link IntegrationRepository} that also supports administrative writes (add/replace, toggle
 * active, remove).
 */
public interface MutableIntegrationRepository extends IntegrationRepository {

  /**
   * Adds or replaces an integration.
   *
   * @param definition the integration to store
   */
  void save(IntegrationDefinition definition);

  /**
   * Activates or deactivates an integration.
   *
   * @param id     integration id
   * @param active new active state
   */
  void setActive(String id, boolean active);

  /**
   * Removes an integration.
   *
   * @param id integration id
   */
  void remove(String id);
}
