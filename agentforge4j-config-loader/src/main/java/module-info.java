module agentforge4j.config.loader {
  requires agentforge4j.util;
  requires agentforge4j.core;
  requires agentforge4j.workflows;
  requires com.fasterxml.jackson.databind;
  requires org.apache.commons.lang3;
  requires static lombok;

  exports com.agentforge4j.config.loader;
  exports com.agentforge4j.config.loader.agent;
  exports com.agentforge4j.config.loader.workflow;
  exports com.agentforge4j.config.loader.prompt;
  exports com.agentforge4j.config.loader.repository;
  exports com.agentforge4j.config.loader.validation;
}
