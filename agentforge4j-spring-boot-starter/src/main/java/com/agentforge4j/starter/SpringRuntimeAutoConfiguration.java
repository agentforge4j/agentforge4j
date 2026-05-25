package com.agentforge4j.starter;

import com.agentforge4j.runtime.RunContextManager;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.starter.logging.MdcRunContextManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers Spring-specific runtime beans not assembled by
 * {@link com.agentforge4j.bootstrap.AgentForge4jBootstrap}.
 */
@AutoConfiguration(after = BootstrapAutoConfiguration.class)
public class SpringRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RunContextManager runContextManager() {
    return new MdcRunContextManager();
  }

  @Bean
  @ConditionalOnMissingBean
  public SchemaProvider schemaProvider() {
    return new ClasspathSchemaProvider();
  }
}
