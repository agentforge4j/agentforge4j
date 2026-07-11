// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.repository;

import com.agentforge4j.core.exception.WorkflowNotFoundException;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryWorkflowRepositoryTest {

  @Test
  void get_returnsWorkflowFromCurrentSnapshot() {
    WorkflowDefinition wf = minimalWorkflow("wf-a", "A");
    InMemoryWorkflowRepository repository =
        new InMemoryWorkflowRepository(Map.of("wf-a", wf));

    assertThat(repository.get("wf-a").name()).isEqualTo("A");
  }

  @Test
  void get_throwsWhenWorkflowIsMissing() {
    InMemoryWorkflowRepository repository = new InMemoryWorkflowRepository(Map.of());

    assertThatThrownBy(() -> repository.get("missing"))
        .isInstanceOf(WorkflowNotFoundException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void get_rejectsBlankId() {
    InMemoryWorkflowRepository repository =
        new InMemoryWorkflowRepository(Map.of("x", minimalWorkflow("x", "X")));

    assertThatThrownBy(() -> repository.get(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Workflow id must not be blank");
  }

  @Test
  void findAll_returnsCopyOfSnapshot() {
    WorkflowDefinition wf = minimalWorkflow("wf-a", "A");
    InMemoryWorkflowRepository repository =
        new InMemoryWorkflowRepository(Map.of("wf-a", wf));

    assertThat(repository.findAll()).containsOnlyKeys("wf-a");
  }

  @Test
  void replace_swapsSnapshotForSubsequentReads() {
    WorkflowDefinition initial = minimalWorkflow("wf-a", "Initial");
    WorkflowDefinition replacement = minimalWorkflow("wf-b", "Replacement");
    InMemoryWorkflowRepository repository =
        new InMemoryWorkflowRepository(Map.of("wf-a", initial));

    repository.replace(Map.of("wf-b", replacement));

    assertThat(repository.findAll()).containsOnlyKeys("wf-b");
    assertThat(repository.get("wf-b").name()).isEqualTo("Replacement");
    assertThatThrownBy(() -> repository.get("wf-a"))
        .isInstanceOf(WorkflowNotFoundException.class);
  }

  private static WorkflowDefinition minimalWorkflow(String id, String name) {
    return new WorkflowDefinition(
        id, name, "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(),
        List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("S1")
            .withBehaviour(new FailBehaviour("stop"))
            .withContextMapping(new ContextMapping(List.of(), List.of()))
            .build()), List.of(), List.of());
  }
}
