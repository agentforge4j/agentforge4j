package com.agentforge4j.core.workflow.event;

/**
 * Classification of {@link WorkflowEvent} entries written during workflow execution.
 */
public enum WorkflowEventType {
  /**
   * Recorded when a run is created or resumed.
   */
  RUN_STARTED,
  /**
   * Recorded when a step begins executing.
   */
  STEP_STARTED,
  /**
   * Recorded when a step finishes without error.
   */
  STEP_COMPLETED,
  /**
   * Recorded when a step throws or otherwise fails.
   */
  STEP_FAILED,
  /**
   * Recorded when a step is retried after failure or policy.
   */
  STEP_RETRIED,
  /**
   * Recorded when the run blocks waiting for typed user input (prompt response or artifact).
   */
  AWAITING_INPUT,
  /**
   * Recorded when the run blocks on a human approval gate.
   */
  AWAITING_APPROVAL,
  /**
   * Recorded when a pending approval is granted.
   */
  APPROVED,
  /**
   * Recorded when a pending approval is denied.
   */
  REJECTED,
  /**
   * Raw text returned by the LLM for an agent invocation (one event per provider call).
   */
  LLM_OUTPUT,
  /**
   * Recorded once per LLM provider call, carrying token usage and model metadata. Payload is a JSON
   * object with fields: {@code stepId}, {@code agentId}, {@code provider}, {@code modelUsed},
   * {@code inputTokens}, {@code outputTokens}, {@code totalTokens}. Null token fields are emitted
   * as JSON {@code null}, never {@code 0}.
   */
  LLM_CALL_COMPLETED,
  /**
   * Recorded when durable usage projection fails after {@link #LLM_CALL_COMPLETED}. Payload is a
   * JSON object with fields: {@code originalEventId}, {@code runId}, {@code stepId},
   * {@code reason}, and optional {@code exceptionClass}.
   */
  USAGE_RECORD_FAILED,
  /**
   * Recorded when workflow context keys are written or replaced.
   */
  CONTEXT_UPDATED,
  /**
   * Recorded at the start of a loop iteration inside a blueprint.
   */
  LOOP_ITERATION_STARTED,
  /**
   * Recorded when a loop iteration completes.
   */
  LOOP_ITERATION_COMPLETED,
  /**
   * Recorded when the run reaches a successful terminal state.
   */
  RUN_COMPLETED,
  /**
   * Recorded when the run ends in failure.
   */
  RUN_FAILED,
  /**
   * Recorded when the run is cancelled.
   */
  RUN_CANCELLED,
  /**
   * Recorded when the active agent is changed mid-run.
   */
  AGENT_SWAPPED,
  /**
   * Recorded when the step prompt is replaced or patched during execution.
   */
  PROMPT_OVERRIDDEN,
  /**
   * Recorded when a step exceeds {@code maxUserPromptRounds} for blocking user prompts.
   */
  USER_PROMPT_LIMIT_REACHED,
  /**
   * Recorded when a tool invocation is requested by the LLM and enters the execution chokepoint. Payload
   * fields: {@code capability}, {@code agentId}, {@code stepUid}, {@code llmRationale}.
   */
  TOOL_INVOCATION_REQUESTED,
  /**
   * Recorded when a tool invocation succeeds (success-only; failures emit {@link #TOOL_INVOCATION_FAILED}).
   * Payload fields: {@code capability}, {@code latencyMillis}.
   */
  TOOL_INVOCATION_COMPLETED,
  /**
   * Recorded when policy denies a tool invocation. Payload fields: {@code capability}, {@code reason}.
   */
  TOOL_INVOCATION_DENIED,
  /**
   * Recorded when a tool invocation is suspended awaiting human approval. Payload fields:
   * {@code capability}, {@code reason}, {@code approverScope}.
   */
  TOOL_INVOCATION_APPROVAL_PENDING,
  /**
   * Recorded when a tool invocation fails. Payload fields: {@code capability}, {@code phase}
   * ({@code RESOLVE} / {@code VALIDATE} / {@code INVOKE}), {@code errorMessage}.
   */
  TOOL_INVOCATION_FAILED
}
