package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
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
    return new StepDefinition(stepId, "Step " + stepId, new FailBehaviour("r"), null, null);
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
        steps);
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
}
