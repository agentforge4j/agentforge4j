// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.interceptor;

import java.io.Serial;

/**
 * Thrown by a {@link RunExecutionInterceptor} to deliberately block a run from proceeding — for example when a budget
 * control declines to admit the next LLM call. This is a <em>control</em> outcome (a chosen veto), distinct from an
 * incidental runtime failure; the runtime treats it as a blocking signal and halts the affected drive.
 *
 * <p>OSS itself never throws this — it carries no notion of why a run might be blocked; only a
 * registered interceptor (supplied by an embedding application) raises it.
 *
 * <p>Lifecycle on a block: the runtime records a neutral
 * {@link com.agentforge4j.core.workflow.event.WorkflowEventType#RUN_BLOCKED} audit event and leaves the run status
 * unchanged (non-terminal) — it performs no terminal transition. The embedding application resolves the block (for
 * example resume after a top-up, or cancel); any mapping to an application status (such as a billing pause) is the
 * embedder's concern.
 */
public final class ExecutionBlockedException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a blocked-execution signal.
   *
   * @param message human-readable reason the interceptor blocked execution
   */
  public ExecutionBlockedException(final String message) {
    super(message);
  }

  /**
   * Creates a blocked-execution signal with an underlying cause.
   *
   * @param message human-readable reason the interceptor blocked execution
   * @param cause   the underlying cause
   */
  public ExecutionBlockedException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
