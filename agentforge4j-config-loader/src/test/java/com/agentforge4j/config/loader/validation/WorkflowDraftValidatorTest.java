// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDraftValidatorTest {

  @Test
  void validate_emptyWorkflows_isValid() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());

    ValidationReport report = draftValidator.validate(Map.of(), Map.of(), Set.of());

    assertThat(report.isValid()).isTrue();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void validate_collectsAgentRefFailuresWithoutThrowing() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("missing-agent", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step), List.of(), List.of());

    ValidationReport report =
        draftValidator.validate(Map.of("wf1", wf), Map.of(), Set.of());

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .anyMatch(e -> "validateAgentRefs".equals(e.code()))
        .anyMatch(e -> e.message().contains("missing-agent"));
  }

  @Test
  void validate_validWorkflowAndAgents_isValid() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("ok", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step), List.of(), List.of());
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
        draftValidator.validate(Map.of("wf1", wf), Map.of("ok", agent), Set.of());

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void validate_accumulatesMultipleCheckFailures() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());
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
        Map.of(), Map.of(), List.of(badAgent, badRetry), List.of(), List.of());

    ValidationReport report = draftValidator.validate(Map.of("wf1", wf), Map.of(), Set.of());

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .extracting(ValidationError::code)
        .contains("validateAgentRefs", "validateRetryStepRefs");
  }
}
