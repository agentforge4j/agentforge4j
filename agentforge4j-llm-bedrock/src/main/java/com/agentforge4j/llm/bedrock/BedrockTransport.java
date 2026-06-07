package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;

/**
 * Internal strategy for invoking a Bedrock model. The dispatcher selects an implementation by
 * {@link BedrockTransportType}.
 */
interface BedrockTransport {

  /**
   * Executes {@code request} against {@code modelId}.
   *
   * @param request      the validated execution request
   * @param modelId      the resolved Bedrock model id, sent to Bedrock unchanged
   * @param capabilities the resolved family capabilities
   *
   * @return the execution response with assistant text and provider token usage when reported
   */
  LlmExecutionResponse execute(
      LlmExecutionRequest request, String modelId, BedrockModelCapabilities capabilities);
}
