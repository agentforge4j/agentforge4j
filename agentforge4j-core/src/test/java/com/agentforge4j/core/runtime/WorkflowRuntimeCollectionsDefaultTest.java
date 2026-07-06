// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.workflow.state.WorkflowState;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code collections()} is {@code default} precisely so an implementation with no
 * {@code COLLECTION}-step support compiles without providing one; this locks that contract in place
 * against a regression back to a mandatory abstract method.
 */
class WorkflowRuntimeCollectionsDefaultTest {

  @Test
  void defaultImplementationThrowsUnsupportedOperationException() {
    WorkflowRuntime runtime = new NoCollectionSupportRuntime();

    assertThatThrownBy(runtime::collections)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("does not support collection-gate operations");
  }

  private static final class NoCollectionSupportRuntime implements WorkflowRuntime {

    @Override
    public String start(String workflowId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void continueRun(String runId, String actorId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void retry(String runId, String stepId, String actorId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void approve(String runId, String stepId, String approverNote, String actorId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void submitInput(String runId, Map<String, String> answers, String actorId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void cancel(String runId, String actorId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void submitReview(String runId, String stepId, String reviewNote, String actorId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void decideStepApproval(String runId, String stepId, StepApprovalDecision decision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowState getState(String runId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowState continueAfterToolApproval(String runId, String toolInvocationId,
        ApprovalDecision decision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowState resolveToolDecision(String runId, String toolInvocationId,
        ToolDecision decision) {
      throw new UnsupportedOperationException();
    }
  }
}
