package com.agentforge4j.starter.logging;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;

public final class RunMdcContext implements AutoCloseable {

  private static final String RUN_ID = "runId";
  private static final String WORKFLOW_ID = "workflowId";
  private static final String STEP_ID = "stepId";
  private static final String AGENT_ID = "agentId";

  private final Map<String, String> previousValues = new HashMap<>();

  private RunMdcContext(String runId, String workflowId, String stepId, String agentId) {
    put(RUN_ID, runId);
    put(WORKFLOW_ID, workflowId);
    put(STEP_ID, stepId);
    put(AGENT_ID, agentId);
  }

  public static RunMdcContext of(String runId, String workflowId) {
    return new RunMdcContext(runId, workflowId, null, null);
  }

  public static RunMdcContext of(String runId, String workflowId, String stepId, String agentId) {
    return new RunMdcContext(runId, workflowId, stepId, agentId);
  }

  public RunMdcContext withStep(String stepId) {
    put(STEP_ID, stepId);
    return this;
  }

  public RunMdcContext withAgent(String agentId) {
    put(AGENT_ID, agentId);
    return this;
  }

  private void put(String key, String value) {
    if (!previousValues.containsKey(key)) {
      previousValues.put(key, MDC.get(key));
    }
    if (value == null || value.isBlank()) {
      MDC.remove(key);
      return;
    }
    MDC.put(key, value);
  }

  @Override
  public void close() {
    restore(RUN_ID);
    restore(WORKFLOW_ID);
    restore(STEP_ID);
    restore(AGENT_ID);
  }

  private void restore(String key) {
    String previous = previousValues.get(key);
    if (previous == null) {
      MDC.remove(key);
      return;
    }
    MDC.put(key, previous);
  }
}
