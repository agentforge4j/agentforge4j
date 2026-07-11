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
  requires agentforge4j.core;
  requires agentforge4j.config.loader;
  requires agentforge4j.schema;
  requires agentforge4j.llm.api;
  requires agentforge4j.llm;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.core;
  requires org.apache.commons.lang3;

  // PUBLIC API + SPI: WorkflowRuntimeBuilder entry point, the RunContextManager SPI, and
  // ContextPackRegistry (a WorkflowRuntimeBuilder builder parameter type).
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
  // com.agentforge4j.runtime.context stays UNEXPORTED: it is compaction/context-resolution
  // internals (CanonicalJson, CompactSibling(Store), ContextFingerprint, ContextSourceId,
  // ContextSourceResolver). ContextPackRegistry — the only public type from that package, a
  // WorkflowRuntimeBuilder#contextPackRegistry(...) parameter type — lives in the top-level
  // com.agentforge4j.runtime package (already exported above) instead.
  // PUBLIC: exceptions that propagate out of command application to embedders
  // (CompactSiblingUnavailableException from a COMPACT_ONLY expansion grant;
  // UserPromptLimitExceededException from the user-prompt pause guard).
  exports com.agentforge4j.runtime.exception;

  opens com.agentforge4j.runtime.llm to com.fasterxml.jackson.databind;
  // com.agentforge4j.runtime.waste stays UNEXPORTED (WasteDetector and its persisted-history
  // types are pure runtime-internal bookkeeping, mirroring com.agentforge4j.runtime.context) but
  // needs reflective access for Jackson to serialize WasteDetectorInvocationHistory/
  // WasteDetectorLoopHistory to and from the reserved __wasteDetectorHistory.* context keys.
  opens com.agentforge4j.runtime.waste to com.fasterxml.jackson.databind;
}
