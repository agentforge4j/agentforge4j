// SPDX-License-Identifier: Apache-2.0
/**
 * Loads workflow and agent bundles from the filesystem or classpath and maps them into core models.
 *
 * <p>Responsibilities include bundle discovery, index parsing, resource wiring, and mapping loaded
 * resources into {@code agentforge4j.core} domain models. Shipped bundles are located on the
 * classpath (the {@code shipped-workflows} / {@code shipped-agents} roots), which a separately
 * shipped, independently-versioned workflow catalog provides; the framework owns only the locator
 * and compatibility-gate mechanism.
 *
 * <p>Intended consumers: embedding applications, tests, and tooling that prepare bundles for execution.
 *
 * <p>Load-time invariant: across the reachable graph of every workflow (the workflow, the blueprints
 * it references, and the sub-workflows its {@code WORKFLOW} steps reach) each step id must resolve to a
 * single structural location — <em>reachable step ids must be unique</em>. Bundles that violate it are
 * rejected at load so the runtime never has to resolve an ambiguous step at a gate.
 */
module agentforge4j.config.loader {
  requires agentforge4j.util;
  requires agentforge4j.core;
  requires agentforge4j.schema;
  requires com.fasterxml.jackson.databind;
  requires com.networknt.schema;
  requires org.apache.commons.lang3;
  requires static lombok;

  exports com.agentforge4j.config.loader;
  exports com.agentforge4j.config.loader.agent;
  exports com.agentforge4j.config.loader.catalog;
  exports com.agentforge4j.config.loader.integration;
  exports com.agentforge4j.config.loader.workflow;
  exports com.agentforge4j.config.loader.prompt;
  exports com.agentforge4j.config.loader.repository;
  exports com.agentforge4j.config.loader.validation;

  provides com.agentforge4j.config.loader.agent.ArtifactValidatorFactory
      with com.agentforge4j.config.loader.agent.AgentBundleArtifactValidatorFactory,
          com.agentforge4j.config.loader.agent.RequiredArtifactsPresentValidatorFactory;
}
