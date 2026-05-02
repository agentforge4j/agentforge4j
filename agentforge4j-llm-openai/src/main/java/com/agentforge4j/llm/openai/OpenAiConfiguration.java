package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

public interface OpenAiConfiguration extends LlmClientConfiguration {

  String getApiKey();

  Duration getRequestTimeout();

  @Override
  default String getProviderName() {
    return "openai";
  }

  default String getUrl() {
    return "https://api.openai.com/v1/responses";
  }
}
