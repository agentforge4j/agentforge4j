// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link StepTreeSearcher#findStepAcrossWorkflows} resolves a step uniquely and fails
 * closed when a step id is ambiguous across the reachable workflow graph, rather than silently
 * returning the first match.
 */
class StepTreeSearcherTest {

  private final StepTreeSearcher searcher = new StepTreeSearcher();

  @Test
  void resolvesAStepReachableThroughASingleSubWorkflow() {
    WorkflowDefinition subA = workflow("subA",
        Map.of(),
        List.of(leaf("onlyA", "Only in A")));
    WorkflowDefinition root = workflow("root",
        Map.of(),
        List.of(nestedWorkflowStep("to-a", "subA")));
    WorkflowRepository repository = repository(subA, root);

    StepDefinition found = searcher.findStepAcrossWorkflows(root, "onlyA", repository);

    assertThat(found).isNotNull();
    assertThat(found.stepId()).isEqualTo("onlyA");
  }

  @Test
  void returnsNullWhenNoStepMatches() {
    WorkflowDefinition root = workflow("root", Map.of(), List.of(leaf("a", "A")));
    WorkflowRepository repository = repository(root);

    assertThat(searcher.findStepAcrossWorkflows(root, "absent", repository)).isNull();
  }

  @Test
  void failsClosedWhenTwoReachableWorkflowsDefineTheSameStepId() {
    // Two distinct definitions (different names) sharing the id "dup", reachable from one root.
    WorkflowDefinition subA = workflow("subA", Map.of(), List.of(leaf("dup", "Dup in A")));
    WorkflowDefinition subB = workflow("subB", Map.of(), List.of(leaf("dup", "Dup in B")));
    WorkflowDefinition root = workflow("root",
        Map.of(),
        List.of(nestedWorkflowStep("to-a", "subA"), nestedWorkflowStep("to-b", "subB")));
    WorkflowRepository repository = repository(subA, subB, root);

    assertThatThrownBy(() -> searcher.findStepAcrossWorkflows(root, "dup", repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ambiguous step id 'dup'");
  }

  @Test
  void failsClosedWhenTwoReachableWorkflowsDefineIdenticalDuplicateSteps() {
    // Identical definitions (same id, name AND behaviour) in two reachable workflows — value-equal,
    // so a value-equality dedupe would wrongly collapse them. They occupy two structural locations
    // and must fail closed.
    WorkflowDefinition subA = workflow("subA", Map.of(), List.of(leaf("dup", "Dup")));
    WorkflowDefinition subB = workflow("subB", Map.of(), List.of(leaf("dup", "Dup")));
    WorkflowDefinition root = workflow("root",
        Map.of(),
        List.of(nestedWorkflowStep("to-a", "subA"), nestedWorkflowStep("to-b", "subB")));
    WorkflowRepository repository = repository(subA, subB, root);

    assertThatThrownBy(() -> searcher.findStepAcrossWorkflows(root, "dup", repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ambiguous step id 'dup'");
  }

  @Test
  void aSingleStepReachedThroughTwoBlueprintRefsIsNotAmbiguous() {
    // The same blueprint referenced twice yields the identical step via two paths — one match.
    BlueprintDefinition bp = new BlueprintDefinition("bp", "bp",
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(leaf("shared", "Shared")));
    WorkflowDefinition root = workflow("root",
        Map.of("bp", bp),
        List.of(new BlueprintRef("bp"), new BlueprintRef("bp")));
    WorkflowRepository repository = repository(root);

    StepDefinition found = searcher.findStepAcrossWorkflows(root, "shared", repository);

    assertThat(found).isNotNull();
    assertThat(found.stepId()).isEqualTo("shared");
  }

  private static StepDefinition leaf(String stepId, String name) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(name)
        .withBehaviour(new AgentBehaviour("agent", StepTransition.AUTO, null))
        .build();
  }

  private static StepDefinition nestedWorkflowStep(String stepId, String workflowRef) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new WorkflowBehaviour(workflowRef, StepTransition.AUTO))
        .build();
  }

  private static WorkflowDefinition workflow(String id, Map<String, BlueprintDefinition> blueprints,
      List<Executable> steps) {
    return new WorkflowDefinition(id, id, null, null, null, null, null, WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE, Map.of(), blueprints, steps, List.of());
  }

  private static WorkflowRepository repository(WorkflowDefinition... workflows) {
    Map<String, WorkflowDefinition> byId = new java.util.HashMap<>();
    for (WorkflowDefinition workflow : workflows) {
      byId.put(workflow.id(), workflow);
    }
    return new WorkflowRepository() {
      @Override
      public WorkflowDefinition get(String id) {
        return byId.get(id);
      }

      @Override
      public Map<String, WorkflowDefinition> findAll() {
        return Map.copyOf(byId);
      }
    };
  }
}
