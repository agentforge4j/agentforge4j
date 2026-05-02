package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

public interface OllamaConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "ollama";
  }

  default String getUrl() {
    return "http://localhost:11434/api/chat";
  }

  Duration getRequestTimeout();
}
