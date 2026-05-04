package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.util.Validate;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Mutable execution snapshot for one run: identity, status, pending gates, context maps, and
 * failure details. Field mutators are Lombok-generated except where noted on fields.
 */
@Getter
public final class WorkflowState {

  private final String runId;
  private final String workflowId;
  private final String parentRunId;
  private final Instant startedAt;

  @Setter
  private String currentStepId;
  @Setter
  private WorkflowStatus status;
  @Setter
  private Instant lastUpdatedAt;
  @Setter
  private ArtifactDefinition pendingArtifact;
  @Setter
  private String pendingUserPrompt;
  @Setter
  private RunFailure runFailure;

  private final Map<String, String> stepOutputs;
  private final Map<String, ContextValue> context;
  /**
   * Maps stepId to the monotonically increasing uid assigned when that step last began executing.
   */
  private final Map<String, Integer> stepExecutionUid;
  /**
   * Maps each context key to the uid of the step that last wrote it.
   */
  private final Map<String, Integer> contextKeyWrittenAtUid;

  /**
   * Creates a new run in {@link WorkflowStatus#RUNNING} with empty context and step output maps.
   *
   * @param runId       non-blank unique run id
   * @param workflowId  non-blank workflow definition id
   * @param parentRunId optional parent run when nested; may be {@code null}
   * @param startedAt   non-null start instant also used as initial {@link #lastUpdatedAt}
   * @throws IllegalArgumentException when {@code runId}, {@code workflowId}, or {@code startedAt}
   *                                  violates validation rules
   */
  public WorkflowState(
      String runId,
      String workflowId,
      String parentRunId,
      Instant startedAt) {
    this.runId = Validate.notBlank(runId, "WorkflowState runId must not be blank");
    this.workflowId = Validate.notBlank(workflowId, "WorkflowState workflowId must not be blank");
    this.parentRunId = parentRunId;
    this.startedAt = Validate.notNull(startedAt, "WorkflowState startedAt must not be null");
    this.status = WorkflowStatus.RUNNING;
    this.stepOutputs = new HashMap<>();
    this.context = new HashMap<>();
    this.stepExecutionUid = new HashMap<>();
    this.contextKeyWrittenAtUid = new HashMap<>();
    this.lastUpdatedAt = startedAt;
  }

  /**
   * Returns {@link RunFailure#failureReason()} when {@code runFailure} is set; otherwise
   * {@code null}.
   */
  public String getFailureReason() {
    return runFailure == null ? null : runFailure.failureReason();
  }

  /**
   * Returns {@link RunFailure#failedStepId()} when {@code runFailure} is set; otherwise
   * {@code null}.
   */
  public String getFailedStepId() {
    return runFailure == null ? null : runFailure.failedStepId();
  }

  /**
   * Returns {@link RunFailure#supportId()} when {@code runFailure} is set; otherwise {@code null}.
   */
  public String getSupportId() {
    return runFailure == null ? null : runFailure.supportId();
  }
}
