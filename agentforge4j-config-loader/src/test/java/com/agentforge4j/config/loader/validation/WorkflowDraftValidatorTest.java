// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.WorkflowTreeWalker;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDraftValidatorTest {

  @Test
  void validate_emptyWorkflows_isValid() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));

    ValidationReport report = draftValidator.validate(Map.of(), Map.of());

    assertThat(report.isValid()).isTrue();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void validate_collectsAgentRefFailuresWithoutThrowing() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("missing-agent", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step));

    ValidationReport report =
        draftValidator.validate(Map.of("wf1", wf), Map.of());

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .anyMatch(e -> "validateAgentRefs".equals(e.code()))
        .anyMatch(e -> e.message().contains("missing-agent"));
  }

  @Test
  void validate_validWorkflowAndAgents_isValid() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("ok", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step));
    AgentDefinition agent = AgentDefinition.builder()
        .withId("ok")
        .withName("Ok")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();

    ValidationReport report =
        draftValidator.validate(Map.of("wf1", wf), Map.of("ok", agent));

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void validate_accumulatesMultipleCheckFailures() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));
    StepDefinition badAgent = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("missing", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    StepDefinition badRetry = StepDefinition.builder()
        .withStepId("s2")
        .withName("S2")
        .withBehaviour(new RetryPreviousBehaviour("nope", RetryMode.FROM_STEP, 2, new BlueprintRef("bp")))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(badAgent, badRetry));

    ValidationReport report = draftValidator.validate(Map.of("wf1", wf), Map.of());

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .extracting(ValidationError::code)
        .contains("validateAgentRefs", "validateRetryStepRefs");
  }

  // Regression coverage for the shared-walker duplication defect: before this fix, a single dangling
  // BlueprintRef was independently caught by 7 of the 9 checks (each treating the walker's own
  // structural-integrity guard as its own failure), so one broken blueprint ref produced 7 duplicate
  // ValidationError entries in one report instead of 1.
  @Test
  void validate_reportsBlueprintStructureDefectExactlyOnce() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(new BlueprintRef("ghost-bp")));

    ValidationReport report = draftValidator.validate(Map.of("wf1", wf), Map.of());

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .hasSize(1)
        .extracting(ValidationError::code)
        .containsExactly("validateBlueprintRefs");
  }

  // Regression coverage: before this fix, WorkflowDraftValidator never invoked
  // validateReachableStepIdUniqueness at all (the pre-shared-suite check list omitted it); switching
  // to the shared ValidationCheck.suite() now runs it for drafts too.
  @Test
  void validate_reportsReachableStepIdAmbiguity() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));
    StepDefinition dupInSubA = StepDefinition.builder()
        .withStepId("dup")
        .withName("Dup")
        .withBehaviour(new AgentBehaviour("ok", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    StepDefinition dupInSubB = StepDefinition.builder()
        .withStepId("dup")
        .withName("Dup")
        .withBehaviour(new AgentBehaviour("ok", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition subA = new WorkflowDefinition(
        "sub-a", "SubA", "d", null, null, null, null, WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(dupInSubA));
    WorkflowDefinition subB = new WorkflowDefinition(
        "sub-b", "SubB", "d", null, null, null, null, WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(dupInSubB));
    StepDefinition callA = StepDefinition.builder()
        .withStepId("call-a")
        .withName("CallA")
        .withBehaviour(new WorkflowBehaviour("sub-a", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    StepDefinition callB = StepDefinition.builder()
        .withStepId("call-b")
        .withName("CallB")
        .withBehaviour(new WorkflowBehaviour("sub-b", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = new WorkflowDefinition(
        "root", "Root", "d", null, null, null, null, WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(callA, callB));
    AgentDefinition agent = AgentDefinition.builder()
        .withId("ok")
        .withName("Ok")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();

    ValidationReport report = draftValidator.validate(
        Map.of("root", root, "sub-a", subA, "sub-b", subB), Map.of("ok", agent));

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .anyMatch(e -> "validateReachableStepIdUniqueness".equals(e.code()));
  }
}
