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
 * <p>During the clean-slate window the catalog ships no workflows, so discovery yields nothing and
 * this factory produces zero dynamic tests (which passes). Once a workflow ships a
 * {@code verification/<scenario>/} folder, its scenario is discovered and executed here with no
 * further wiring.
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
