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
 * <p>During the clean-slate window — the catalog has been wiped and PR B has not yet re-added a
 * workflow — the catalog is intentionally empty: the emptiness is asserted directly and the
 * coverage/orphan checks hold vacuously. PR B restores the non-empty assertions when it ships a
 * workflow.
 */
class CatalogConformanceTest {

  private static Set<String> shippedWorkflows() {
    return Set.copyOf(WorkflowBundleLocator.shippedWorkflowIds());
  }

  private static Set<String> scenarioOwners() {
    return CatalogScenarios.scenarioOwningWorkflowIds();
  }

  @Test
  void shippedCatalogIsEmptyDuringCleanSlate() {
    // Clean-slate window: the catalog has been wiped and PR B has not yet re-added a workflow, so the
    // shipped index must enumerate nothing. PR B restores the non-empty assertion when it ships one.
    assertThat(shippedWorkflows())
        .as("during the clean-slate window the shipped workflow index must be empty")
        .isEmpty();
  }

  @Test
  void noScenariosExistDuringCleanSlate() {
    // Clean-slate window: with no shipped workflows there are no owned verification scenarios. PR B
    // restores the at-least-one-scenario assertion when it ships a workflow.
    assertThat(CatalogScenarios.discover())
        .as("during the clean-slate window no verification scenarios are owned")
        .isEmpty();
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
          .isEqualTo(scenario.name());
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
