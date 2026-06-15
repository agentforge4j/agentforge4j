package com.agentforge4j.llm.fake;

/**
 * Counter key for the per-sequence ordinal, held inside the per-run store (never on the client). A separate ordinal
 * sequence is maintained for each distinct {@code (runId, workflowId, stepId, agentId)} tuple, so two agents in one
 * step, or the same agent across loop re-entries, advance independently.
 *
 * @param runId      run id
 * @param workflowId innermost active workflow id
 * @param stepId     step id
 * @param agentId    agent id
 */
public record OrdinalKey(String runId, String workflowId, String stepId, String agentId) {

}
