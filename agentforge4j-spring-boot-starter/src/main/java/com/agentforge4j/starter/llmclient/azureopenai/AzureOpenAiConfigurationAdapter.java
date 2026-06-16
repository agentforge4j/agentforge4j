// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.azureopenai;

import com.agentforge4j.llm.azureopenai.AzureOpenAiConfiguration;
import java.time.Duration;

record AzureOpenAiConfigurationAdapter(AzureOpenAiLlmClientProperties properties)
    implements AzureOpenAiConfiguration {

  @Override
  public String getApiKey() {
    return properties.apiKey();
  }

  @Override
  public String getDeploymentName() {
    return properties.deploymentName();
  }

  @Override
  public String getEndpoint() {
    return properties.endpoint();
  }

  @Override
  public String getApiVersion() {
    return properties.apiVersion();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public Duration getRequestTimeout() {
    return properties.requestTimeout();
  }

  @Override
  public String getDefaultModel() {
    return properties.deploymentName();
  }
}
