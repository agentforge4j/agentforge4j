// SPDX-License-Identifier: Apache-2.0
/**
 * Framework-agnostic domain contracts for workflow-driven orchestration.
 *
 * <p>Defines immutable workflow, agent, step, command, and runtime-facing models together with
 * repository interfaces. Consumers are the runtime, loaders, and providers; nothing here binds
 * to Spring, persistence, or a specific LLM vendor.
 *
 * <p>Architectural boundary: workflow definitions and typed commands describe behaviour; model
 * output is interpreted as content and commands, not as authority over execution flow (that stays
 * with the runtime).
 */
module agentforge4j.core {
  requires static lombok;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.annotation;
  requires org.apache.commons.lang3;

  exports com.agentforge4j.core.agent;
  exports com.agentforge4j.core.command;
  exports com.agentforge4j.core.command.schema;
  exports com.agentforge4j.core.spi.tool;
  exports com.agentforge4j.core.spi.integration;
  exports com.agentforge4j.core.spi.contextpack;
  exports com.agentforge4j.core.spi.validation;
  exports com.agentforge4j.core.runtime;
  exports com.agentforge4j.core.workflow;
  exports com.agentforge4j.core.workflow.estimate;
  exports com.agentforge4j.core.workflow.requirement;
  exports com.agentforge4j.core.workflow.collection;
  exports com.agentforge4j.core.workflow.artifact;
  exports com.agentforge4j.core.workflow.step;
  exports com.agentforge4j.core.workflow.context;
  exports com.agentforge4j.core.exception;
  exports com.agentforge4j.core.workflow.step.blueprint;
  exports com.agentforge4j.core.workflow.step.loop;
  exports com.agentforge4j.core.workflow.step.retry;
  exports com.agentforge4j.core.workflow.step.spar;
  exports com.agentforge4j.core.workflow.step.behaviour;
  exports com.agentforge4j.core.workflow.event;
  exports com.agentforge4j.core.workflow.file;
  exports com.agentforge4j.core.workflow.state;
  exports com.agentforge4j.core.workflow.repository;
  exports com.agentforge4j.core.workflow.reachability;
}
