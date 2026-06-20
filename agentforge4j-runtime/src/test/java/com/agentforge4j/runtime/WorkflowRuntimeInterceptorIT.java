// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.interceptor.RunExecutionContext;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * End-to-end {@link RunExecutionInterceptor} behaviour over the real {@link WorkflowRuntimeBuilder} graph: a run that
 * genuinely pauses on its first ({@code INPUT}) step and then resumes exercises the real
 * {@code stepExecutionUid().isEmpty()} first-entry gate, proving {@code beforeMainExecution} fires once and not again
 * on resume.
 */
class WorkflowRuntimeInterceptorIT {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void beforeMainExecutionFiresOnceAcrossAGenuinePauseAndResume() {
    ArtifactDefinition artifact = new ArtifactDefinition(
        "form", List.of(new TextArtifactItem("field", "Field", true, null)));
    StepDefinition inputStep = StepDefinition.builder()
        .withStepId("in")
        .withName("in")
        .withBehaviour(new InputBehaviour("form", StepTransition.AUTO))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf-input", "wf-input", null, null, null,
        null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form", artifact),
        Map.of(), List.of(inputStep));

    List<RunExecutionContext> mainEntries = new ArrayList<>();
    RunExecutionInterceptor recording = new RunExecutionInterceptor() {
      @Override
      public void beforeMainExecution(RunExecutionContext context) {
        mainEntries.add(context);
      }
    };
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .agentInvoker(mock(AgentInvoker.class))
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .runExecutionInterceptor(recording)
        .build();

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(mainEntries).hasSize(1);                 // fired once at first entry

    runtime.submitInput(runId, Map.of("field", "answer"), "user");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(mainEntries).hasSize(1);                 // not re-fired on resume
  }
}
