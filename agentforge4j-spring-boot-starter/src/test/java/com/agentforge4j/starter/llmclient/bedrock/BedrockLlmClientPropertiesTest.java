// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class BedrockLlmClientPropertiesTest {

  @Test
  void leavesMaxTokensAndTemperatureNullWhenUnset() {
    MockEnvironment env = new MockEnvironment()
        .withProperty("agentforge4j.llm.bedrock.region", "us-east-1")
        .withProperty("agentforge4j.llm.bedrock.model-id", "anthropic.claude-3-haiku")
        .withProperty("agentforge4j.llm.bedrock.anthropic-version", "bedrock-2023-05-31");

    BedrockLlmClientProperties props = Binder.get(env)
        .bindOrCreate("agentforge4j.llm.bedrock", Bindable.of(BedrockLlmClientProperties.class));

    assertThat(props.maxTokens()).isNull();
    assertThat(props.temperature()).isNull();
    assertThat(props.anthropicVersion()).isEqualTo("bedrock-2023-05-31");
  }
}
