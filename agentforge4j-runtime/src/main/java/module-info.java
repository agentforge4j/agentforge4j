// SPDX-License-Identifier: Apache-2.0
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
  // transitive: WorkflowRuntimeBuilder (the exported entry point) and RunExecutionContext (the
  // exported interceptor SPI) both expose agentforge4j.core types directly to callers.
  requires transitive agentforge4j.core;
  requires agentforge4j.config.loader;
  requires agentforge4j.schema;
  requires agentforge4j.llm.api;
  // transitive: exported com.agentforge4j.runtime.llm signatures (AgentInvocationResult,
  // LlmCallObserver, AgentInvoker.Builder) expose ModelTier/TokenUsageReport/LlmExecutionResponse
  // (llm.api, via llm's own transitive requires) and LlmClientResolver (llm) directly to callers.
  requires transitive agentforge4j.llm;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.core;
  requires org.apache.commons.lang3;

  // PUBLIC API + SPI: WorkflowRuntimeBuilder entry point and the RunContextManager SPI.
  exports com.agentforge4j.runtime;
  // PUBLIC: command-application wiring types (FileSink and its implementations).
  exports com.agentforge4j.runtime.command;
  // SPI/infra: EventRecorder, consumed by bootstrap assembly.
  exports com.agentforge4j.runtime.event;
  // PUBLIC: in-memory default persistence implementations.
  exports com.agentforge4j.runtime.repository;
  // PUBLIC: tool-execution wiring and default implementations.
  exports com.agentforge4j.runtime.tool;

  // PUBLIC + SPI: LLM provider-selection strategy, AgentInvoker, and call observation.
  exports com.agentforge4j.runtime.llm;
  // PUBLIC SPI: run-execution interceptor seam for embedding applications.
  exports com.agentforge4j.runtime.interceptor;

  opens com.agentforge4j.runtime.llm to com.fasterxml.jackson.databind;
}
