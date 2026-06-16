/**
 * Workflow execution engine: owns state transitions, events, and command handling for runs.
 *
 * <p>Consumes validated definitions from {@code agentforge4j.core} and
 * {@code agentforge4j.schema}, drives deterministic steps with auditable events, and invokes
 * integrations and LLM clients only through controlled abstractions. Human-in-the-loop transitions
 * (approvals, signals) are enforced here, not by provider responses.
 *
 * <p>Runtime-owned flow control: orchestration follows the workflow graph; model output supplies
 * structured commands and content, not arbitrary control of the graph.
 */
module agentforge4j.runtime {
  requires static lombok;
  requires agentforge4j.util;
  requires agentforge4j.core;
  requires agentforge4j.config.loader;
  requires agentforge4j.schema;
  requires agentforge4j.llm.api;
  requires agentforge4j.llm;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.core;
  requires org.apache.commons.lang3;

  exports com.agentforge4j.runtime;
  exports com.agentforge4j.runtime.command;
  exports com.agentforge4j.runtime.command.handler;
  exports com.agentforge4j.runtime.event;
  exports com.agentforge4j.runtime.repository;
  exports com.agentforge4j.runtime.tool;

  exports com.agentforge4j.runtime.llm;
  exports com.agentforge4j.runtime.interceptor;

  opens com.agentforge4j.runtime.llm to com.fasterxml.jackson.databind;
}
