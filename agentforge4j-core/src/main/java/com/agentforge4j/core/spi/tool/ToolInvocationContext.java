package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * Identity propagated through a single tool invocation, matching the existing command pipeline.
 *
 * @param runId   non-blank owning run id
 * @param stepUid step instance id ({@code currentStepUid} propagation), or {@code null}
 * @param agentId logical agent requesting the tool, or {@code null}
 * @param scope   non-null workflow/run scope
 */
public record ToolInvocationContext(String runId, String stepUid, String agentId, ToolScope scope) {

  /**
   * Validates that {@code runId} is non-blank and {@code scope} is non-null.
   */
  public ToolInvocationContext {
    Validate.notBlank(runId, "ToolInvocationContext runId must not be blank");
    Validate.notNull(scope, "ToolInvocationContext scope must not be null");
  }
}
