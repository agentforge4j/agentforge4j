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
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import java.util.ArrayList;
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
        workflows(gate(false, true, ReopenPolicy.NONE, AuthorizationMode.ENFORCED),
            withSubmitAndView(deadlineCloseRequirements()))))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsManuallyAndDeadlineClosableGateUnderEnforcedModeWithBothRequirements() {
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(true, true, ReopenPolicy.NONE, AuthorizationMode.ENFORCED),
            withSubmitAndView(deadlineCloseRequirements()))))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDeadlineOnlyClosableGateUnderOpenMode() {
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(false, true, ReopenPolicy.NONE, AuthorizationMode.OPEN))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authorizationMode=ENFORCED");
  }

  @Test
  void rejectsManuallyAndDeadlineClosableGateUnderOpenMode() {
    // externalDeadlineClosable=true, manualClose=true, authorizationMode=OPEN: the manual-close
    // escape hatch does not make the deadline path reachable -- authorize() still denies
    // DEADLINE_CLOSE unconditionally under OPEN, so this combination must also be rejected.
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(true, true, ReopenPolicy.NONE, AuthorizationMode.OPEN))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authorizationMode=ENFORCED");
  }

  @Test
  void rejectsDeadlineClosableGateMissingStepActionRequirements() {
    // Zero requirements declared: the general per-action loop checks CLOSE first (it is always
    // reachable and, along with DEADLINE_CLOSE/OVERRIDE/REOPEN/REPLACE*/WITHDRAW*, is checked
    // before the unconditionally-reachable SUBMIT/VIEW), so that is the action named in the
    // rejection.
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(false, true, ReopenPolicy.NONE, AuthorizationMode.ENFORCED))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action 'close'");
  }

  @Test
  void rejectsDeadlineClosableGateMissingOnlyTheDeadlineCloseRequirement() {
    List<WorkflowRequirement> closeOnly = List.of(closeRequirement());
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(false, true, ReopenPolicy.NONE, AuthorizationMode.ENFORCED), closeOnly)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action 'deadline_close'");
  }

  @Test
  void rejectsEnforcedGateMissingSubmitRequirement() {
    // Every other reachable action (close, and implicitly view, since it is checked last) is
    // declared -- only submit is missing, so it is named in the rejection.
    List<WorkflowRequirement> requirements = List.of(closeRequirement(), viewRequirement());
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(true, false, ReopenPolicy.NONE, AuthorizationMode.ENFORCED), requirements)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action 'submit'");
  }

  @Test
  void rejectsEnforcedGateMissingViewRequirement() {
    List<WorkflowRequirement> requirements = List.of(closeRequirement(), submitRequirement());
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(true, false, ReopenPolicy.NONE, AuthorizationMode.ENFORCED), requirements)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action 'view'");
  }

  @Test
  void acceptsEnforcedGateWithSubmitAndViewRequirementsDeclared() {
    List<WorkflowRequirement> requirements = List.of(closeRequirement(), submitRequirement(),
        viewRequirement());
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(true, false, ReopenPolicy.NONE, AuthorizationMode.ENFORCED), requirements)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsGateThatIsNotClosable() {
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(gate(false, false, ReopenPolicy.NONE))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closable");
  }

  @Test
  void acceptsReopenAllowedWithManualClose() {
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(true, false, ReopenPolicy.ALLOWED)))).doesNotThrowAnyException();
  }

  @Test
  void acceptsReopenAllowedOnADeadlineOnlyClosableGateWithoutManualClose() {
    // No runtime dependency ties reopen() to manualClose -- a fully deadline-driven close/reopen
    // cycle (deadline-close, reopen, deadline-close again) is functionally valid with no human
    // step required, so reopenPolicy=ALLOWED no longer requires manualClose.
    List<WorkflowRequirement> requirements = withSubmitAndView(List.of(
        new WorkflowRequirement("req-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "cv-intake", "close", false, null,
            ResolutionMode.DEFERRED),
        new WorkflowRequirement("req-deadline-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "cv-intake", "deadline_close", false, null,
            ResolutionMode.DEFERRED),
        new WorkflowRequirement("req-reopen", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "cv-intake", "reopen", false, null,
            ResolutionMode.DEFERRED)));
    assertThatCode(() -> validator.validateCollectionGates(
        workflows(gate(false, true, ReopenPolicy.ALLOWED, AuthorizationMode.ENFORCED), requirements)))
        .doesNotThrowAnyException();
  }

  // ---- D2 general rule: every action the config makes reachable needs a declared requirement ---

  @Test
  void rejectsOverrideReachableGateMissingOverrideRequirement() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 1, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(cfg, List.of(closeRequirement()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'override'");
  }

  @Test
  void acceptsOverrideReachableGateWithOverrideRequirement() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 1, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    List<WorkflowRequirement> requirements = withSubmitAndView(
        List.of(closeRequirement(), stepActionRequirement("override")));
    assertThatCode(() -> validator.validateCollectionGates(workflows(cfg, requirements)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAuthorizedReplacePolicyGateMissingReplaceAnyRequirement() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.AUTHORIZED_REPLACE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    // Declares replace_own but not replace_any: AUTHORIZED_REPLACE resolves to REPLACE_ANY for a
    // non-owning actor, so that sub-path is still silently dead without both declared.
    List<WorkflowRequirement> requirements = List.of(closeRequirement(), stepActionRequirement("replace_own"));
    assertThatThrownBy(() -> validator.validateCollectionGates(workflows(cfg, requirements)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'replace_any'");
  }

  @Test
  void acceptsAuthorizedReplacePolicyGateWithBothReplaceRequirements() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.AUTHORIZED_REPLACE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    List<WorkflowRequirement> requirements = withSubmitAndView(List.of(closeRequirement(),
        stepActionRequirement("replace_own"), stepActionRequirement("replace_any")));
    assertThatCode(() -> validator.validateCollectionGates(workflows(cfg, requirements)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsOwnerWithdrawPolicyGateMissingWithdrawOwnRequirement() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.OWNER_WITHDRAW, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    assertThatThrownBy(() -> validator.validateCollectionGates(
        workflows(cfg, List.of(closeRequirement()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'withdraw_own'");
  }

  @Test
  void acceptsOwnerWithdrawPolicyGateWithWithdrawOwnRequirement() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.OWNER_WITHDRAW, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    List<WorkflowRequirement> requirements = withSubmitAndView(
        List.of(closeRequirement(), stepActionRequirement("withdraw_own")));
    assertThatCode(() -> validator.validateCollectionGates(workflows(cfg, requirements)))
        .doesNotThrowAnyException();
  }

  private static WorkflowRequirement closeRequirement() {
    return stepActionRequirement("close");
  }

  private static WorkflowRequirement submitRequirement() {
    return stepActionRequirement("submit");
  }

  private static WorkflowRequirement viewRequirement() {
    return stepActionRequirement("view");
  }

  private static WorkflowRequirement stepActionRequirement(String action) {
    return new WorkflowRequirement("req-" + action, "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, "cv-intake", action, false, null, ResolutionMode.DEFERRED);
  }

  /** Appends submit/view requirements, unconditionally reachable on every ENFORCED gate. */
  private static List<WorkflowRequirement> withSubmitAndView(List<WorkflowRequirement> requirements) {
    List<WorkflowRequirement> combined = new ArrayList<>(requirements);
    combined.add(submitRequirement());
    combined.add(viewRequirement());
    return combined;
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
  void rejectsUnclosableGateInsideNestedWorkflowDefinition() {
    StepDefinition nestedGate = step("nested-gate", gate(false, false, ReopenPolicy.NONE));
    WorkflowDefinition nested = new WorkflowDefinition("nested", "nested", null, null, null,
        "1.0.0", null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(nestedGate));
    WorkflowDefinition workflow = new WorkflowDefinition("w", "w", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(nested));

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
    return gate(manualClose, deadlineClosable, reopen, AuthorizationMode.OPEN);
  }

  private static CollectionBehaviour gate(boolean manualClose, boolean deadlineClosable,
      ReopenPolicy reopen, AuthorizationMode authorizationMode) {
    return new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, manualClose, deadlineClosable, reopen,
        authorizationMode, StepTransition.AUTO);
  }

  private static Map<String, WorkflowDefinition> workflows(CollectionBehaviour gate) {
    return workflows(gate, List.of());
  }

  private static Map<String, WorkflowDefinition> workflows(CollectionBehaviour gate,
      List<WorkflowRequirement> requirements) {
    StepDefinition step = StepDefinition.builder()
        .withStepId("cv-intake")
        .withName("CV intake")
        .withBehaviour(gate)
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("w", "w", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        requirements);
    return Map.of("w", workflow);
  }

  private static List<WorkflowRequirement> deadlineCloseRequirements() {
    return List.of(
        new WorkflowRequirement("req-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "cv-intake", "close", false, null,
            ResolutionMode.DEFERRED),
        new WorkflowRequirement("req-deadline-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "cv-intake", "deadline_close", false, null,
            ResolutionMode.DEFERRED));
  }
}
