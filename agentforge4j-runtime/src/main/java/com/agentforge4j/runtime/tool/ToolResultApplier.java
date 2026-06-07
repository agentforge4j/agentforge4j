package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import org.apache.commons.lang3.StringUtils;

/**
 * The single routine that applies a successful {@link ToolResult} to {@link WorkflowState}, used
 * identically by the inline ALLOW path ({@code ToolInvocationCommandHandler}) and the
 * approved-resume path ({@code DefaultWorkflowRuntime#continueAfterToolApproval}) so both produce
 * the same state shape.
 *
 * <p>Tool output is runtime-produced, so it is written under a reserved {@code tool.<capability>}
 * key tagged with the current step's execution uid and is not subject to agent {@code outputKeys}
 * gating.
 */
public final class ToolResultApplier {

  /**
   * Reserved context-key prefix for tool results.
   */
  public static final String TOOL_CONTEXT_KEY_PREFIX = "tool.";

  /**
   * Reserved context-key suffix for a tool error (denied or failed), written under
   * {@code tool.<capability>.error}.
   */
  public static final String TOOL_ERROR_KEY_SUFFIX = ".error";

  private final EventRecorder eventRecorder;

  /**
   * Creates the applier.
   *
   * @param eventRecorder sink for the {@code CONTEXT_UPDATED} event
   */
  public ToolResultApplier(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  /**
   * Writes a successful tool result into workflow context.
   *
   * @param capability the logical capability whose result this is
   * @param result     the successful tool result; ignored when {@code null} or without output
   * @param state      the workflow state to update
   * @param actorId    actor recorded on the context-update event
   */
  public void apply(String capability, ToolResult result, WorkflowState state, String actorId) {
    Validate.notBlank(capability, "capability must not be blank");
    Validate.notNull(state, "state must not be null");
    if (result == null || result.output() == null) {
      return;
    }
    String key = TOOL_CONTEXT_KEY_PREFIX + capability;
    state.putContextValue(key, new StringContextValue(result.output()));
    String currentStepId = state.getCurrentStepId();
    if (StringUtils.isNotBlank(currentStepId)) {
      Integer uid = state.getStepExecutionUid().get(currentStepId);
      if (uid != null) {
        state.putContextKeyWrittenAtUid(key, uid);
      }
    }
    eventRecorder.record(state.getRunId(), currentStepId, WorkflowEventType.CONTEXT_UPDATED,
        "tool '%s' result stored in context key '%s'".formatted(capability, key), actorId);
  }

  /**
   * Writes a tool error (denial or failure detail) into workflow context under
   * {@code tool.<capability>.error}, so a subsequent step or agent can branch on it. Used by the
   * operator continue path and by a rejected approval.
   *
   * @param capability the logical capability whose error this is
   * @param reason     human-readable denial or failure detail; {@code null} becomes an empty
   *                   string
   * @param state      the workflow state to update
   * @param actorId    actor recorded on the context-update event
   */
  public void applyError(String capability, String reason, WorkflowState state, String actorId) {
    Validate.notBlank(capability, "capability must not be blank");
    Validate.notNull(state, "state must not be null");
    String key = TOOL_CONTEXT_KEY_PREFIX + capability + TOOL_ERROR_KEY_SUFFIX;
    state.putContextValue(key, new StringContextValue(StringUtils.defaultString(reason)));
    String currentStepId = state.getCurrentStepId();
    if (StringUtils.isNotBlank(currentStepId)) {
      Integer uid = state.getStepExecutionUid().get(currentStepId);
      if (uid != null) {
        state.putContextKeyWrittenAtUid(key, uid);
      }
    }
    eventRecorder.record(state.getRunId(), currentStepId, WorkflowEventType.CONTEXT_UPDATED,
        "tool '%s' error stored in context key '%s'".formatted(capability, key), actorId);
  }
}
