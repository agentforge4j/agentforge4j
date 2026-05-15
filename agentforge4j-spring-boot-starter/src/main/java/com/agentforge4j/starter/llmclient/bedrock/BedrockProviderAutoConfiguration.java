package com.agentforge4j.starter.llmclient.bedrock;

import com.agentforge4j.llm.bedrock.BedrockConfiguration;
import com.agentforge4j.starter.LlmAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers Bedrock configuration when {@code agentforge4j.llm.bedrock.enabled=true}.
 */
@AutoConfiguration(before = LlmAutoConfiguration.class)
@EnableConfigurationProperties(BedrockLlmClientProperties.class)
@ConditionalOnClass(BedrockConfiguration.class)
public class BedrockProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "agentforge4j.llm.bedrock.enabled", havingValue = "true")
  BedrockConfiguration bedrockConfiguration(BedrockLlmClientProperties properties) {
    return new BedrockConfigurationAdapter(properties);
  }
}
