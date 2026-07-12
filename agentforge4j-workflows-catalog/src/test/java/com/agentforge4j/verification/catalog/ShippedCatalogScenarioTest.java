// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Drives every data-driven shipped-catalog scenario through the harness and asserts its expected
 * result (Tier 1: proves the genuine shipped workflows are executable end-to-end, not merely
 * loadable). A workflow may own several scenarios; each is driven as its own dynamic test.
 *
 * <p>Each shipped workflow that owns a {@code verification/<scenario>/} folder is discovered and
 * executed here with no further wiring — {@code workflow-execution-estimator} contributes seven
 * scenarios and {@code agent-creator} contributes fifteen. A catalog with no shipped workflows at
 * all would make this factory produce zero dynamic tests (which passes vacuously); that is no
 * longer the state of this module.
 */
class ShippedCatalogScenarioTest {

  @TestFactory
  Stream<DynamicTest> shippedCatalogScenariosMeetTheirExpectations() {
    return CatalogScenarios.discover().stream()
        .map(scenario -> dynamicTest(scenario.name(), () -> {
          WorkflowRunResult result = CatalogScenarios.run(scenario);
          CatalogScenarios.assertExpectations(result, scenario.expected().expect());
        }));
  }
}
