// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

/**
 * Identity a {@link FakeLlmClient} passes to a {@link FakeResponseSource} for one invocation. The source selects the
 * script by {@link #runId()} and atomically advances the ordinal for the {@code (runId, workflowId, stepId, agentId)}
 * sequence — the client carries no ordinal and holds no per-run state.
 *
 * @param workflowId innermost active workflow id for this call
 * @param runId      run id (selects the per-run script and counters)
 * @param stepId     step id
 * @param agentId    agent id
 */
public record FakeInvocation(String workflowId, String runId, String stepId, String agentId) {

}
