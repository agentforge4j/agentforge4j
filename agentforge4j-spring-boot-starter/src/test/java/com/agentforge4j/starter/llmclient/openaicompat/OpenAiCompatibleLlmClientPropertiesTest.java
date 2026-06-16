// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class OpenAiCompatibleLlmClientPropertiesTest {

  @Test
  void bindsAuthHeadersAndResponsesPathFromConfiguration() {
    MockEnvironment env = new MockEnvironment()
        .withProperty("agentforge4j.llm.openai-compatible.api-key", "k")
        .withProperty("agentforge4j.llm.openai-compatible.base-url", "https://example.com")
        .withProperty("agentforge4j.llm.openai-compatible.auth-header-name", "Authorization")
        .withProperty("agentforge4j.llm.openai-compatible.auth-header-prefix", "Bearer ")
        .withProperty("agentforge4j.llm.openai-compatible.responses-path", "/v1/responses");

    OpenAiCompatibleLlmClientProperties props = Binder.get(env)
        .bindOrCreate(
            "agentforge4j.llm.openai-compatible",
            Bindable.of(OpenAiCompatibleLlmClientProperties.class));

    assertThat(props.authHeaderPrefix()).isEqualTo("Bearer ");
    assertThat(props.authHeaderName()).isEqualTo("Authorization");
    assertThat(props.responsesPath()).isEqualTo("/v1/responses");
  }
}
