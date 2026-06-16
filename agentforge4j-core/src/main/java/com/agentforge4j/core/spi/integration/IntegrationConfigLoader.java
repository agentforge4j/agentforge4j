// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.integration;

import java.util.List;

/**
 * Loads {@link IntegrationDefinition}s from an external source (for example JSON definition files).
 * Pure contract; implementations live downstream.
 */
public interface IntegrationConfigLoader {

  /**
   * Loads the configured integration definitions.
   *
   * @return the loaded definitions; never {@code null}
   */
  List<IntegrationDefinition> load();
}
