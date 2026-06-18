// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.claude.ClaudeConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables Claude adapters when {@code agentforge4j.llm.claude.api-key} is populated.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(ClaudeLlmClientProperties.class)
@ConditionalOnClass(ClaudeConfiguration.class)
public class ClaudeProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.claude.api-key")
  LlmClientConfiguration claudeConfiguration(ClaudeLlmClientProperties properties) {
    return new ClaudeConfigurationAdapter(properties);
  }
}
