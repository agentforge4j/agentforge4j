// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.agentforge4j.config.loader.workflow.WorkflowBundleLocator;
import com.agentforge4j.llm.fake.FakeScriptParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Conformance gate for the plug-and-play scenario model. Each shipped workflow owns its verification
 * scenario locally under its own {@code <id>.workflow/verification/} folder; there is no central
 * registry. Two live sources are cross-checked: the shipped-workflow inventory read from
 * {@link WorkflowBundleLocator#shippedWorkflowIds()} (the very list the production loader drives) and
 * the scenario-owning folders discovered from the catalog tree by
 * {@link CatalogScenarios#scenarioOwningWorkflowIds()}. Coverage and orphan checks fall straight out
 * of comparing the two — adding a workflow obliges a colocated scenario, and removing a workflow
 * removes its scenario with it (an orphan survives only if a scenario folder outlives its index
 * entry).
 *
 * <p>Each scenario must carry a {@code README.md}, a parseable {@code script.json}, and an
 * {@code expected-result.json} naming the same workflow as the folder that owns it.
 *
 * <p>The clean-slate window ended with the first shipped workflow (workflow-execution-estimator):
 * the shipped index and scenario ownership are now asserted non-empty, and the coverage/orphan
 * checks below exercise the genuine cross-check, not a vacuous one.
 */
class CatalogConformanceTest {

  private static Set<String> shippedWorkflows() {
    return Set.copyOf(WorkflowBundleLocator.shippedWorkflowIds());
  }

  private static Set<String> scenarioOwners() {
    return CatalogScenarios.scenarioOwningWorkflowIds();
  }

  @Test
  void shippedCatalogOwnsTheExecutionEstimatorWorkflow() {
    assertThat(shippedWorkflows())
        .as("the shipped workflow index must enumerate the shipped catalog's workflows")
        .contains("workflow-execution-estimator");
  }

  @Test
  void scenariosExistForTheShippedWorkflow() {
    assertThat(CatalogScenarios.discover())
        .as("the shipped workflow must own at least one verification scenario")
        .isNotEmpty();
  }

  @Test
  void everyScenarioIsWellFormed() {
    List<ScenarioCase> cases = CatalogScenarios.discover();
    for (ScenarioCase scenario : cases) {
      assertThat(scenario.readmePresent())
          .as("scenario '%s' must carry a README.md", scenario.name()).isTrue();
      assertThat(scenario.expected().workflowId())
          .as("scenario '%s' expected-result.json must name the workflow its folder owns",
              scenario.name())
          .isEqualTo(scenario.owningWorkflowId());
      assertThatCode(() -> new FakeScriptParser().parse(scenario.scriptJson()))
          .as("scenario '%s' script.json must parse", scenario.name())
          .doesNotThrowAnyException();
    }
  }

  @Test
  void everyShippedWorkflowOwnsAScenario() {
    assertThat(scenarioOwners())
        .as("every shipped workflow must own a local verification scenario under its "
            + "<id>.workflow/verification/ folder — a newly shipped workflow with no colocated "
            + "scenario fails this gate (no skips, no holds)")
        .containsAll(shippedWorkflows());
  }

  @Test
  void noScenarioOrphansARemovedWorkflow() {
    assertThat(shippedWorkflows())
        .as("every owned scenario must belong to a real shipped workflow; an orphan means the "
            + "workflow was removed from the shipped index but its verification folder remains")
        .containsAll(scenarioOwners());
  }
}
