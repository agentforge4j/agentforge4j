// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.exception.RequirementResolutionException;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Exercises the OSS run-start requirement checkpoint and the deferred first-use hook over the real
 * {@link WorkflowRuntimeBuilder} graph. The workflow uses a single {@code RESOURCE} step, so no LLM is involved.
 */
class WorkflowRuntimeRequirementCheckpointIT {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private static final RequirementResolver ALWAYS_EMPTY = (requirement, context) -> null;

  @Test
  void requiredRunStartRequirement_unresolved_failsFastBeforePersistence() {
    WorkflowDefinition workflow = workflow(List.of(
        new WorkflowRequirement("run-access", "rbac_runner_allowed", RequirementScope.WORKFLOW,
            null, null, true, null, ResolutionMode.RUN_START)));
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    WorkflowRuntime runtime = runtime(workflow, stateRepository, eventLog, ALWAYS_EMPTY);

    assertThatThrownBy(() -> runtime.start(workflow.id()))
        .isInstanceOf(RequirementResolutionException.class)
        .hasMessageContaining("run-access");

    assertThat(stateRepository.findAll()).isEmpty();
  }

  @Test
  void requiredRequirement_withDeclaredDefault_resolvesViaDefaultResolver_completes() {
    WorkflowDefinition workflow = workflow(List.of(
        new WorkflowRequirement("run-access", "rbac_runner_allowed", RequirementScope.WORKFLOW,
            null, null, true, "{\"mode\":\"all\"}", ResolutionMode.INSTALL)));
    WorkflowRuntime runtime = runtime(workflow, new InMemoryWorkflowStateRepository(),
        new InMemoryWorkflowEventLog(), null);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void nonRequiredRequirement_unresolved_doesNotBlockRun() {
    WorkflowDefinition workflow = workflow(List.of(
        new WorkflowRequirement("optional", "public_step_access", RequirementScope.WORKFLOW,
            null, null, false, null, ResolutionMode.RUN_START)));
    WorkflowRuntime runtime = runtime(workflow, new InMemoryWorkflowStateRepository(),
        new InMemoryWorkflowEventLog(), ALWAYS_EMPTY);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void deferredRequirement_unresolved_failsAtFirstUse() {
    WorkflowDefinition workflow = workflow(List.of(
        new WorkflowRequirement("late", "rbac_step_action_allowed", RequirementScope.STEP,
            "s1", null, true, null, ResolutionMode.DEFERRED)));
    WorkflowRuntime runtime = runtime(workflow, new InMemoryWorkflowStateRepository(),
        new InMemoryWorkflowEventLog(), ALWAYS_EMPTY);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void nestedWorkflow_requiredRequirement_unresolved_failsRunAtEntry() {
    WorkflowDefinition nested = nestedWorkflow(List.of(
        new WorkflowRequirement("nested-access", "rbac_runner_allowed", RequirementScope.WORKFLOW,
            null, null, true, null, ResolutionMode.RUN_START)));
    WorkflowDefinition parent = parentWorkflow(nested.id());
    WorkflowRuntime runtime = runtime(Map.of(parent.id(), parent, nested.id(), nested), ALWAYS_EMPTY);

    String runId = runtime.start(parent.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void nestedWorkflow_requiredRequirement_withDefault_resolvesAtEntry_completes() {
    WorkflowDefinition nested = nestedWorkflow(List.of(
        new WorkflowRequirement("nested-access", "rbac_runner_allowed", RequirementScope.WORKFLOW,
            null, null, true, "{\"mode\":\"all\"}", ResolutionMode.INSTALL)));
    WorkflowDefinition parent = parentWorkflow(nested.id());
    WorkflowRuntime runtime = runtime(Map.of(parent.id(), parent, nested.id(), nested), null);

    String runId = runtime.start(parent.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void nestedWorkflow_deferredRequirement_unresolved_failsAtFirstUse_againstDeclaringWorkflow() {
    WorkflowDefinition nested = nestedWorkflow(List.of(
        new WorkflowRequirement("nested-late", "rbac_step_action_allowed", RequirementScope.STEP,
            "n1", null, true, null, ResolutionMode.DEFERRED)));
    WorkflowDefinition parent = parentWorkflow(nested.id());
    WorkflowRuntime runtime = runtime(Map.of(parent.id(), parent, nested.id(), nested), ALWAYS_EMPTY);

    String runId = runtime.start(parent.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void deferredRequirement_onBlueprintInternalStep_failsAtFirstUse() {
    StepDefinition inner = StepDefinition.builder()
        .withStepId("bp-inner")
        .withName("bp-inner")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "inner.out", StepTransition.AUTO))
        .build();
    BlueprintDefinition blueprint = new BlueprintDefinition("bp", "bp",
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(inner));
    WorkflowDefinition workflow = new WorkflowDefinition("wf-bp-req", "wf-bp-req", null, null, null,
        null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of("bp", blueprint),
        List.<Executable>of(new BlueprintRef("bp")),
        List.of(new WorkflowRequirement("bp-late", "rbac_step_action_allowed", RequirementScope.STEP,
            "bp-inner", null, true, null, ResolutionMode.DEFERRED)),
        List.of());
    WorkflowRuntime runtime = runtime(Map.of(workflow.id(), workflow), ALWAYS_EMPTY);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  private static WorkflowDefinition workflow(List<WorkflowRequirement> requirements) {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("s1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .build();
    return new WorkflowDefinition("wf-req", "wf-req", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        requirements, List.of());
  }

  private static WorkflowDefinition nestedWorkflow(List<WorkflowRequirement> requirements) {
    StepDefinition step = StepDefinition.builder()
        .withStepId("n1")
        .withName("n1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "nested.out", StepTransition.AUTO))
        .build();
    return new WorkflowDefinition("wf-nested", "wf-nested", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        requirements, List.of());
  }

  private static WorkflowDefinition parentWorkflow(String nestedRef) {
    StepDefinition callNested = StepDefinition.builder()
        .withStepId("call-nested")
        .withName("call-nested")
        .withBehaviour(new WorkflowBehaviour(nestedRef, StepTransition.AUTO))
        .build();
    return new WorkflowDefinition("wf-parent", "wf-parent", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.<Executable>of(callNested), List.of(), List.of());
  }

  private static WorkflowRuntime runtime(Map<String, WorkflowDefinition> workflows,
      RequirementResolver requirementResolver) {
    WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(workflows))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .agentInvoker(mock(AgentInvoker.class))
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER);
    if (requirementResolver != null) {
      builder.requirementResolver(requirementResolver);
    }
    return builder.build();
  }

  private static WorkflowRuntime runtime(WorkflowDefinition workflow,
      InMemoryWorkflowStateRepository stateRepository,
      InMemoryWorkflowEventLog eventLog,
      RequirementResolver requirementResolver) {
    WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(mock(AgentInvoker.class))
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER);
    if (requirementResolver != null) {
      builder.requirementResolver(requirementResolver);
    }
    return builder.build();
  }
}
