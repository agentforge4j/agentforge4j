package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;

/**
 * Shared resolution of inference parameters for Bedrock transports.
 */
final class BedrockInference {

  /**
   * Default {@code max_tokens} applied when neither the request nor the configuration sets one.
   */
  static final int DEFAULT_MAX_TOKENS = 4096;

  private BedrockInference() {
  }

  /**
   * Resolves the output token cap: {@link LlmExecutionRequest#maxOutputTokens()} when set,
   * otherwise a positive {@link BedrockConfiguration#getMaxTokens()}, otherwise
   * {@link #DEFAULT_MAX_TOKENS}.
   *
   * @param request the execution request
   * @param config  the Bedrock configuration
   *
   * @return the resolved {@code max_tokens} value
   */
  static int resolveMaxTokens(LlmExecutionRequest request, BedrockConfiguration config) {
    if (request.maxOutputTokens() != null) {
      return request.maxOutputTokens();
    }
    if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
      return config.getMaxTokens();
    }
    return DEFAULT_MAX_TOKENS;
  }
}
