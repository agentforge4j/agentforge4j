/**
 * Framework-agnostic bootstrap: assembles runtime and configuration defaults without binding to
 * Spring or a specific LLM provider module.
 */
module agentforge4j.bootstrap {
  requires agentforge4j.util;
  requires agentforge4j.core;
  requires agentforge4j.llm.api;
  requires agentforge4j.llm;
  requires agentforge4j.config.loader;
  requires agentforge4j.runtime;
  requires agentforge4j.integrations;
  requires agentforge4j.schema;
  requires agentforge4j.workflows;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;

  exports com.agentforge4j.bootstrap;
}
