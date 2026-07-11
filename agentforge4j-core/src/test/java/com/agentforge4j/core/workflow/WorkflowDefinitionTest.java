// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionTest {

  private static StepDefinition failStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName("Step " + stepId)
        .withBehaviour(new FailBehaviour("r"))
        .build();
  }

  private static WorkflowDefinition workflow(
      String id,
      String name,
      String uuid,
      WorkflowSource source,
      WorkflowLifecycle lifecycle,
      Map<String, ArtifactDefinition> artifacts,
      Map<String, com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition> blueprints,
      List<Executable> steps) {
    return new WorkflowDefinition(
        id,
        name,
        null,
        null,
        null,
        "1.0.0",
        uuid,
        source,
        lifecycle,
        artifacts,
        blueprints,
        steps,
        List.of(),
        List.of());
  }

  private static WorkflowDefinition minimal(String id, String name, List<Executable> steps) {
    return workflow(id, name, null, null, null, null, null, steps);
  }

  @Test
  void applies_defaults_for_optional_fields() {
    WorkflowDefinition wf = workflow("w1", "One", "  ", null, null, null, null, List.of(failStep("s1")));

    assertThat(wf.uuid()).isNull();
    assertThat(wf.source()).isEqualTo(WorkflowSource.CUSTOM);
    assertThat(wf.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
    assertThat(wf.artifacts()).isEmpty();
    assertThat(wf.blueprints()).isEmpty();
  }

  @Test
  void preserves_non_blank_uuid() {
    WorkflowDefinition wf = workflow(
        "w1",
        "One",
        "550e8400-e29b-41d4-a716-446655440000",
        null,
        null,
        null,
        null,
        List.of(failStep("s1")));
    assertThat(wf.uuid()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_id(String id) {
    assertThatThrownBy(() -> minimal(id, "N", List.of(failStep("s1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WorkflowDefinition id must not be blank");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_name(String name) {
    assertThatThrownBy(() -> minimal("w1", name, List.of(failStep("s1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WorkflowDefinition name must not be blank for workflow: w1");
  }

  @Test
  void rejects_empty_steps() {
    assertThatThrownBy(() -> minimal("w1", "One", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WorkflowDefinition steps must not be empty for workflow: w1");
  }

  @Test
  void steps_list_is_immutable_to_callers() {
    List<Executable> steps = new ArrayList<>(List.of(failStep("s1")));
    WorkflowDefinition wf = minimal("w1", "One", steps);
    steps.add(failStep("s2"));

    assertThat(wf.steps()).hasSize(1);
    assertThatThrownBy(() -> wf.steps().add(failStep("x")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void artifacts_are_snapshot_and_unmodifiable() {
    var item = new TextArtifactItem("q1", "Question", false, null);
    var art = new ArtifactDefinition("art", List.of(item));
    Map<String, ArtifactDefinition> map = new HashMap<>();
    map.put("art", art);
    WorkflowDefinition wf = workflow("w1", "One", null, null, null, map, null, List.of(failStep("s1")));
    map.clear();

    assertThat(wf.artifacts()).containsKey("art");
    assertThatThrownBy(() -> wf.artifacts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void builder_maps_every_field_to_its_record_component() {
    var item = new TextArtifactItem("q1", "Question", false, null);
    Map<String, ArtifactDefinition> artifacts = Map.of("art", new ArtifactDefinition("art", List.of(item)));
    Map<String, BlueprintDefinition> blueprints = Map.of("bp", new BlueprintDefinition(
        "bp", "Blueprint", new BlueprintBehaviour(null, StepTransition.AUTO), List.of(failStep("b1"))));
    List<Executable> steps = List.of(failStep("s1"));
    List<WorkflowRequirement> requirements = List.of(new WorkflowRequirement(
        "req-1", "role", RequirementScope.WORKFLOW, null, null, true, null, ResolutionMode.INSTALL));

    WorkflowDefinition wf = WorkflowDefinition.builder()
        .withId("w1")
        .withName("One")
        .withDescription("the description")
        .withAuthor("the author")
        .withContact("the contact")
        .withVersion("2.3.4")
        .withUuid("550e8400-e29b-41d4-a716-446655440000")
        .withSource(WorkflowSource.SHIPPED)
        .withLifecycle(WorkflowLifecycle.DRAFT)
        .withArtifacts(artifacts)
        .withBlueprints(blueprints)
        .withSteps(steps)
        .withRequirements(requirements)
        .build();

    assertThat(wf.id()).isEqualTo("w1");
    assertThat(wf.name()).isEqualTo("One");
    assertThat(wf.description()).isEqualTo("the description");
    assertThat(wf.author()).isEqualTo("the author");
    assertThat(wf.contact()).isEqualTo("the contact");
    assertThat(wf.version()).isEqualTo("2.3.4");
    assertThat(wf.uuid()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    assertThat(wf.source()).isEqualTo(WorkflowSource.SHIPPED);
    assertThat(wf.lifecycle()).isEqualTo(WorkflowLifecycle.DRAFT);
    assertThat(wf.artifacts()).isEqualTo(artifacts);
    assertThat(wf.blueprints()).isEqualTo(blueprints);
    assertThat(wf.steps()).isEqualTo(steps);
    assertThat(wf.requirements()).isEqualTo(requirements);
  }

  @Test
  void builder_applies_defaults_when_optional_setters_are_skipped() {
    WorkflowDefinition wf = WorkflowDefinition.builder()
        .withId("w1")
        .withName("One")
        .withSteps(List.of(failStep("s1")))
        .build();

    assertThat(wf.description()).isNull();
    assertThat(wf.author()).isNull();
    assertThat(wf.contact()).isNull();
    assertThat(wf.version()).isNull();
    assertThat(wf.uuid()).isNull();
    assertThat(wf.source()).isEqualTo(WorkflowSource.CUSTOM);
    assertThat(wf.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
    assertThat(wf.artifacts()).isEmpty();
    assertThat(wf.blueprints()).isEmpty();
    assertThat(wf.requirements()).isEmpty();
  }

  @Test
  void builder_defers_required_field_validation_to_build() {
    assertThatThrownBy(() -> WorkflowDefinition.builder()
        .withName("One")
        .withSteps(List.of(failStep("s1")))
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WorkflowDefinition id must not be blank");

    assertThatThrownBy(() -> WorkflowDefinition.builder()
        .withId("w1")
        .withSteps(List.of(failStep("s1")))
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WorkflowDefinition name must not be blank for workflow: w1");

    assertThatThrownBy(() -> WorkflowDefinition.builder()
        .withId("w1")
        .withName("One")
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WorkflowDefinition steps must not be empty for workflow: w1");
  }
}
