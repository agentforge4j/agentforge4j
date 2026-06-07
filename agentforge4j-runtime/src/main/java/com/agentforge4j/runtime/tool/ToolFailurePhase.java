package com.agentforge4j.runtime.tool;

/**
 * Phase of {@link com.agentforge4j.core.spi.tool.ToolExecutionService} processing at which a tool
 * invocation failed; emitted in the {@code TOOL_INVOCATION_FAILED} event payload.
 */
enum ToolFailurePhase {
  RESOLVE,
  VALIDATE,
  INVOKE
}
