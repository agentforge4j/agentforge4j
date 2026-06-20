// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultApplierTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

  private final ToolResultApplier applier =
      new ToolResultApplier(new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK));

  @Test
  void apply_stamps_tool_output_as_external_tool() {
    WorkflowState state = stateAtStep("s1");

    applier.apply("web.search", ToolResult.success("\"result\"", 5), state, "agent-1");

    assertThat(state.getContextValue("tool.web.search").orElseThrow().provenance())
        .isEqualTo(ContextProvenance.EXTERNAL_TOOL);
  }

  @Test
  void applyError_stamps_tool_error_as_external_tool() {
    WorkflowState state = stateAtStep("s1");

    applier.applyError("web.search", "denied by policy", state, "agent-1");

    assertThat(state.getContextValue("tool.web.search.error").orElseThrow().provenance())
        .isEqualTo(ContextProvenance.EXTERNAL_TOOL);
  }

  private static WorkflowState stateAtStep(String stepId) {
    WorkflowState state =
        new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-06-15T00:00:00Z"));
    state.setCurrentStepId(stepId);
    return state;
  }
}
