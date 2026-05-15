package com.agentforge4j.starter.llmclient.claude;

import com.agentforge4j.llm.claude.ClaudeConfiguration;
import com.agentforge4j.starter.LlmAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables Claude adapters when {@code agentforge4j.llm.claude.api-key} is populated.
 */
@AutoConfiguration(before = LlmAutoConfiguration.class)
@EnableConfigurationProperties(ClaudeLlmClientProperties.class)
@ConditionalOnClass(ClaudeConfiguration.class)
public class ClaudeProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.claude.api-key")
  ClaudeConfiguration claudeConfiguration(ClaudeLlmClientProperties properties) {
    return new ClaudeConfigurationAdapter(properties);
  }
}
