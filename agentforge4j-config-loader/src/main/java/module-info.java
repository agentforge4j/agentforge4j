/**
 * Loads workflow and agent bundles from the filesystem or classpath and maps them into core models.
 *
 * <p>Responsibilities include bundle discovery, index parsing, resource wiring, and mapping loaded
 * resources into {@code agentforge4j.core} domain models. Depends on {@code agentforge4j.workflows} for
 * shipped bundle locations.
 *
 * <p>Intended consumers: embedding applications, tests, and tooling that prepare bundles for execution.
 */
module agentforge4j.config.loader {
  requires agentforge4j.util;
  requires agentforge4j.core;
  requires agentforge4j.schema;
  requires agentforge4j.workflows;
  requires com.fasterxml.jackson.databind;
  requires com.networknt.schema;
  requires org.apache.commons.lang3;
  requires static lombok;

  exports com.agentforge4j.config.loader;
  exports com.agentforge4j.config.loader.agent;
  exports com.agentforge4j.config.loader.integration;
  exports com.agentforge4j.config.loader.workflow;
  exports com.agentforge4j.config.loader.prompt;
  exports com.agentforge4j.config.loader.repository;
  exports com.agentforge4j.config.loader.validation;
}
