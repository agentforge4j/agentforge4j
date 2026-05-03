package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;

/**
 * Configuration for vLLM LLM client.
 * <p>
 * Provides the URL and request timeout for connecting to a vLLM server.
 */
public interface VllmConfiguration extends LlmClientConfiguration {

  /**
   * Returns the vLLM server URL.
   *
   * @return the API endpoint URL
   */
  String getUrl();

  /**
   * Returns the request timeout for vLLM API calls.
   *
   * @return the timeout duration
   */
  Duration getRequestTimeout();

  @Override
  default String getProviderName() {
    return "vllm";
  }
}
