// SPDX-License-Identifier: Apache-2.0
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

  /**
   * Returns the Azure OpenAI API key.
   *
   * @return the API key for authentication
   */
  String getApiKey();

  /**
   * Returns the deployment name for the model.
   *
   * @return the deployment name
   */
  String getDeploymentName();

  /**
   * Returns the Azure OpenAI API version.
   *
   * @return the API version string
   */
  String getApiVersion();

  /**
   * Returns the request timeout for Azure OpenAI API calls.
   *
   * @return the timeout duration
   */
  Duration getRequestTimeout();
}
