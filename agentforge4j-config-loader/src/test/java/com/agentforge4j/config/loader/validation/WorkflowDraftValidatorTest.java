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

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDraftValidatorTest {

  @Test
  void validate_emptyWorkflows_isValid() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());

    ValidationReport report = draftValidator.validate(Map.of(), Map.of());

    assertThat(report.isValid()).isTrue();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void validate_collectsAgentRefFailuresWithoutThrowing() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());
    StepDefinition step = new StepDefinition("s1", "S1",
        new AgentBehaviour("missing-agent", StepTransition.AUTO, null),
        new ContextMapping(List.of(), List.of()), null, null);
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
        new WorkflowDraftValidator(new WorkflowValidator());
    StepDefinition step = new StepDefinition("s1", "S1",
        new AgentBehaviour("ok", StepTransition.AUTO, null),
        new ContextMapping(List.of(), List.of()), null, null);
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step));
    AgentDefinition agent = new AgentDefinition("ok", "Ok", AgentLocality.CLOUD, true, "sys",
        List.of(new ProviderPreference("openai", "gpt-4o-mini")), List.of("COMPLETE"), null, null,
        "1.0.0");

    ValidationReport report =
        draftValidator.validate(Map.of("wf1", wf), Map.of("ok", agent));

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void validate_accumulatesMultipleCheckFailures() {
    WorkflowDraftValidator draftValidator =
        new WorkflowDraftValidator(new WorkflowValidator());
    StepDefinition badAgent = new StepDefinition("s1", "S1",
        new AgentBehaviour("missing", StepTransition.AUTO, null),
        new ContextMapping(List.of(), List.of()), null, null);
    StepDefinition badRetry = new StepDefinition("s2", "S2",
        new RetryPreviousBehaviour("nope", RetryMode.FROM_STEP, 2, new BlueprintRef("bp")),
        new ContextMapping(List.of(), List.of()), null, null);
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(badAgent, badRetry));

    ValidationReport report = draftValidator.validate(Map.of("wf1", wf), Map.of());

    assertThat(report.isValid()).isFalse();
    assertThat(report.errors())
        .extracting(ValidationError::code)
        .contains("validateAgentRefs", "validateRetryStepRefs");
  }
}
