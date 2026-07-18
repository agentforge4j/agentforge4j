// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.PolicyDenialTerminalException;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.tool.ToolInvocationClaimLostException;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Default {@link ToolExecutionService}: the single chokepoint for tool invocation. It emits audit
 * events, resolves the capability, validates arguments, evaluates policy, and invokes the provider
 * under an
 * <em>authoritative</em> hard timeout that the service owns regardless of provider behaviour. On
 * approval it persists a {@link PendingToolInvocation} and resumes later via {@link #resume}, never
 * re-invoking the LLM.
 */
public final class DefaultToolExecutionService implements ToolExecutionService {

  private static final System.Logger LOG =
      System.getLogger(DefaultToolExecutionService.class.getName());

  private final ToolProviderResolver resolver;
  private final ToolPolicy policy;
  private final PendingToolInvocationStore pendingStore;
  private final ToolExecutionOptions options;
  private final EventRecorder eventRecorder;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final ToolArgumentValidator argumentValidator;
  private final ExecutorService invocationExecutor;

  /**
   * Creates the chokepoint service with its collaborators.
   *
   * @param resolver      capability resolver (sole resolver; fail-fast)
   * @param policy        policy gate
   * @param pendingStore  store for approval-pending invocations
   * @param options       invocation tunables, including the authoritative timeout
   * @param eventRecorder audit sink
   * @param objectMapper  JSON mapper for argument validation and event payloads
   * @param clock         clock for pending-invocation timestamps
   */
  public DefaultToolExecutionService(ToolProviderResolver resolver,
      ToolPolicy policy,
      PendingToolInvocationStore pendingStore,
      ToolExecutionOptions options,
      EventRecorder eventRecorder,
      ObjectMapper objectMapper,
      Clock clock) {
    this.resolver = Validate.notNull(resolver, "resolver must not be null");
    this.policy = Validate.notNull(policy, "policy must not be null");
    this.pendingStore = Validate.notNull(pendingStore, "pendingStore must not be null");
    this.options = Validate.notNull(options, "options must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
    this.argumentValidator = new ToolArgumentValidator(objectMapper);
    this.invocationExecutor = Executors.newCachedThreadPool(daemonThreadFactory());
  }

  @Override
  public ToolExecutionOutcome execute(ToolInvocationCommand cmd, ToolInvocationContext ctx) {
    Validate.notNull(cmd, "cmd must not be null");
    Validate.notNull(ctx, "ctx must not be null");
    emitRequested(cmd, ctx);
    String argumentsJson = serializeArguments(cmd.arguments());

    Prepared prepared = resolveAndValidate(cmd.capability(), argumentsJson, ctx);
    if (prepared.failure() != null) {
      // RESOLVE / VALIDATE failures are deterministic — not retried; suspend for an operator decision.
      return suspendForDecision(cmd, ctx, argumentsJson, prepared.failure());
    }
    ResolvedTool resolved = prepared.resolved();

    PolicyDecision decision = policy.evaluate(cmd, resolved.descriptor(), ctx);
    if (decision instanceof PolicyDecision.Deny deny) {
      emitDenied(cmd.capability(), ctx, deny.reason());
      return suspendForDecision(cmd, ctx, argumentsJson,
          ToolExecutionOutcome.denied(deny.reason()));
    }
    if (decision instanceof PolicyDecision.RequireApproval requireApproval) {
      pendingStore.save(
          PendingToolInvocation.pending(cmd, ctx, requireApproval, argumentsJson, clock.instant()));
      emitApprovalPending(cmd.capability(), ctx, requireApproval.reason(),
          requireApproval.approverScope());
      return ToolExecutionOutcome.approvalPending(requireApproval.reason());
    }

    ToolExecutionOutcome outcome = invokeWithRetry(resolved, cmd.capability(), argumentsJson, ctx);
    if (outcome.status() == ToolExecutionOutcome.Status.FAILED) {
      return suspendForDecision(cmd, ctx, argumentsJson, outcome);
    }
    return outcome;
  }

  /**
   * Persists a pending row (unless one already exists for this run + invocation id) so the operator
   * can {@code Continue} or {@code Retry} the denied/failed call, and returns the outcome
   * unchanged. The denial/failure detail is recorded as the pending {@code reason}; the row's
   * {@link PendingToolInvocation.Origin} is derived from the outcome status so {@link #resume} can
   * later tell a policy denial from an execution failure.
   */
  private ToolExecutionOutcome suspendForDecision(ToolInvocationCommand cmd,
      ToolInvocationContext ctx, String argumentsJson, ToolExecutionOutcome outcome) {
    if (pendingStore.find(ctx.runId(), cmd.toolInvocationId()) == null) {
      PendingToolInvocation.Origin origin = outcome.status() == ToolExecutionOutcome.Status.DENIED
          ? PendingToolInvocation.Origin.POLICY_DENIED
          : PendingToolInvocation.Origin.EXECUTION_FAILED;
      pendingStore.save(PendingToolInvocation.forDecision(
          cmd, ctx, argumentsJson, outcome.detail(), origin, clock.instant()));
    }
    return outcome;
  }

  /**
   * Serializes structured tool arguments to JSON text for the {@code ToolProvider} boundary. Empty
   * arguments serialize to {@code "{}"}.
   */
  private String serializeArguments(Map<String, Object> arguments) {
    if (arguments == null || arguments.isEmpty()) {
      return "{}";
    }
    try {
      return objectMapper.writeValueAsString(arguments);
    } catch (JsonProcessingException e) {
      // Arguments originate from an already-parsed LlmCommand; serialization failure is not expected.
      throw new IllegalStateException("Failed to serialize tool arguments", e);
    }
  }

  /**
   * Invokes under the authoritative timeout, retrying only {@code INVOKE} failures up to
   * {@code options.maxRetries()} with {@code options.retryBackoff()} between attempts. RESOLVE and
   * VALIDATE failures never reach here (they short-circuit before invoke) and so are never
   * retried.
   */
  private ToolExecutionOutcome invokeWithRetry(ResolvedTool resolved, String capability,
      String arguments, ToolInvocationContext ctx) {
    int attempt = 0;
    ToolExecutionOutcome outcome = invokeUnderTimeout(resolved, capability, arguments, ctx);
    while (outcome.status() == ToolExecutionOutcome.Status.FAILED
        && attempt < options.maxRetries()) {
      attempt++;
      if (!sleepBackoff(options.retryBackoff())) {
        return outcome;
      }
      outcome = invokeUnderTimeout(resolved, capability, arguments, ctx);
    }
    return outcome;
  }

  /**
   * @return {@code true} to proceed with the next attempt, {@code false} if interrupted while
   * backing off (in which case the caller stops retrying)
   */
  private static boolean sleepBackoff(Duration backoff) {
    if (backoff.isZero() || backoff.isNegative()) {
      return true;
    }
    try {
      Thread.sleep(backoff.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>There is no claimable pending row for {@code runId}/{@code toolInvocationId} — never pending,
   * already resolved, or already claimed/replaced by a concurrent resume — this call throws
   * {@link ToolInvocationClaimLostException} without mutating any state; this is a benign
   * concurrency-loss signal, never reported as a provider/tool failure. When a row is found, a
   * {@link PendingToolInvocation.Origin#POLICY_DENIED} row is terminal: an
   * {@link ApprovalDecision.Approve} — whether issued by the runtime's {@code ToolDecision.Retry} or
   * a direct SPI call — never reaches the provider for such a row. Before treating it as terminal,
   * this re-verifies (via {@link PendingToolInvocationStore#verifyStillCurrent}) that the exact row
   * this call originally observed is still the current one — a concurrent {@link
   * ApprovalDecision.Reject} or the runtime's non-executing {@code ToolDecision.Continue} may have
   * already resolved it in the meantime, in which case this call throws
   * {@link ToolInvocationClaimLostException} instead, never an override-rejected event for a row that
   * is no longer even pending. Only once still confirmed current is it rejected with a
   * {@link PolicyDenialTerminalException} and an audited override-rejected event, with the row left
   * in place (never claimed/removed here) so that later legitimate resolver can still resolve it.
   * Every other pending row is claimed atomically, tied to the exact row this call observed via {@link
   * PendingToolInvocationStore#find} (see {@link PendingToolInvocationStore#claim}), before its
   * provider is invoked, so at most one of two concurrent resumes on the same invocation executes;
   * the other — or a resume whose observed row was replaced by a different one before its own claim
   * attempt — never reaches the provider and throws the same {@link ToolInvocationClaimLostException}.
   * If the claimed call itself ends in {@link ToolExecutionOutcome.Status#FAILED}, a fresh pending
   * row is persisted (origin {@link PendingToolInvocation.Origin#EXECUTION_FAILED}) so the operator
   * gets a further decision point rather than the run silently losing the pending invocation.
   *
   * @throws ToolInvocationClaimLostException if there is no claimable pending row for this
   *                                           invocation, whether because it was never pending,
   *                                           already resolved, or claimed/replaced by a concurrent
   *                                           resume before this call's own claim attempt
   */
  @Override
  public ToolExecutionOutcome resume(String runId, String toolInvocationId,
      ApprovalDecision decision) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    Validate.notNull(decision, "decision must not be null");

    PendingToolInvocation peeked = pendingStore.find(runId, toolInvocationId);
    if (peeked == null) {
      // No row to peek at all: either this id was never pending, or a concurrent resume already
      // claimed/removed it before this call's own find() ran — the two are indistinguishable from
      // here, and callers that reach resume() only after confirming a row exists (both runtime
      // entry points do) can only land here via the latter. Either way, there is nothing to claim,
      // so this must never be reported as a provider/tool failure.
      throw claimLost(runId, toolInvocationId);
    }

    if (decision instanceof ApprovalDecision.Reject reject) {
      PendingToolInvocation claimed = pendingStore.claim(runId, toolInvocationId, peeked);
      if (claimed == null) {
        throw claimLost(runId, toolInvocationId);
      }
      ToolInvocationContext ctx = contextOf(claimed);
      emitDenied(claimed.capability(), ctx, rejectionReason(reject));
      return ToolExecutionOutcome.denied(rejectionReason(reject));
    }

    // Sealed ApprovalDecision has exactly two variants; Reject is handled above, so this is Approve.
    if (peeked.origin() == PendingToolInvocation.Origin.POLICY_DENIED) {
      // peeked may already be stale by the time this line runs (a concurrent Reject, or the
      // runtime's non-executing Continue, may have already resolved this exact row between this
      // call's find() above and here) — verifyStillCurrent re-confirms, atomically against every
      // concurrent claim/save/remove, that the row is still exactly the one this call observed
      // before treating it as a terminal-denial override attempt. A caller whose row was already
      // resolved elsewhere in the meantime must be reported as a lost claim instead, never as an
      // override rejection for a row that is no longer even pending.
      PendingToolInvocation stillDenied =
          pendingStore.verifyStillCurrent(runId, toolInvocationId, peeked);
      if (stillDenied == null) {
        throw claimLost(runId, toolInvocationId);
      }
      // Terminal: a policy denial can never be executed via Retry/Approve. The row is deliberately
      // NOT claimed/removed here, so a later Reject or the runtime's non-executing Continue still
      // resolves it.
      ToolInvocationContext ctx = contextOf(stillDenied);
      String reason = "Tool invocation '%s' for run '%s' was denied by policy (%s); only Reject or "
          .formatted(toolInvocationId, runId, stillDenied.reason())
          + "the runtime's non-executing Continue may resolve it, never Retry/Approve";
      emitOverrideRejected(stillDenied.capability(), ctx, reason);
      throw new PolicyDenialTerminalException(reason);
    }

    PendingToolInvocation claimed = pendingStore.claim(runId, toolInvocationId, peeked);
    if (claimed == null) {
      throw claimLost(runId, toolInvocationId);
    }
    ToolInvocationContext ctx = contextOf(claimed);

    Prepared prepared = resolveAndValidate(claimed.capability(), claimed.arguments(), ctx);
    if (prepared.failure() != null) {
      reSuspendForDecision(claimed, prepared.failure());
      return prepared.failure();
    }
    ToolExecutionOutcome outcome =
        invokeWithRetry(prepared.resolved(), claimed.capability(), claimed.arguments(), ctx);
    if (outcome.status() == ToolExecutionOutcome.Status.FAILED) {
      reSuspendForDecision(claimed, outcome);
    }
    return outcome;
  }

  /**
   * There is no claimable pending row for {@code runId}/{@code toolInvocationId} right now: either
   * it was never pending, it was already resolved, or a concurrent resume already claimed or
   * replaced it before this call could claim the exact row it observed. Never a provider/tool
   * failure — the caller must not translate this into run-state mutation.
   */
  private static ToolInvocationClaimLostException claimLost(String runId, String toolInvocationId) {
    return new ToolInvocationClaimLostException(
        "No claimable pending tool invocation '%s' for run '%s' (never pending, already resolved, "
            .formatted(toolInvocationId, runId)
            + "or claimed/replaced by a concurrent resume)");
  }

  private static ToolInvocationContext contextOf(PendingToolInvocation pending) {
    return new ToolInvocationContext(pending.runId(), pending.stepUid(), pending.agentId(),
        new ToolScope(pending.workflowId(), pending.runId()));
  }

  /**
   * Persists a fresh pending row when a claimed {@code resume()} attempt itself ends in FAILED, so
   * the operator gets a further decision point instead of the run silently losing the pending
   * invocation once this method's atomic claim has already removed the original row.
   */
  private void reSuspendForDecision(PendingToolInvocation claimed, ToolExecutionOutcome outcome) {
    pendingStore.save(new PendingToolInvocation(
        claimed.toolInvocationId(), claimed.runId(), claimed.stepUid(), claimed.agentId(),
        claimed.workflowId(), claimed.capability(), claimed.arguments(), claimed.llmRationale(),
        outcome.detail(), null, PendingToolInvocation.Origin.EXECUTION_FAILED, clock.instant()));
  }

  /**
   * Shared prelude for {@link #execute} and {@link #resume}: resolves the capability then validates
   * the arguments against its input schema. On failure it emits the terminal failure event and
   * carries the resulting outcome back; the caller branches on {@link Prepared#failure()}.
   */
  private Prepared resolveAndValidate(String capability, String arguments,
      ToolInvocationContext ctx) {
    ResolvedTool resolved;
    try {
      resolved = resolver.resolve(capability, ctx.scope());
    } catch (CapabilityResolutionException e) {
      return new Prepared(null, fail(capability, ctx, ToolFailurePhase.RESOLVE, e.getMessage()));
    }

    ToolArgumentValidator.Result validation =
        argumentValidator.validate(arguments, resolved.descriptor().inputSchema());
    if (!validation.ok()) {
      return new Prepared(null,
          fail(capability, ctx, ToolFailurePhase.VALIDATE, validation.message()));
    }

    return new Prepared(resolved, null);
  }

  /**
   * Result of {@link #resolveAndValidate}: exactly one field is non-null — {@code resolved} when
   * the capability resolved and its arguments validated, or {@code failure} carrying the
   * already-emitted terminal outcome otherwise.
   */
  private record Prepared(ResolvedTool resolved, ToolExecutionOutcome failure) {

  }

  private ToolExecutionOutcome invokeUnderTimeout(ResolvedTool resolved, String capability,
      String arguments, ToolInvocationContext ctx) {
    Future<ToolResult> future = invocationExecutor.submit(
        () -> resolved.provider().invoke(resolved.descriptor(), arguments, ctx, options));
    try {
      ToolResult result = future.get(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!result.success()) {
        return fail(capability, ctx, ToolFailurePhase.INVOKE, result.errorMessage(), result);
      }
      emitCompleted(capability, ctx, result.latencyMillis());
      return ToolExecutionOutcome.executed(result);
    } catch (TimeoutException e) {
      future.cancel(true);
      return fail(capability, ctx, ToolFailurePhase.INVOKE,
          "tool invocation timed out after %s".formatted(options.timeout()));
    } catch (ExecutionException e) {
      return fail(capability, ctx, ToolFailurePhase.INVOKE, rootMessage(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return fail(capability, ctx, ToolFailurePhase.INVOKE, "tool invocation interrupted");
    }
  }

  private ToolExecutionOutcome fail(String capability, ToolInvocationContext ctx,
      ToolFailurePhase phase, String errorMessage) {
    return fail(capability, ctx, phase, errorMessage, null);
  }

  private ToolExecutionOutcome fail(String capability, ToolInvocationContext ctx,
      ToolFailurePhase phase, String errorMessage, ToolResult result) {
    emitFailed(capability, ctx, phase, errorMessage);
    return ToolExecutionOutcome.failed(result, errorMessage);
  }

  private void emitRequested(ToolInvocationCommand cmd, ToolInvocationContext ctx) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("capability", cmd.capability());
    payload.put("agentId", ctx.agentId());
    payload.put("stepUid", ctx.stepUid());
    payload.put("llmRationale", cmd.llmRationale());
    record(ctx, WorkflowEventType.TOOL_INVOCATION_REQUESTED, payload);
  }

  private void emitCompleted(String capability, ToolInvocationContext ctx, long latencyMillis) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("capability", capability);
    payload.put("latencyMillis", latencyMillis);
    record(ctx, WorkflowEventType.TOOL_INVOCATION_COMPLETED, payload);
  }

  private void emitDenied(String capability, ToolInvocationContext ctx, String reason) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("capability", capability);
    payload.put("reason", reason);
    record(ctx, WorkflowEventType.TOOL_INVOCATION_DENIED, payload);
  }

  private void emitApprovalPending(String capability, ToolInvocationContext ctx, String reason,
      String approverScope) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("capability", capability);
    payload.put("reason", reason);
    payload.put("approverScope", approverScope);
    record(ctx, WorkflowEventType.TOOL_INVOCATION_APPROVAL_PENDING, payload);
  }

  private void emitOverrideRejected(String capability, ToolInvocationContext ctx, String reason) {
    LOG.log(System.Logger.Level.WARNING,
        "Rejected an attempt to Retry/Approve a policy-denied tool invocation capability={0}",
        capability);
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("capability", capability);
    payload.put("reason", reason);
    record(ctx, WorkflowEventType.TOOL_INVOCATION_OVERRIDE_REJECTED, payload);
  }

  private void emitFailed(String capability, ToolInvocationContext ctx, ToolFailurePhase phase,
      String errorMessage) {
    LOG.log(System.Logger.Level.WARNING,
        "Tool invocation failed capability={0}, phase={1}, error={2}",
        capability, phase, errorMessage);
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("capability", capability);
    payload.put("phase", phase.name());
    payload.put("errorMessage", errorMessage);
    record(ctx, WorkflowEventType.TOOL_INVOCATION_FAILED, payload);
  }

  private void record(ToolInvocationContext ctx, WorkflowEventType type, ObjectNode payload) {
    eventRecorder.record(ctx.runId(), null, type, payload.toString(), actorOf(ctx));
  }

  private static String actorOf(ToolInvocationContext ctx) {
    return StringUtils.defaultIfBlank(ctx.agentId(), "runtime");
  }

  private static String rejectionReason(ApprovalDecision.Reject reject) {
    return StringUtils.defaultIfBlank(reject.reason(),
        "rejected by %s".formatted(reject.rejectedBy()));
  }

  private static String rootMessage(ExecutionException e) {
    Throwable cause = ObjectUtils.getIfNull(e.getCause(), e);
    return StringUtils.defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName());
  }

  private static ThreadFactory daemonThreadFactory() {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, "af4j-tool-invoke-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
