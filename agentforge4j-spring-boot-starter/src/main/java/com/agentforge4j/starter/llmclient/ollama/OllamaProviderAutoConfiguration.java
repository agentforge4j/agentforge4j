// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.ollama.OllamaConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables Ollama providers when {@code agentforge4j.llm.ollama.enabled=true} once the backing
 * module is on the classpath.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(OllamaLlmClientProperties.class)
@ConditionalOnClass(OllamaConfiguration.class)
public class OllamaProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "agentforge4j.llm.ollama.enabled", havingValue = "true")
  LlmClientConfiguration ollamaConfiguration(OllamaLlmClientProperties properties) {
    return new OllamaConfigurationAdapter(properties);
  }
}
