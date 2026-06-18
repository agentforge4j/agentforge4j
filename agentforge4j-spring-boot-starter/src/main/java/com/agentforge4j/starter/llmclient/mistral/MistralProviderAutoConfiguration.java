// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.mistral;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.mistral.MistralConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Creates Mistral configuration beans when {@code agentforge4j.llm.mistral.api-key} resolves.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(MistralLlmClientProperties.class)
@ConditionalOnClass(MistralConfiguration.class)
public class MistralProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.mistral.api-key")
  LlmClientConfiguration mistralConfiguration(MistralLlmClientProperties properties) {
    return new MistralConfigurationAdapter(properties);
  }
}
