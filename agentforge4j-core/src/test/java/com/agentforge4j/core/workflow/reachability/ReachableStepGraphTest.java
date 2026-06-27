// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link ReachableStepGraph}. These cases pin the descent rules the runtime's step
 * resolution relies on (and that the loader guard reuses), so a regression here would silently
 * diverge load-time and run-time reachability.
 */
class ReachableStepGraphTest {

  private static final WorkflowRefResolver NO_SUBWORKFLOWS = ref -> null;

  @Test
  void walk_recordsTopLevelStepAtWorkflowLocation() {
    WorkflowDefinition root = wf("root", Map.of(), step("s1"));

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).singleElement().satisfies(rs -> {
      assertThat(rs.stepId()).isEqualTo("s1");
      assertThat(rs.location()).isEqualTo("wf:root/step:s1");
    });
  }

  @Test
  void walk_descendsBlueprintRefButNotInlineBlueprintDefinition() {
    BlueprintDefinition referenced = blueprint("ref-bp", step("inner"));
    BlueprintDefinition inline = blueprint("inline-bp", step("ghost"));
    WorkflowDefinition root = wf("root", Map.of("ref-bp", referenced),
        new BlueprintRef("ref-bp"), inline);

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::location)
        .containsExactly("wf:root/bp:ref-bp/step:inner");
    // The inline BlueprintDefinition is not directly executable, so its steps are unreachable.
    assertThat(reached).extracting(ReachableStep::stepId).doesNotContain("ghost");
  }

  @Test
  void walk_descendsResolvedSubWorkflow() {
    WorkflowDefinition sub = wf("sub", Map.of(), step("substep"));
    WorkflowDefinition root = wf("root", Map.of(), workflowStep("call", "sub"));

    List<ReachableStep> reached = ReachableStepGraph.walk(root, Map.of("sub", sub)::get);

    assertThat(reached).extracting(ReachableStep::location)
        .containsExactlyInAnyOrder("wf:root/step:call", "wf:sub/step:substep");
  }

  @Test
  void walk_doesNotDescendBranchTargets() {
    // The runtime's cross-workflow searcher does not descend BranchBehaviour targets; the guard must
    // not either, or it would reject ids the runtime never resolves at a gate.
    BlueprintDefinition branchBody = blueprint("branch-bp", step("branch-step"));
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag", Map.of("yes", new BlueprintRef("branch-bp")), null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of("branch-bp", branchBody), branching);

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::stepId).containsExactly("router");
  }

  @Test
  void resolveUnique_returnsTheSingleReachableStep() {
    WorkflowDefinition root = wf("root", Map.of(), step("only"));

    assertThat(ReachableStepGraph.resolveUnique(root, "only", NO_SUBWORKFLOWS))
        .extracting(StepDefinition::stepId).isEqualTo("only");
  }

  @Test
  void resolveUnique_returnsNullWhenUnreachable() {
    WorkflowDefinition root = wf("root", Map.of(), step("only"));

    assertThat(ReachableStepGraph.resolveUnique(root, "absent", NO_SUBWORKFLOWS)).isNull();
  }

  @Test
  void resolveUnique_throwsWhenIdReachableFromTwoBlueprints() {
    BlueprintDefinition first = blueprint("bp-a", step("dup"));
    BlueprintDefinition second = blueprint("bp-b", step("dup"));
    WorkflowDefinition root = wf("root", Map.of("bp-a", first, "bp-b", second),
        new BlueprintRef("bp-a"), new BlueprintRef("bp-b"));

    assertThatThrownBy(() -> ReachableStepGraph.resolveUnique(root, "dup", NO_SUBWORKFLOWS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ambiguous step id 'dup'")
        .hasMessageContaining("reachable step ids must be unique");
  }

  @Test
  void findAmbiguousStepIds_flagsIdInTwoBlueprintsWithBothLocations() {
    BlueprintDefinition first = blueprint("bp-a", step("dup"));
    BlueprintDefinition second = blueprint("bp-b", step("dup"));
    WorkflowDefinition root = wf("root", Map.of("bp-a", first, "bp-b", second),
        new BlueprintRef("bp-a"), new BlueprintRef("bp-b"));

    List<AmbiguousStepId> ambiguous = ReachableStepGraph.findAmbiguousStepIds(root, NO_SUBWORKFLOWS);

    assertThat(ambiguous).singleElement().satisfies(a -> {
      assertThat(a.stepId()).isEqualTo("dup");
      assertThat(a.locations())
          .containsExactlyInAnyOrder("wf:root/bp:bp-a/step:dup", "wf:root/bp:bp-b/step:dup");
    });
  }

  @Test
  void findAmbiguousStepIds_flagsIdInTwoDistinctSubWorkflows() {
    WorkflowDefinition subA = wf("sub-a", Map.of(), step("dup"));
    WorkflowDefinition subB = wf("sub-b", Map.of(), step("dup"));
    WorkflowDefinition root = wf("root", Map.of(),
        workflowStep("call-a", "sub-a"), workflowStep("call-b", "sub-b"));

    List<AmbiguousStepId> ambiguous = ReachableStepGraph.findAmbiguousStepIds(root,
        Map.of("sub-a", subA, "sub-b", subB)::get);

    assertThat(ambiguous).extracting(AmbiguousStepId::stepId).containsExactly("dup");
  }

  @Test
  void findAmbiguousStepIds_collapsesSameSubWorkflowReferencedTwice() {
    // A single sub-workflow referenced twice is ONE definition reached via two paths: its container
    // key resets to wf:<sub-id>, so the locations collapse and it is not ambiguous (mirrors the
    // runtime searcher's workflow-id visited guard).
    WorkflowDefinition sub = wf("sub", Map.of(), step("dup"));
    WorkflowDefinition root = wf("root", Map.of(),
        workflowStep("call-1", "sub"), workflowStep("call-2", "sub"));

    assertThat(ReachableStepGraph.findAmbiguousStepIds(root, Map.of("sub", sub)::get)).isEmpty();
    assertThatCode(() -> ReachableStepGraph.resolveUnique(root, "dup", Map.of("sub", sub)::get))
        .doesNotThrowAnyException();
  }

  @Test
  void findAmbiguousStepIds_collapsesSameBlueprintReferencedTwice() {
    BlueprintDefinition shared = blueprint("bp", step("dup"));
    WorkflowDefinition root = wf("root", Map.of("bp", shared),
        new BlueprintRef("bp"), new BlueprintRef("bp"));

    assertThat(ReachableStepGraph.findAmbiguousStepIds(root, NO_SUBWORKFLOWS)).isEmpty();
  }

  @Test
  void walk_terminatesOnBlueprintRefCycle() {
    BlueprintDefinition blueprintA = blueprint("bp-a", new BlueprintRef("bp-b"));
    BlueprintDefinition blueprintB = blueprint("bp-b", new BlueprintRef("bp-a"), step("x"));
    WorkflowDefinition root = wf("root", Map.of("bp-a", blueprintA, "bp-b", blueprintB),
        new BlueprintRef("bp-a"));

    assertThatCode(() -> {
      List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);
      assertThat(reached).extracting(ReachableStep::stepId).containsExactly("x");
    }).doesNotThrowAnyException();
  }

  private static StepDefinition step(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new FailBehaviour("stop"))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static StepDefinition workflowStep(String stepId, String workflowRef) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new WorkflowBehaviour(workflowRef, StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static BlueprintDefinition blueprint(String blueprintId, Executable... steps) {
    return new BlueprintDefinition(blueprintId, blueprintId,
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(steps));
  }

  private static WorkflowDefinition wf(String id, Map<String, BlueprintDefinition> blueprints,
      Executable... steps) {
    return new WorkflowDefinition(id, "W", "d", null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, List.of(steps));
  }
}
