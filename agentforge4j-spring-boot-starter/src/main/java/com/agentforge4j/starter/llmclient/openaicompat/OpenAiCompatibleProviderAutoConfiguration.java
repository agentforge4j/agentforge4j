// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openaicompat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.openaicompatible.OpenAiCompatibleConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables OpenAI-compatible providers when {@code agentforge4j.llm.openai-compatible.api-key}
 * resolves.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(OpenAiCompatibleLlmClientProperties.class)
@ConditionalOnClass(OpenAiCompatibleConfiguration.class)
public class OpenAiCompatibleProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.openai-compatible.api-key")
  LlmClientConfiguration openAiCompatibleConfiguration(
      OpenAiCompatibleLlmClientProperties properties) {
    return new OpenAiCompatibleConfigurationAdapter(properties);
  }
}
