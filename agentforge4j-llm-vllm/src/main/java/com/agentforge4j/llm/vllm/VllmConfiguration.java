package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;

public interface VllmConfiguration extends LlmClientConfiguration {

  String getUrl();

  Duration getRequestTimeout();

  @Override
  default String getProviderName() {
    return "vllm";
  }
}
