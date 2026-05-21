package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.util.Validate;

import java.util.List;

/**
 * The parsed result of a single LLM call: the raw response text plus the parsed list of commands.
 *
 * <p>Keeping the raw text alongside the commands allows the runtime to record
 * the original output on the event log for auditing, even after the structured commands have been
 * applied.
 *
 * @param rawResponse raw model output text before command parsing
 * @param commands    parsed commands from the response
 * @param modelUsed   the concrete model the provider ran; sourced from
 *                    {@link com.agentforge4j.llm.api.LlmExecutionResponse#modelUsed()}; nullable —
 *                    absent when invocation did not go through an LLM (e.g. test doubles) or when
 *                    the provider did not report it
 * @param tokenUsage  token counts for this invocation; sourced from
 *                    {@link com.agentforge4j.llm.api.LlmExecutionResponse#tokenUsage()}; nullable —
 *                    absent when invocation did not go through an LLM or when the provider did not
 *                    report usage
 */
public record AgentInvocationResult(
    String rawResponse,
    List<LlmCommand> commands,
    String modelUsed,
    TokenUsageReport tokenUsage) {

  public AgentInvocationResult {
    Validate.notBlank(rawResponse, "AgentInvocationResult rawResponse must not be blank");
    Validate.notNull(commands, "AgentInvocationResult commands must not be null");
    commands = List.copyOf(commands);
  }
}
