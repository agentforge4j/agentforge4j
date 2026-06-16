// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openai;

import com.agentforge4j.llm.openai.OpenAiConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables OpenAI client wiring when LLM adapter classes exist and
 * {@code agentforge4j.llm.openai.api-key} is set.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(OpenAiLlmClientProperties.class)
@ConditionalOnClass(OpenAiConfiguration.class)
public class OpenAiProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.openai.api-key")
  OpenAiConfiguration openAiConfiguration(OpenAiLlmClientProperties properties) {
    return new OpenAiConfigurationAdapter(properties);
  }
}
