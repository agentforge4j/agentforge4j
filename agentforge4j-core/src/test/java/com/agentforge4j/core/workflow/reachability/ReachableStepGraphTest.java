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
import com.agentforge4j.core.workflow.step.behaviour.BranchPredicate;
import com.agentforge4j.core.workflow.step.behaviour.BranchPredicateKind;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
  void walk_descendsBlueprintRef() {
    // A BlueprintDefinition is not itself an Executable; it is reached only via a BlueprintRef, whose
    // referenced body steps are descended.
    BlueprintDefinition referenced = blueprint("ref-bp", step("inner"));
    WorkflowDefinition root = wf("root", Map.of("ref-bp", referenced), new BlueprintRef("ref-bp"));

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::location)
        .containsExactly("wf:root/bp:ref-bp/step:inner");
  }

  @Test
  void walk_doesNotDescendUnreferencedBlueprint() {
    // Under the no-inline-blueprint model a BlueprintDefinition is reachable only through a
    // BlueprintRef; one present in the workflow's blueprint map but referenced by nothing contributes
    // no reachable steps. This is the modern replacement for the retired inline-BlueprintDefinition case.
    BlueprintDefinition unreferenced = blueprint("orphan-bp", step("ghost"));
    WorkflowDefinition root = wf("root", Map.of("orphan-bp", unreferenced), step("s1"));

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::stepId).containsExactly("s1");
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
  void walk_descendsBranchTargets() {
    // A step reachable only as a BranchBehaviour target (here, via a blueprint-ref branch
    // value) must be descended exactly like any other child, or the runtime's gate/resume
    // resolution (which uses this same graph) can neither find nor gate it — the branch-child
    // human-gate bypass this case regresses.
    BlueprintDefinition branchBody = blueprint("branch-bp", step("branch-step"));
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag", Map.of("yes", new BlueprintRef("branch-bp")),
            List.of(), null, false))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of("branch-bp", branchBody), branching);

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::stepId)
        .containsExactlyInAnyOrder("router", "branch-step");
    assertThat(reached).extracting(ReachableStep::location)
        .contains("wf:root/bp:branch-bp/step:branch-step");
  }

  @Test
  void walk_descendsDirectBranchStepTargets() {
    // Same as walk_descendsBranchTargets, but the branch value is a plain step (no blueprint-ref
    // indirection) — the shape a gated INPUT/AGENT step directly under a BRANCH takes.
    StepDefinition gated = step("gated");
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag", Map.of("yes", gated), List.of(), null, false))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of(), branching);

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::stepId)
        .containsExactlyInAnyOrder("router", "gated");
    assertThat(reached).extracting(ReachableStep::location).contains("wf:root/step:gated");
  }

  @Test
  void walk_descendsBranchDefaultTarget() {
    // Same regression class as walk_descendsDirectBranchStepTargets, but the gated step is reachable
    // only via BranchBehaviour.defaultBranch() — no exact-match key or predicate matches it.
    StepDefinition gated = step("gated");
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag", Map.of("unrelated", step("decoy")), List.of(),
            gated, false))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of(), branching);

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::stepId)
        .containsExactlyInAnyOrder("router", "decoy", "gated");
    assertThat(reached).extracting(ReachableStep::location).contains("wf:root/step:gated");
  }

  @Test
  void walk_descendsBranchPredicateTarget() {
    // Same regression class again, but the gated step is reachable only via a matched predicate's
    // target — neither an exact-match branch nor the default.
    StepDefinition gated = step("gated");
    BranchPredicate predicate = new BranchPredicate(BranchPredicateKind.MEMBER_OF,
        Set.of("yes", "go"), gated);
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag", Map.of(), List.of(predicate), null, false))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of(), branching);

    List<ReachableStep> reached = ReachableStepGraph.walk(root, NO_SUBWORKFLOWS);

    assertThat(reached).extracting(ReachableStep::stepId)
        .containsExactlyInAnyOrder("router", "gated");
    assertThat(reached).extracting(ReachableStep::location).contains("wf:root/step:gated");
  }

  @Test
  void walk_flagsDistinctSameIdDefinitionsInTwoBranchArms() {
    // Two branch arms each define their own step with the same id. They are distinct definitions
    // that may disagree on gating, so they must surface as two occurrences (ambiguous, fail-closed)
    // rather than silently collapsing to whichever arm was walked first.
    StepDefinition armYes = step("dup");
    StepDefinition armNo = step("dup");
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag", Map.of("yes", armYes, "no", armNo), List.of(),
            null, false))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of(), branching);

    List<AmbiguousStepId> ambiguous = ReachableStepGraph.findAmbiguousStepIds(root, NO_SUBWORKFLOWS);
    assertThat(ambiguous).extracting(AmbiguousStepId::stepId).containsExactly("dup");

    assertThatThrownBy(() -> ReachableStepGraph.resolveUnique(root, "dup", NO_SUBWORKFLOWS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ambiguous step id 'dup'");
  }

  @Test
  void walk_collapsesSameBlueprintReferencedFromTwoBranchArms() {
    // Convergence: two arms routing to the same blueprint is ONE definition reached via two paths —
    // it must collapse to a single location, not read as ambiguous.
    BlueprintDefinition shared = blueprint("shared-bp", step("converged"));
    StepDefinition branching = StepDefinition.builder()
        .withStepId("router")
        .withName("router")
        .withBehaviour(new BranchBehaviour("flag",
            Map.of("yes", new BlueprintRef("shared-bp"), "no", new BlueprintRef("shared-bp")),
            List.of(), null, false))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = wf("root", Map.of("shared-bp", shared), branching);

    assertThat(ReachableStepGraph.findAmbiguousStepIds(root, NO_SUBWORKFLOWS)).isEmpty();
    assertThatCode(() -> ReachableStepGraph.resolveUnique(root, "converged", NO_SUBWORKFLOWS))
        .doesNotThrowAnyException();
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
