// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.vllm.VllmConfiguration;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Activates vLLM wiring when {@code agentforge4j.llm.vllm.url} is set.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(VllmLlmClientProperties.class)
@ConditionalOnClass(VllmConfiguration.class)
public class VllmProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.vllm.url")
  LlmClientConfiguration vllmConfiguration(VllmLlmClientProperties properties) {
    return new VllmConfigurationAdapter(properties);
  }
}
