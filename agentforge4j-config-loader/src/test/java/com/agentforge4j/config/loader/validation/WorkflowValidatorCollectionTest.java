// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.collection.AuthorizationMode;
import com.agentforge4j.core.workflow.collection.DuplicatePolicy;
import com.agentforge4j.core.workflow.collection.ReopenPolicy;
import com.agentforge4j.core.workflow.collection.ReplacementPolicy;
import com.agentforge4j.core.workflow.collection.WithdrawalPolicy;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowValidatorCollectionTest {

  private final WorkflowValidator validator = new WorkflowValidator();

  @Test
  void acceptsManuallyClosableGate() {
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(true, false, ReopenPolicy.NONE)))).doesNotThrowAnyException();
  }

  @Test
  void acceptsDeadlineOnlyClosableGate() {
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(false, true, ReopenPolicy.NONE)))).doesNotThrowAnyException();
  }

  @Test
  void rejectsGateThatIsNotClosable() {
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(false, false, ReopenPolicy.NONE))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closable");
  }

  @Test
  void rejectsReopenAllowedWithoutManualClose() {
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(false, true, ReopenPolicy.ALLOWED))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reopenPolicy=ALLOWED requires manualClose");
  }

  @Test
  void acceptsReopenAllowedWithManualClose() {
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(true, false, ReopenPolicy.ALLOWED)))).doesNotThrowAnyException();
  }

  // ---- nested placements: blueprint bodies and BRANCH children carry the same invariants ------

  @Test
  void rejectsUnclosableGateInsideBlueprintBody() {
    StepDefinition nestedGate = step("bp-gate", gate(false, false, ReopenPolicy.NONE));
    BlueprintDefinition blueprint = new BlueprintDefinition("bp", "bp",
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(nestedGate));
    WorkflowDefinition workflow = new WorkflowDefinition("w", "w", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of("bp", blueprint),
        List.of(new BlueprintRef("bp")));

    assertThatThrownBy(() -> validator.validateCollectionGates(Map.of("w", workflow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closable");
  }

  @Test
  void rejectsUnclosableGateUnderBranchChild() {
    StepDefinition branchedGate = step("branch-gate", gate(false, false, ReopenPolicy.NONE));
    StepDefinition branchStep = step("router",
        new BranchBehaviour("route", Map.of("submit", branchedGate), List.of(), null, false));
    WorkflowDefinition workflow = new WorkflowDefinition("w", "w", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(branchStep));

    assertThatThrownBy(() -> validator.validateCollectionGates(Map.of("w", workflow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closable");
  }

  @Test
  void acceptsValidGateInsideBlueprintBody() {
    StepDefinition nestedGate = step("bp-gate", gate(true, false, ReopenPolicy.NONE));
    BlueprintDefinition blueprint = new BlueprintDefinition("bp", "bp",
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(nestedGate));
    WorkflowDefinition workflow = new WorkflowDefinition("w", "w", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of("bp", blueprint),
        List.of(new BlueprintRef("bp")));

    assertThatCode(() -> validator.validateCollectionGates(Map.of("w", workflow)))
        .doesNotThrowAnyException();
  }

  private static StepDefinition step(String stepId, StepBehaviour behaviour) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(behaviour)
        .build();
  }

  private static CollectionBehaviour gate(boolean manualClose, boolean deadlineClosable,
      ReopenPolicy reopen) {
    return new CollectionBehaviour(null, 0, null, null, 0, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, manualClose, deadlineClosable, reopen,
        AuthorizationMode.OPEN, StepTransition.AUTO);
  }

  private static Map<String, WorkflowDefinition> workflows(CollectionBehaviour gate) {
    StepDefinition step = StepDefinition.builder()
        .withStepId("cv-intake")
        .withName("CV intake")
        .withBehaviour(gate)
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("w", "w", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step));
    return Map.of("w", workflow);
  }
}
