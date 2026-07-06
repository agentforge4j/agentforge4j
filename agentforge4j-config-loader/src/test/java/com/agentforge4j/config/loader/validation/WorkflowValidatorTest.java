// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ContextEqualityContract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link WorkflowValidator} (in addition to {@link WorkflowDraftValidatorTest}
 * which exercises the same rules through the draft report).
 */
class WorkflowValidatorTest {

  private final WorkflowValidator validator = new WorkflowValidator();

  @Test
  void validateWorkflowRefs_rejectsUnknownNestedWorkflow() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new WorkflowBehaviour("missing-wf", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateWorkflowRefs(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown workflow")
        .hasMessageContaining("missing-wf");
  }

  @Test
  void validateCircularRefs_detectsDirectSelfReference() {
    StepDefinition selfRef = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new WorkflowBehaviour("wf-a", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wfA = wf("wf-a", List.of(selfRef));

    assertThatThrownBy(() -> validator.validateCircularRefs(Map.of("wf-a", wfA)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("circular references")
        .hasMessageContaining("wf-a");
  }

  @Test
  void validateCircularRefs_detectsThreeNodeCycle() {
    WorkflowDefinition wfA = wf("wf-a", List.of(workflowRefStep("s1", "wf-b")));
    WorkflowDefinition wfB = wf("wf-b", List.of(workflowRefStep("s2", "wf-c")));
    WorkflowDefinition wfC = wf("wf-c", List.of(workflowRefStep("s3", "wf-a")));

    assertThatThrownBy(() -> validator.validateCircularRefs(
        Map.of("wf-a", wfA, "wf-b", wfB, "wf-c", wfC)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("circular references");
  }

  @Test
  void validateCircularRefs_allowsAcyclicDiamond() {
    WorkflowDefinition wfA = wf("wf-a", List.of(
        workflowRefStep("s1", "wf-b"),
        workflowRefStep("s2", "wf-c")));
    WorkflowDefinition wfB = wf("wf-b", List.of(workflowRefStep("s3", "wf-d")));
    WorkflowDefinition wfC = wf("wf-c", List.of(workflowRefStep("s4", "wf-d")));
    WorkflowDefinition wfD = wf("wf-d", List.of(terminalStep("s5")));

    assertThatCode(() -> validator.validateCircularRefs(
        Map.of("wf-a", wfA, "wf-b", wfB, "wf-c", wfC, "wf-d", wfD)))
        .doesNotThrowAnyException();
  }

  @Test
  void validateCircularRefs_detectsMutualWorkflowReferences() {
    StepDefinition toB = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new WorkflowBehaviour("wf-b", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    StepDefinition toA = StepDefinition.builder()
        .withStepId("s2")
        .withName("S2")
        .withBehaviour(new WorkflowBehaviour("wf-a", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wfA = wf("wf-a", List.of(toB));
    WorkflowDefinition wfB = wf("wf-b", List.of(toA));

    assertThatThrownBy(() -> validator.validateCircularRefs(Map.of("wf-a", wfA, "wf-b", wfB)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("circular references");
  }

  @Test
  void validateBlueprintRefs_rejectsUnknownBlueprintRef() {
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1",
        "W",
        "d",
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(new BlueprintRef("ghost-bp")),
        List.of(),
        List.of());

    assertThatThrownBy(() -> validator.validateBlueprintRefs(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown blueprint")
        .hasMessageContaining("ghost-bp");
  }

  @Test
  void validateArtifactRefs_rejectsUnknownArtifactOnInputStep() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new InputBehaviour("missing-artifact", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateArtifactRefs(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown artifact")
        .hasMessageContaining("missing-artifact");
  }

  @Test
  void validateAgentRefs_throwsWithAllUnresolvedSites() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("ghost-agent", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateAgentRefs(Map.of("wf1", wf), Map.of()))
        .isInstanceOf(UnresolvedAgentReferenceException.class)
        .hasMessageContaining("ghost-agent");
  }

  @Test
  void validateAgentRefs_passesWhenCatalogContainsAgent() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("ok-agent", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = wf("wf1", List.of(step));
    AgentDefinition agent = AgentDefinition.builder()
        .withId("ok-agent")
        .withName("Ok")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();

    assertThatCode(() -> validator.validateAgentRefs(Map.of("wf1", wf), Map.of("ok-agent", agent)))
        .doesNotThrowAnyException();
  }

  @Test
  void validateReachableStepIdUniqueness_rejectsIdReachableFromTwoSubWorkflows() {
    WorkflowDefinition subA = wf("sub-a", List.of(terminalStep("dup")));
    WorkflowDefinition subB = wf("sub-b", List.of(terminalStep("dup")));
    WorkflowDefinition root = wf("root", List.of(
        workflowRefStep("call-a", "sub-a"), workflowRefStep("call-b", "sub-b")));

    assertThatThrownBy(() -> validator.validateReachableStepIdUniqueness(
        Map.of("root", root, "sub-a", subA, "sub-b", subB)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reachable step ids must be unique")
        .hasMessageContaining("dup")
        .hasMessageContaining("wf:sub-a/step:dup")
        .hasMessageContaining("wf:sub-b/step:dup");
  }

  @Test
  void validateReachableStepIdUniqueness_rejectsIdInTwoBlueprints() {
    BlueprintDefinition first = blueprint("bp-a", terminalStep("dup"));
    BlueprintDefinition second = blueprint("bp-b", terminalStep("dup"));
    WorkflowDefinition root = wfWithBlueprints("root",
        Map.of("bp-a", first, "bp-b", second),
        List.of(new BlueprintRef("bp-a"), new BlueprintRef("bp-b")));

    assertThatThrownBy(() -> validator.validateReachableStepIdUniqueness(Map.of("root", root)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reachable step ids must be unique")
        .hasMessageContaining("wf:root/bp:bp-a/step:dup")
        .hasMessageContaining("wf:root/bp:bp-b/step:dup");
  }

  @Test
  void validateReachableStepIdUniqueness_collapsesSameSubWorkflowReferencedTwice() {
    // One sub-workflow reached by two WORKFLOW steps is a single definition reached via two paths;
    // its container key resets to wf:<sub-id>, so the locations collapse and it is not ambiguous.
    WorkflowDefinition sub = wf("sub", List.of(terminalStep("dup")));
    WorkflowDefinition root = wf("root", List.of(
        workflowRefStep("call-1", "sub"), workflowRefStep("call-2", "sub")));

    assertThatCode(() -> validator.validateReachableStepIdUniqueness(
        Map.of("root", root, "sub", sub))).doesNotThrowAnyException();
  }

  @Test
  void validateReachableStepIdUniqueness_allowsSameIdInTwoDifferentRoots() {
    // Each workflow is validated as its own run root; a step id shared across two unrelated roots is
    // two separate runs, never an ambiguity.
    WorkflowDefinition rootA = wf("root-a", List.of(terminalStep("dup")));
    WorkflowDefinition rootB = wf("root-b", List.of(terminalStep("dup")));

    assertThatCode(() -> validator.validateReachableStepIdUniqueness(
        Map.of("root-a", rootA, "root-b", rootB))).doesNotThrowAnyException();
  }

  @Test
  void validateReachableStepIdUniqueness_ignoresUnreferencedBlueprintCollision() {
    // Under the no-inline-blueprint model a blueprint is reachable only via a BlueprintRef; one present
    // in the blueprint map but referenced by nothing is unreachable, so a colliding step id inside it is
    // not flagged. This replaces the retired inline-BlueprintDefinition-collision case.
    BlueprintDefinition unreferenced = blueprint("orphan-bp", terminalStep("dup"));
    WorkflowDefinition root = wfWithBlueprints("root", Map.of("orphan-bp", unreferenced),
        List.of(terminalStep("dup")));

    assertThatCode(() -> validator.validateReachableStepIdUniqueness(Map.of("root", root)))
        .doesNotThrowAnyException();
  }

  private static BlueprintDefinition blueprint(String blueprintId, Executable... steps) {
    return new BlueprintDefinition(blueprintId, blueprintId,
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(steps));
  }

  private static WorkflowDefinition wfWithBlueprints(String id,
      Map<String, BlueprintDefinition> blueprints, List<Executable> steps) {
    return new WorkflowDefinition(id, "W", "d", null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, steps,
        List.of(), List.of());
  }

  private static StepDefinition terminalStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new FailBehaviour("stop"))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static StepDefinition workflowRefStep(String stepId, String workflowRef) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new WorkflowBehaviour(workflowRef, StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  @Test
  void validateValidateBehaviourContracts_rejectsContractPathOutsideAllowlist() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("validate")
        .withName("Validate")
        .withBehaviour(new ValidateBehaviour("agent-bundle", List.of("agent.json"),
            List.of(new ContextEqualityContract("other.json", "/modelTier", "recommendedTier"))))
        .withContextMapping(new ContextMapping(List.of("recommendedTier"), List.of()))
        .build();
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateValidateBehaviourContracts(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("other.json")
        .hasMessageContaining("requiredArtifacts allowlist");
  }

  @Test
  void validateValidateBehaviourContracts_acceptsContractPathWithinAllowlist() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("validate")
        .withName("Validate")
        .withBehaviour(new ValidateBehaviour("agent-bundle", List.of("agent.json"),
            List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier"))))
        .withContextMapping(new ContextMapping(List.of("recommendedTier"), List.of()))
        .build();
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatCode(() -> validator.validateValidateBehaviourContracts(Map.of("wf1", wf)))
        .doesNotThrowAnyException();
  }

  private static WorkflowDefinition wf(String id, List<Executable> steps) {
    return new WorkflowDefinition(
        id,
        "W",
        "d",
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        steps,
        List.of(),
        List.of());
  }
}
