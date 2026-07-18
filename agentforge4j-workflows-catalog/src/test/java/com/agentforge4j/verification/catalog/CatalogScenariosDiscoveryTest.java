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

  @Test
  void markerlessScenarioFolderIsNotDiscoveredButIsReported() {
    // alpha/broken carries script.json + README.md but no expected-result.json: discovery must
    // skip it (proven by discoversEveryScenarioSubfolderPerWorkflow's exact list) and the inverse
    // check must surface it so the conformance gate can fail instead of silently dropping a test.
    assertThat(CatalogScenarios.discoverFrom(FIXTURE_ROOT))
        .extracting(ScenarioCase::name)
        .doesNotContain("alpha/broken");
    assertThat(CatalogScenarios.unmarkedScenarioFolders(FIXTURE_ROOT))
        .containsExactly("alpha/broken");
  }

  @Test
  void physicalWorkflowFoldersAreEnumeratedRegardlessOfScenarioContent() {
    assertThat(CatalogScenarios.physicalWorkflowFolderIds(FIXTURE_ROOT))
        .containsExactlyInAnyOrder("alpha", "beta");
    assertThat(CatalogScenarios.physicalWorkflowFolderIds("/no-such-root")).isEmpty();
    assertThat(CatalogScenarios.unmarkedScenarioFolders("/no-such-root")).isEmpty();
  }
}
