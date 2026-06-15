package com.agentforge4j.llm.fake;

/**
 * Intra-script lookup key for a single scripted response. A {@link FakeScript} is already per-run (selected by run id
 * at registration), so the run id is not part of this key.
 *
 * <p>This is a plain carrier used both for script entries (built from validated JSON by
 * {@link FakeScriptParser}) and for runtime lookups (built from the request's invocation identity). Lookup keys may
 * carry {@code null} components when the identity is incomplete; such a key simply fails to match any entry, yielding a
 * fail-closed miss.
 *
 * @param workflowId innermost active workflow id
 * @param stepId     step id
 * @param agentId    agent id
 * @param ordinal    zero-based call ordinal within this {@code (workflowId, stepId, agentId)} sequence for the run
 */
public record FakeScriptKey(String workflowId, String stepId, String agentId, int ordinal) {

}
