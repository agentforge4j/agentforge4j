package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;

public interface ClaudeConfiguration extends LlmClientConfiguration {

  String getApiKey();

  String getApiVersion();

  String getUrl();

  Duration getRequestTimeout();

  default int getMaxTokenSize() {
    return 8096;
  }

  @Override
  default String getProviderName() {
    return "claude";
  }
}
