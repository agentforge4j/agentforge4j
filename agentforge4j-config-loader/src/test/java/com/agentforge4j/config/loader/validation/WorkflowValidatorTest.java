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
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
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
    StepDefinition step = new StepDefinition(
        "s1",
        "S1",
        new WorkflowBehaviour("missing-wf", StepTransition.AUTO),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateWorkflowRefs(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown workflow")
        .hasMessageContaining("missing-wf");
  }

  @Test
  void validateCircularRefs_detectsDirectSelfReference() {
    StepDefinition selfRef = new StepDefinition(
        "s1",
        "S1",
        new WorkflowBehaviour("wf-a", StepTransition.AUTO),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
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
    StepDefinition toB = new StepDefinition(
        "s1",
        "S1",
        new WorkflowBehaviour("wf-b", StepTransition.AUTO),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
    StepDefinition toA = new StepDefinition(
        "s2",
        "S2",
        new WorkflowBehaviour("wf-a", StepTransition.AUTO),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
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
        List.of(new BlueprintRef("ghost-bp")));

    assertThatThrownBy(() -> validator.validateBlueprintRefs(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown blueprint")
        .hasMessageContaining("ghost-bp");
  }

  @Test
  void validateArtifactRefs_rejectsUnknownArtifactOnInputStep() {
    StepDefinition step = new StepDefinition(
        "s1",
        "S1",
        new InputBehaviour("missing-artifact", StepTransition.AUTO),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateArtifactRefs(Map.of("wf1", wf)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown artifact")
        .hasMessageContaining("missing-artifact");
  }

  @Test
  void validateAgentRefs_throwsWithAllUnresolvedSites() {
    StepDefinition step = new StepDefinition(
        "s1",
        "S1",
        new AgentBehaviour("ghost-agent", StepTransition.AUTO, null),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
    WorkflowDefinition wf = wf("wf1", List.of(step));

    assertThatThrownBy(() -> validator.validateAgentRefs(Map.of("wf1", wf), Map.of()))
        .isInstanceOf(UnresolvedAgentReferenceException.class)
        .hasMessageContaining("ghost-agent");
  }

  @Test
  void validateAgentRefs_passesWhenCatalogContainsAgent() {
    StepDefinition step = new StepDefinition(
        "s1",
        "S1",
        new AgentBehaviour("ok-agent", StepTransition.AUTO, null),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
    WorkflowDefinition wf = wf("wf1", List.of(step));
    AgentDefinition agent = new AgentDefinition(
        "ok-agent",
        "Ok",
        AgentLocality.CLOUD,
        true,
        "sys",
        List.of(new ProviderPreference("openai", "gpt-4o-mini")),
        List.of("COMPLETE"),
        null,
        null,
        "1.0.0");

    assertThatCode(() -> validator.validateAgentRefs(Map.of("wf1", wf), Map.of("ok-agent", agent)))
        .doesNotThrowAnyException();
  }

  private static StepDefinition terminalStep(String stepId) {
    return new StepDefinition(
        stepId,
        stepId,
        new FailBehaviour("stop"),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
  }

  private static StepDefinition workflowRefStep(String stepId, String workflowRef) {
    return new StepDefinition(
        stepId,
        stepId,
        new WorkflowBehaviour(workflowRef, StepTransition.AUTO),
        new ContextMapping(List.of(), List.of()),
        null,
        null);
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
        steps);
  }
}
