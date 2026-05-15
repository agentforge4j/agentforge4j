package com.agentforge4j.starter.logging;

import com.agentforge4j.runtime.RunContextManager;

/**
 * Binds correlation identifiers onto {@linkplain org.slf4j.MDC SLF4J MDC} for the duration each
 * {@link RunContextManager.Scope} stays open via {@link RunMdcContext}.
 */
public final class MdcRunContextManager implements RunContextManager {

  @Override
  public Scope open(String runId, String workflowId, String stepId, String agentId) {
    RunMdcContext context = RunMdcContext.of(runId, workflowId, stepId, agentId);
    return context::close;
  }
}
