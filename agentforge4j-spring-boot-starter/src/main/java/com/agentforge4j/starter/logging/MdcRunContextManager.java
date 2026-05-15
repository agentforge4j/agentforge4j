package com.agentforge4j.starter.logging;

import com.agentforge4j.runtime.RunContextManager;

public final class MdcRunContextManager implements RunContextManager {

  @Override
  public Scope open(String runId, String workflowId, String stepId, String agentId) {
    RunMdcContext context = RunMdcContext.of(runId, workflowId, stepId, agentId);
    return context::close;
  }
}
