package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.util.Validate;

import java.util.List;

/**
 * The parsed result of a single LLM call: the raw response text plus the parsed list of commands.
 *
 * <p>Keeping the raw text alongside the commands allows the runtime to record
 * the original output on the event log for auditing, even after the structured commands have been
 * applied.
 */
public record AgentInvocationResult(String rawResponse, List<LlmCommand> commands) {

  public AgentInvocationResult {
    Validate.notBlank(rawResponse, "AgentInvocationResult rawResponse must not be blank");
    Validate.notNull(commands, "AgentInvocationResult commands must not be null");
    commands = List.copyOf(commands);
  }
}
