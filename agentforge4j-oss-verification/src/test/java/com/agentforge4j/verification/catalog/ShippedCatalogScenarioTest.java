// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives every data-driven shipped-catalog scenario through the harness and asserts its expected
 * result. Tier 1: proves the genuine shipped workflows are executable end-to-end, not merely
 * loadable.
 */
class ShippedCatalogScenarioTest {

  static List<ScenarioCase> scenarios() {
    return CatalogScenarios.discover();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  void scenarioReachesExpectedResult(ScenarioCase scenario) {
    WorkflowRunResult result = CatalogScenarios.run(scenario);
    CatalogScenarios.assertExpectations(result, scenario.expected().expect());
  }
}
