// SPDX-License-Identifier: Apache-2.0
/**
 * AgentForge4j testkit: a lean, reusable assertion engine for testing workflows.
 *
 * <p>Assembles a runtime backed by the deterministic fake LLM provider, captures the run's
 * observable effects (events, files, context, token usage), and exposes a fluent run-assertion API
 * for verifying outcomes. The bootstrap-facing harness adapter is the only component that depends
 * on {@code agentforge4j.bootstrap}; the assertion and capture layers operate on {@code core} and
 * {@code runtime} types only, so the engine stays assembly-agnostic.
 */
module agentforge4j.testkit {
  requires transitive agentforge4j.core;
  // Transitive: these modules' types appear in the exported public API — FileSink (the supertype of
  // the exported CapturingFileSink), ModelTier (WorkflowRunAssert.providerCallTier), and FakeScript /
  // FakeScriptParser (the harness builder and ScenarioScriptLoader). A modular consumer reading only
  // agentforge4j.testkit must transitively read them to use that API without re-declaring requires.
  requires transitive agentforge4j.runtime;
  requires transitive agentforge4j.llm.api;
  requires transitive agentforge4j.llm.fake;
  requires agentforge4j.bootstrap;
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;

  exports com.agentforge4j.testkit.harness;
  exports com.agentforge4j.testkit.capture;
  exports com.agentforge4j.testkit.assertion;
  exports com.agentforge4j.testkit.scenario;
  exports com.agentforge4j.testkit.tool;
}
