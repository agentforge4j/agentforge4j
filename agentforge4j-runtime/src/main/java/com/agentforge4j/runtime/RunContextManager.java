package com.agentforge4j.runtime;

/**
 * Optional contextual scope hook used by runtime callers to populate correlation metadata (for
 * example, MDC) without coupling runtime to logging framework APIs.
 */
@FunctionalInterface
public interface RunContextManager {

  Scope NO_OP_SCOPE = () -> {
  };

  RunContextManager NO_OP = (runId, workflowId, stepId, agentId) -> NO_OP_SCOPE;

  Scope open(String runId, String workflowId, String stepId, String agentId);

  @FunctionalInterface
  interface Scope extends AutoCloseable {

    @Override
    void close();
  }
}
