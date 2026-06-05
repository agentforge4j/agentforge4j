package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.llm.api.ModelTier;
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
 * @param rawResponse        raw model output text before command parsing
 * @param commands           parsed commands from the response
 * @param modelUsed          the concrete model the provider ran; sourced from
 *                           {@link com.agentforge4j.llm.api.LlmExecutionResponse#modelUsed()};
 *                           nullable — absent when invocation did not go through an LLM (e.g. test
 *                           doubles) or when the provider did not report it
 * @param tokenUsage         token counts for this invocation; sourced from
 *                           {@link com.agentforge4j.llm.api.LlmExecutionResponse#tokenUsage()};
 *                           nullable — absent when invocation did not go through an LLM or when the
 *                           provider did not report usage
 * @param resolvedModel      the model string the runtime resolved and sent on the request; non-null
 *                           for {@link ModelSource#PIN} and {@link ModelSource#TIER}, {@code null}
 *                           for {@link ModelSource#PROVIDER_DEFAULT} (no model was sent)
 * @param modelSource        how {@code resolvedModel} was determined; never {@code null}
 * @param requestedModelTier the capability tier requested for this call (step tier overriding agent
 *                           tier), or {@code null} when no tier applied
 */
public record AgentInvocationResult(
    String rawResponse,
    List<LlmCommand> commands,
    String modelUsed,
    TokenUsageReport tokenUsage,
    String resolvedModel,
    ModelSource modelSource,
    ModelTier requestedModelTier) {

  public AgentInvocationResult {
    Validate.notBlank(rawResponse, "AgentInvocationResult rawResponse must not be blank");
    Validate.notNull(commands, "AgentInvocationResult commands must not be null");
    Validate.notNull(modelSource, "AgentInvocationResult modelSource must not be null");
    commands = List.copyOf(commands);
  }

  /**
   * Backward-compatible constructor for callers (chiefly tests and non-LLM paths) that do not carry
   * tier-resolution metadata; delegates to the canonical constructor with
   * {@link ModelSource#PROVIDER_DEFAULT}, a {@code null} {@code resolvedModel}, and a {@code null}
   * {@code requestedModelTier}.
   *
   * @param rawResponse raw model output text before command parsing
   * @param commands    parsed commands from the response
   * @param modelUsed   the concrete model the provider ran; nullable
   * @param tokenUsage  token counts for this invocation; nullable
   */
  public AgentInvocationResult(
      String rawResponse,
      List<LlmCommand> commands,
      String modelUsed,
      TokenUsageReport tokenUsage) {
    this(rawResponse, commands, modelUsed, tokenUsage, null, ModelSource.PROVIDER_DEFAULT, null);
  }
}
