// SPDX-License-Identifier: Apache-2.0
/**
 * Framework-agnostic bootstrap: assembles runtime and configuration defaults without binding to
 * Spring or a specific LLM provider module.
 */
module agentforge4j.bootstrap {
  requires agentforge4j.util;
  requires agentforge4j.core;
  requires agentforge4j.llm.api;
  requires transitive agentforge4j.llm;
  requires transitive agentforge4j.config.loader;
  requires transitive agentforge4j.runtime;
  requires agentforge4j.schema;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires org.apache.commons.lang3;

  uses com.agentforge4j.llm.LlmClientFactory;
  uses com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory;
  uses com.agentforge4j.config.loader.agent.ArtifactValidatorFactory;
  uses com.agentforge4j.core.spi.aggregation.ContextAggregator;

  exports com.agentforge4j.bootstrap;
}
