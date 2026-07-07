// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the generic 1:N scenario discovery against a fixture tree under a dedicated classpath
 * root, so the assertions do not depend on (and cannot pollute) the real {@code /shipped-workflows}
 * catalog. Proves a single workflow folder can own several {@code verification/<scenario>/} folders.
 */
class CatalogScenariosDiscoveryTest {

  private static final String FIXTURE_ROOT = "/scenario-discovery-fixtures";

  @Test
  void discoversEveryScenarioSubfolderPerWorkflow() {
    List<ScenarioCase> cases = CatalogScenarios.discoverFrom(FIXTURE_ROOT);

    assertThat(cases).extracting(ScenarioCase::name)
        .containsExactly("alpha/happy", "alpha/sad", "beta/only");
    assertThat(cases).extracting(ScenarioCase::owningWorkflowId)
        .containsExactly("alpha", "alpha", "beta");
    assertThat(cases).allSatisfy(scenario -> assertThat(scenario.readmePresent()).isTrue());
    assertThat(cases.get(0).expected().workflowId()).isEqualTo("alpha");
  }

  @Test
  void owningWorkflowIdsAreDeduplicated() {
    assertThat(CatalogScenarios.owningWorkflowIds(FIXTURE_ROOT))
        .containsExactlyInAnyOrder("alpha", "beta");
  }

  @Test
  void absentRootYieldsNoScenarios() {
    assertThat(CatalogScenarios.discoverFrom("/no-such-root")).isEmpty();
    assertThat(CatalogScenarios.owningWorkflowIds("/no-such-root")).isEmpty();
  }
}
