package com.agentforge4j.starter.llmclient.azureopenai;

import com.agentforge4j.llm.azureopenai.AzureOpenAiConfiguration;
import com.agentforge4j.starter.LlmAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = LlmAutoConfiguration.class)
@EnableConfigurationProperties(AzureOpenAiLlmClientProperties.class)
@ConditionalOnClass(AzureOpenAiConfiguration.class)
public class AzureOpenAiProviderAutoConfiguration {

  @Bean
  @ConditionalOnProperty("agentforge4j.llm.azure-openai.api-key")
  AzureOpenAiConfiguration azureOpenAiConfiguration(AzureOpenAiLlmClientProperties properties) {
    return new AzureOpenAiConfigurationAdapter(properties);
  }
}
