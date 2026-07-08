// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorContextSelectionTest {

  private final WorkflowValidator validator = new WorkflowValidator();

  private static ContextSelector sel(ContextSourceKind kind, String ref) {
    return new ContextSelector(kind, ref, ContextVariant.FULL);
  }

  private static LedgerDefinition ledger(String id) {
    return new LedgerDefinition(id, "ledger/requirement-ledger.schema.json",
        LedgerMergeStrategy.APPEND, null);
  }

  private static StepDefinition step(String id) {
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop")).build();
  }

  private static StepDefinition stepWithSelection(String id, ContextSelection selection) {
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop")).withContextSelection(selection).build();
  }

  private static WorkflowDefinition workflow(List<Executable> steps,
      List<LedgerDefinition> ledgers) {
    return workflow("wf", steps, ledgers, Map.of());
  }

  private static WorkflowDefinition workflow(String id, List<Executable> steps,
      List<LedgerDefinition> ledgers, Map<String, BlueprintDefinition> blueprints) {
    return new WorkflowDefinition(id, "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, steps, List.of(),
        ledgers);
  }

  private static BlueprintDefinition blueprint(String id, List<Executable> steps) {
    return new BlueprintDefinition(id, id, new BlueprintBehaviour(null, StepTransition.AUTO),
        steps);
  }

  private void validate(WorkflowDefinition wf) {
    validator.validateContextSelectionRefs(Map.of("wf", wf));
  }

  @Test
  void acceptsResolvableLedgerAndStepSelectors() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "requirements.entries"),
            sel(ContextSourceKind.STEP_OUTPUT, "s1")),
        List.of(), null);
    WorkflowDefinition wf = workflow(
        List.of(step("s1"), stepWithSelection("s2", selection)),
        List.of(ledger("requirements")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownLedgerSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void rejectsUnknownStepOutputSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "ghost")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown step 'ghost'");
  }

  @Test
  void rejectsUnknownArtifactSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.ARTIFACT, "missing")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown artifact 'missing'");
  }

  @Test
  void skipsContextPackAndStateKeySelectors() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "any-pack"),
            sel(ContextSourceKind.STATE_KEY, "some-key")),
        List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void validatesExpandableScopeSelectors() {
    ContextSelection selection = new ContextSelection(List.of(),
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void validatesCompactStepSourceSelector() {
    StepDefinition compact = StepDefinition.builder().withStepId("c").withName("c")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "nope"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(compact), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void acceptsResolvableSelectorInsideBranchChild() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "branch-target-a")), List.of(), null);
    StepDefinition branchTargetA = step("branch-target-a");
    StepDefinition branchTargetB = stepWithSelection("branch-target-b", selection);
    BranchBehaviour branch = new BranchBehaviour("route",
        Map.of("a", branchTargetA, "b", branchTargetB), List.of(), null, false);
    StepDefinition branchStep = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(branch).build();
    WorkflowDefinition wf = workflow(List.of(branchStep), List.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownSelectorInsideBranchChild() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of(), null);
    StepDefinition branchTarget = stepWithSelection("branch-target", selection);
    BranchBehaviour branch = new BranchBehaviour("route", Map.of("a", branchTarget), List.of(),
        null, false);
    StepDefinition branchStep = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(branch).build();
    WorkflowDefinition wf = workflow(List.of(branchStep), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void acceptsResolvableSelectorInsideBlueprintRefSteps() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "bp-step-a")), List.of(), null);
    StepDefinition bpStepA = step("bp-step-a");
    StepDefinition bpStepB = stepWithSelection("bp-step-b", selection);
    BlueprintDefinition bp = blueprint("bp1", List.of(bpStepA, bpStepB));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp1")), List.of(),
        Map.of("bp1", bp));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownSelectorInsideBlueprintRefSteps() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of(), null);
    BlueprintDefinition bp = blueprint("bp1", List.of(stepWithSelection("bp-step", selection)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp1")), List.of(),
        Map.of("bp1", bp));

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void nestedWorkflowValidatesItsOwnLedgerScopeIndependently() {
    ContextSelection parentSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "parentLedger.entries")), List.of(), null);
    ContextSelection nestedSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nestedLedger.entries")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf",
        List.of(stepWithSelection("nested-step", nestedSelection)),
        List.of(ledger("nestedLedger")), Map.of());
    WorkflowDefinition wf = workflow("wf",
        List.of(stepWithSelection("parent-step", parentSelection), nestedWf),
        List.of(ledger("parentLedger")), Map.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsNestedWorkflowLedgerSelectorReferencedFromParentScope() {
    ContextSelection parentSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nestedLedger.entries")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf", List.of(step("nested-step")),
        List.of(ledger("nestedLedger")), Map.of());
    WorkflowDefinition wf = workflow("wf",
        List.of(stepWithSelection("parent-step", parentSelection), nestedWf),
        List.of(ledger("parentLedger")), Map.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nestedLedger'");
  }

  @Test
  void rejectsParentWorkflowLedgerSelectorReferencedFromNestedScope() {
    ContextSelection nestedSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "parentLedger.entries")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf",
        List.of(stepWithSelection("nested-step", nestedSelection)), List.of(), Map.of());
    WorkflowDefinition wf = workflow("wf", List.of(nestedWf), List.of(ledger("parentLedger")),
        Map.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'parentLedger'");
  }
}
