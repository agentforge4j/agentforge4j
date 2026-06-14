package com.agentforge4j.core.spi.tool;

/**
 * OSS-clean execution scope identifying the workflow and run a tool invocation belongs to.
 *
 * <p>Carries no tenant, user, or role: OSS core and runtime never hold such identity. The embedding
 * application resolves any such identity inside its own {@link ToolPolicy} and
 * {@link ToolProviderResolver} implementations, never via this type. Either field may be
 * {@code null} when not yet known.
 *
 * @param workflowId id of the workflow, or {@code null}
 * @param runId      id of the run, or {@code null}
 */
public record ToolScope(String workflowId, String runId) {

}
