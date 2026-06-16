// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

/**
 * Optional run/workflow/step/agent identity for a single LLM invocation.
 * <p>
 * Carried by {@link LlmExecutionRequest} so providers that key behaviour on the originating run can resolve it at the
 * {@link LlmClient#execute(LlmExecutionRequest)} boundary, where the request payload alone is otherwise anonymous. Real
 * network providers ignore this; it exists for deterministic or run-aware clients (for example the fake provider).
 * Every component is nullable: the whole record is {@code null} for direct, run-less {@code LlmClient} use, and
 * individual components are {@code null} when the corresponding identifier is not yet assigned or not applicable.
 *
 * @param workflowId id of the <strong>innermost active</strong> workflow for this call — the nested workflow currently
 *                   executing, or the run's root workflow when not nested. This is not necessarily the run's root
 *                   workflow id ({@code runId} identifies the single run); it distinguishes steps of different nested
 *                   workflows under one run. May be {@code null} for direct, run-less use.
 * @param runId      current run id, or {@code null} when not yet assigned
 * @param stepId     current step id, or {@code null} when not step-scoped
 * @param agentId    current agent id, or {@code null} when not agent-scoped
 */
public record LlmInvocationIdentity(
    String workflowId,
    String runId,
    String stepId,
    String agentId) {

}
