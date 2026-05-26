package com.agentforge4j.starter.llmclient.gemini;

import com.agentforge4j.llm.gemini.GeminiConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables {@linkplain com.agentforge4j.llm.gemini.GeminiConfiguration Gemini settings} once
 * {@code agentforge4j.llm.gemini.api-key} is populated.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(GeminiLlmClientProperties.class)
@ConditionalOnClass(GeminiConfiguration.class)
public class GeminiProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.gemini.api-key")
  GeminiConfiguration geminiConfiguration(GeminiLlmClientProperties properties) {
    return new GeminiConfigurationAdapter(properties);
  }
}
