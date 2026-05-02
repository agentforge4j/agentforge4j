package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Azure OpenAI chat completions settings.
 */
public interface AzureOpenAiConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "azure-openai";
  }

  /**
   * Resource endpoint, for example {@code https://myresource.openai.azure.com}.
   */
  String getEndpoint();

  String getApiKey();

  String getDeploymentName();

  String getApiVersion();

  Duration getRequestTimeout();
}
