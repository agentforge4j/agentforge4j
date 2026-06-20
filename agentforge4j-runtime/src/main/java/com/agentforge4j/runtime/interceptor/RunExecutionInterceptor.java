// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.interceptor;

/**
 * Control SPI for intercepting a run's execution at two pre-execution points: once before a run enters main execution,
 * and immediately before each LLM call. Both points are synchronous and may veto by throwing
 * {@link ExecutionBlockedException}; returning normally allows execution to proceed.
 *
 * <p>This is a <em>control</em> seam — distinct from the passive {@code LlmCallObserver}, which only
 * watches completed calls. It carries no tenant, billing, or cost concepts; an embedding application (for example a
 * budget enforcer) registers an implementation and derives those itself. Both methods default to no-ops, so when
 * nothing is registered the runtime behaves exactly as before.
 *
 * <p>A single instance is shared across all runs and driving threads, so implementations must be
 * thread-safe.
 *
 * <p>When a hook throws {@link ExecutionBlockedException} the runtime records a neutral
 * {@link com.agentforge4j.core.workflow.event.WorkflowEventType#RUN_BLOCKED} audit event and leaves the run status
 * unchanged (non-terminal); the embedding application resolves the block (resume or cancel). OSS performs no terminal
 * transition.
 */
public interface RunExecutionInterceptor {

  /**
   * Canonical no-op interceptor — both hooks allow; the default when nothing is registered.
   */
  RunExecutionInterceptor NO_OP = new RunExecutionInterceptor() {
  };

  /**
   * Invoked once, when a run first enters main execution (not on resume). May throw {@link ExecutionBlockedException}
   * to block the run before any step runs.
   *
   * @param context the run entering main execution
   */
  default void beforeMainExecution(final RunExecutionContext context) {
  }

  /**
   * Invoked immediately before each LLM provider call, after model resolution and before dispatch. May throw
   * {@link ExecutionBlockedException} to block the call.
   *
   * @param context the imminent call
   */
  default void beforeLlmCall(final LlmCallContext context) {
  }
}
