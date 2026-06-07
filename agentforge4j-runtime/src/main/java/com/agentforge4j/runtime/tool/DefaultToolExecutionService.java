package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
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
   * unchanged. The denial/failure detail is recorded as the pending {@code reason}.
   */
  private ToolExecutionOutcome suspendForDecision(ToolInvocationCommand cmd,
      ToolInvocationContext ctx, String argumentsJson, ToolExecutionOutcome outcome) {
    if (pendingStore.find(ctx.runId(), cmd.toolInvocationId()) == null) {
      pendingStore.save(PendingToolInvocation.forDecision(
          cmd, ctx, argumentsJson, outcome.detail(), clock.instant()));
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

  @Override
  public ToolExecutionOutcome resume(String runId, String toolInvocationId,
      ApprovalDecision decision) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    Validate.notNull(decision, "decision must not be null");

    PendingToolInvocation pending = pendingStore.find(runId, toolInvocationId);
    if (pending == null) {
      return ToolExecutionOutcome.failed(null,
          "No pending tool invocation '%s' for run '%s'".formatted(toolInvocationId, runId));
    }

    ToolInvocationContext ctx = new ToolInvocationContext(pending.runId(), pending.stepUid(),
        pending.agentId(), new ToolScope(pending.workflowId(), pending.runId()));

    if (decision instanceof ApprovalDecision.Reject reject) {
      emitDenied(pending.capability(), ctx, rejectionReason(reject));
      pendingStore.remove(runId, toolInvocationId);
      return ToolExecutionOutcome.denied(rejectionReason(reject));
    }

    try {
      Prepared prepared = resolveAndValidate(pending.capability(), pending.arguments(), ctx);
      if (prepared.failure() != null) {
        return prepared.failure();
      }
      return invokeWithRetry(
          prepared.resolved(), pending.capability(), pending.arguments(), ctx);
    } finally {
      pendingStore.remove(runId, toolInvocationId);
    }
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
