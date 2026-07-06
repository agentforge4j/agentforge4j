// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.logging;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

/**
 * Stacks run, workflow, step, and agent identifiers into {@link org.slf4j.MDC}; {@link #close()}
 * restores values that were present beforehand.
 *
 * <p>Keys correspond to fixed field names consumed by downstream logging pipelines.
 */
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

  /**
   * Puts identifiers into {@link MDC}, replacing blank strings with removals for the affected key.
   */
  public static RunMdcContext of(String runId, String workflowId, String stepId, String agentId) {
    return new RunMdcContext(runId, workflowId, stepId, agentId);
  }

  private void put(String key, String value) {
    if (!previousValues.containsKey(key)) {
      previousValues.put(key, MDC.get(key));
    }
    if (StringUtils.isBlank(value)) {
      MDC.remove(key);
      return;
    }
    MDC.put(key, value);
  }

  /** Restores earlier {@link MDC} values captured on first write of each key during this context. */
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
