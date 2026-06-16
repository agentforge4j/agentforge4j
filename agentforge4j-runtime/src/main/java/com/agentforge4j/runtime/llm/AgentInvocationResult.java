// SPDX-License-Identifier: Apache-2.0
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
   * Returns a new {@link Builder} for assembling an {@link AgentInvocationResult} without positional
   * arguments. The tier-resolution metadata is optional: {@code modelSource} defaults to
   * {@link ModelSource#PROVIDER_DEFAULT} and both {@code resolvedModel} and
   * {@code requestedModelTier} default to {@code null}, so callers on non-LLM paths (chiefly tests)
   * only set the four core components. Required fields are validated when {@link Builder#build()} is
   * called.
   *
   * @return new builder; never {@code null}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for {@link AgentInvocationResult}. Lets callers omit the tier-resolution metadata
   * without passing trailing arguments to the canonical constructor. Validation is deferred to
   * {@link #build()}, which delegates to the canonical constructor.
   */
  public static final class Builder {

    private String rawResponse;
    private List<LlmCommand> commands;
    private String modelUsed;
    private TokenUsageReport tokenUsage;
    private String resolvedModel;
    private ModelSource modelSource = ModelSource.PROVIDER_DEFAULT;
    private ModelTier requestedModelTier;

    private Builder() {
      // obtain via AgentInvocationResult.builder()
    }

    /**
     * Sets the raw model output text before command parsing.
     *
     * @param rawResponse non-blank raw response text
     *
     * @return this builder
     */
    public Builder withRawResponse(String rawResponse) {
      this.rawResponse = rawResponse;
      return this;
    }

    /**
     * Sets the parsed commands from the response.
     *
     * @param commands parsed commands; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withCommands(List<LlmCommand> commands) {
      this.commands = commands;
      return this;
    }

    /**
     * Sets the concrete model the provider ran.
     *
     * @param modelUsed model the provider reported; nullable
     *
     * @return this builder
     */
    public Builder withModelUsed(String modelUsed) {
      this.modelUsed = modelUsed;
      return this;
    }

    /**
     * Sets the token counts for this invocation.
     *
     * @param tokenUsage token usage; nullable
     *
     * @return this builder
     */
    public Builder withTokenUsage(TokenUsageReport tokenUsage) {
      this.tokenUsage = tokenUsage;
      return this;
    }

    /**
     * Sets the model string the runtime resolved and sent on the request.
     *
     * @param resolvedModel resolved model; {@code null} for {@link ModelSource#PROVIDER_DEFAULT}
     *
     * @return this builder
     */
    public Builder withResolvedModel(String resolvedModel) {
      this.resolvedModel = resolvedModel;
      return this;
    }

    /**
     * Sets how {@code resolvedModel} was determined. Defaults to
     * {@link ModelSource#PROVIDER_DEFAULT} when not set.
     *
     * @param modelSource model source; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withModelSource(ModelSource modelSource) {
      this.modelSource = modelSource;
      return this;
    }

    /**
     * Sets the capability tier requested for this call.
     *
     * @param requestedModelTier requested tier, or {@code null} when no tier applied
     *
     * @return this builder
     */
    public Builder withRequestedModelTier(ModelTier requestedModelTier) {
      this.requestedModelTier = requestedModelTier;
      return this;
    }

    /**
     * Builds the validated {@link AgentInvocationResult}.
     *
     * @return immutable invocation result; never {@code null}
     */
    public AgentInvocationResult build() {
      return new AgentInvocationResult(rawResponse, commands, modelUsed, tokenUsage, resolvedModel,
          modelSource, requestedModelTier);
    }
  }
}
