// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;

public final class TestFixtures {

  private TestFixtures() {
  }

  public static LlmClientConfiguration testConfig(String providerName, String defaultModel) {
    return testConfig(providerName, defaultModel, Duration.ofSeconds(30));
  }

  public static LlmClientConfiguration testConfig(String providerName, String defaultModel,
      Duration timeout) {
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return providerName;
      }

      @Override
      public String getDefaultModel() {
        return defaultModel;
      }

      @Override
      public Duration getConnectTimeout() {
        return timeout;
      }
    };
  }

  public static LlmExecutionRequest testRequest(String provider, String systemPrompt,
      String userInput) {
    return testRequest(provider, null, systemPrompt, userInput);
  }

  public static LlmExecutionRequest testRequest(String provider, String model, String systemPrompt,
      String userInput) {
    return new LlmExecutionRequest(provider, model, systemPrompt, userInput, null, null, null);
  }

  public static ObjectMapper testObjectMapper() {
    return new ObjectMapper();
  }

  public static class TestLlmClient implements LlmClient {

    private final String providerName;

    public TestLlmClient(String providerName) {
      this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
      return providerName;
    }

    @Override
    public LlmExecutionResponse execute(LlmExecutionRequest request) {
      return new LlmExecutionResponse("response from " + providerName, null, null);
    }
  }

  public static class TestLlmClientFactory implements LlmClientFactory {

    private final String providerName;

    public TestLlmClientFactory(String providerName) {
      this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
      return providerName;
    }

    @Override
    public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
      return new TestLlmClient(config.getProviderName());
    }
  }
}

