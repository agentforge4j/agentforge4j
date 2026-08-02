// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.agentforge4j.config.loader.agent.AgentBundleLocator;
import com.agentforge4j.config.loader.workflow.WorkflowBundleLocator;
import com.agentforge4j.llm.fake.FakeScriptParser;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
 * {@code expected-result.json} naming the same workflow as the folder that owns it and asserting at
 * least the run's final status — an assertion-free scenario would otherwise pass even over a run
 * that genuinely fails, so the floor is enforced here, not left to convention.
 *
 * <p>The discovery-integrity gates (physical-folder-vs-index, stray root entries) also cover the
 * sibling {@code /shipped-agents} root, whose {@code index} drives the production agent loader via
 * {@link AgentBundleLocator#shippedAgentIds()} — an unindexed {@code .agent} folder would otherwise
 * ship as the same silent dead cargo the workflow-root gates close.
 */
class CatalogConformanceTest {

  private static Set<String> shippedWorkflows() {
    return Set.copyOf(WorkflowBundleLocator.shippedWorkflowIds());
  }

  private static Set<String> scenarioOwners() {
    return CatalogScenarios.scenarioOwningWorkflowIds();
  }

  // The agent loader accepts index entries with or without the .agent suffix, so normalise the
  // same way before comparing against physical folder ids.
  private static Set<String> shippedAgents() {
    return AgentBundleLocator.shippedAgentIds().stream()
        .map(id -> id.endsWith(".agent") ? id.substring(0, id.length() - ".agent".length()) : id)
        .collect(Collectors.toSet());
  }

  @Test
  void shippedCatalogOwnsBothWorkflows() {
    assertThat(shippedWorkflows())
        .as("the shipped workflow index must enumerate the shipped catalog's workflows")
        .contains("agent-creator", "workflow-execution-estimator");
  }

  @Test
  void atLeastOneScenarioExists() {
    assertThat(CatalogScenarios.discover())
        .as("the shipped catalog must own at least one verification scenario")
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
      assertThat(scenario.expected().expect())
          .as("scenario '%s' must declare an expect block — an assertion-free scenario passes "
              + "even when the run fails, which is not verification", scenario.name())
          .isNotNull();
      assertThat(scenario.expected().expect().status())
          .as("scenario '%s' must assert at least the run's final status", scenario.name())
          .isNotBlank();
    }
  }

  @Test
  void noScenarioFolderLacksItsDiscoveryMarker() {
    assertThat(CatalogScenarios.unmarkedScenarioFolders())
        .as("a verification/ sub-folder without expected-result.json silently stops being a test "
            + "(the marker is the sole discovery trigger) — restore or remove the folder")
        .isEmpty();
  }

  @Test
  void everyPhysicalWorkflowFolderIsIndexed() {
    assertThat(shippedWorkflows())
        .as("every physical <id>.workflow folder must be listed in shipped-workflows/index — an "
            + "unindexed folder ships as dead cargo in the jar, invisible to the loader and to "
            + "every scenario gate")
        .containsAll(CatalogScenarios.physicalWorkflowFolderIds());
  }

  @Test
  void noStrayEntriesAtCatalogRoot() {
    assertThat(CatalogScenarios.strayCatalogRootEntries())
        .as("the catalog root may contain only <id>.workflow folders, the index, and the "
            + "compatibility manifest — anything else (e.g. a folder missing the .workflow "
            + "suffix) is invisible to discovery and the loader")
        .isEmpty();
  }

  @Test
  void everyPhysicalAgentFolderIsIndexed() {
    assertThat(shippedAgents())
        .as("every physical <id>.agent folder must be listed in shipped-agents/index — an "
            + "unindexed folder ships as dead cargo in the jar, invisible to the agent loader")
        .containsAll(CatalogScenarios.physicalAgentFolderIds());
  }

  @Test
  void noStrayEntriesAtAgentRoot() {
    assertThat(CatalogScenarios.strayAgentRootEntries())
        .as("the shipped-agents root may contain only <id>.agent folders and the index — anything "
            + "else (e.g. a folder missing the .agent suffix) is invisible to the agent loader")
        .isEmpty();
  }

  @Test
  void everyShippedWorkflowCarriesAReadme() {
    for (String workflowId : shippedWorkflows()) {
      String readme = "/shipped-workflows/" + workflowId + ".workflow/README.md";
      assertThat(CatalogConformanceTest.class.getResource(readme))
          .as("shipped workflow '%s' must carry a bundle-level README.md (%s) describing what it "
              + "does and how its verification scenarios drive it", workflowId, readme)
          .isNotNull();
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
