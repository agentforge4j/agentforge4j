package com.agentforge4j.core.workflow.requirement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRequirementTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void blankId_isRejected() {
    assertThatThrownBy(() -> new WorkflowRequirement(" ", "rbac", RequirementScope.WORKFLOW,
        null, null, true, null, ResolutionMode.INSTALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void blankType_isRejected() {
    assertThatThrownBy(() -> new WorkflowRequirement("r", " ", RequirementScope.WORKFLOW,
        null, null, true, null, ResolutionMode.INSTALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type");
  }

  @Test
  void nullScope_isRejected() {
    assertThatThrownBy(() -> new WorkflowRequirement("r", "rbac", null,
        null, null, true, null, ResolutionMode.INSTALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scope");
  }

  @Test
  void nullResolution_isRejected() {
    assertThatThrownBy(() -> new WorkflowRequirement("r", "rbac", RequirementScope.WORKFLOW,
        null, null, true, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolution");
  }

  @Test
  void workflowScope_rejectsStepId() {
    assertThatThrownBy(() -> new WorkflowRequirement("r", "rbac", RequirementScope.WORKFLOW,
        "s1", null, true, null, ResolutionMode.INSTALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WORKFLOW");
  }

  @Test
  void stepScope_requiresStepId() {
    assertThatThrownBy(() -> new WorkflowRequirement("r", "rbac", RequirementScope.STEP,
        null, null, true, null, ResolutionMode.INSTALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("STEP scope requires a stepId");
  }

  @Test
  void stepActionScope_requiresAction() {
    assertThatThrownBy(() -> new WorkflowRequirement("r", "rbac", RequirementScope.STEP_ACTION,
        "s1", null, true, null, ResolutionMode.INSTALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires an action");
  }

  @Test
  void blankOptionalFields_areNormalisedToNull() {
    WorkflowRequirement requirement = new WorkflowRequirement("r", "rbac",
        RequirementScope.WORKFLOW, "  ", "  ", false, "  ", ResolutionMode.RUN_START);
    assertThat(requirement.stepId()).isNull();
    assertThat(requirement.action()).isNull();
    assertThat(requirement.defaultJson()).isNull();
  }

  @Test
  void deserialisesDefaultObjectAsRawJsonString() throws Exception {
    String json = "{\"id\":\"run-access\",\"type\":\"rbac_runner_allowed\",\"scope\":\"WORKFLOW\","
        + "\"required\":true,\"default\":{\"mode\":\"all\"},\"resolution\":\"INSTALL\"}";

    WorkflowRequirement requirement = MAPPER.readValue(json, WorkflowRequirement.class);

    assertThat(requirement.id()).isEqualTo("run-access");
    assertThat(requirement.scope()).isEqualTo(RequirementScope.WORKFLOW);
    assertThat(requirement.required()).isTrue();
    assertThat(requirement.resolution()).isEqualTo(ResolutionMode.INSTALL);
    assertThat(requirement.defaultJson()).isEqualTo("{\"mode\":\"all\"}");
  }

  @Test
  void serialisesRawDefaultWithoutQuoting() throws Exception {
    WorkflowRequirement requirement = new WorkflowRequirement("run-access",
        "rbac_runner_allowed", RequirementScope.WORKFLOW, null, null, true,
        "{\"mode\":\"all\"}", ResolutionMode.INSTALL);

    String json = MAPPER.writeValueAsString(requirement);

    assertThat(json).contains("\"default\":{\"mode\":\"all\"}");
  }

  @Test
  void stepActionDeclaration_roundTrips() throws Exception {
    WorkflowRequirement requirement = new WorkflowRequirement("cv-review", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, "review-cv", "REVIEW", true, null,
        ResolutionMode.INSTALL_OR_RUN_START);

    WorkflowRequirement parsed = MAPPER.readValue(MAPPER.writeValueAsString(requirement),
        WorkflowRequirement.class);

    assertThat(parsed).isEqualTo(requirement);
  }
}
