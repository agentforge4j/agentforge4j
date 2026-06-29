// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Drives every data-driven shipped-catalog scenario through the harness and asserts its expected
 * result (Tier 1: proves the genuine shipped workflows are executable end-to-end, not merely
 * loadable).
 *
 * <p>During the clean-slate window the catalog ships no workflows, so there are no data-driven
 * scenarios to drive; this asserts the catalog owns none. PR B restores the parameterized
 * per-scenario execution when it ships a workflow with a verification scenario.
 */
class ShippedCatalogScenarioTest {

  @Test
  void noShippedScenariosRunDuringCleanSlate() {
    assertThat(CatalogScenarios.discover())
        .as("during the clean-slate window no shipped-catalog scenarios are executable")
        .isEmpty();
  }
}
