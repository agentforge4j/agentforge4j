// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeResponseSource;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.LocalFileSink;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.tool.InMemoryPendingToolInvocationStore;
import com.agentforge4j.testkit.capture.CaptureBundle;
import com.agentforge4j.testkit.capture.CapturedFile;
import com.agentforge4j.testkit.capture.CapturingFileSink;
import com.agentforge4j.testkit.capture.CapturingWorkflowEventLog;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.util.Validate;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The bootstrap-facing harness adapter: assembles an AgentForge4j runtime with the deterministic
 * fake LLM provider active, runs a workflow, and returns a {@link WorkflowRunResult} holding the
 * final state plus the captured event stream and files.
 *
 * <p>This is the single class in the testkit permitted to depend on {@code agentforge4j.bootstrap};
 * the assertion and capture layers stay assembly-agnostic. Fake is activated through the only seam
 * that accepts it — an explicit {@link LlmClientResolver} over a {@link FakeLlmClient} backed by a
 * run-agnostic {@link StaticFakeResponseSource}, which avoids any pre-{@code start} run-id
 * registration timing problem.
 */
public final class WorkflowTestHarness {

  private static final Clock DEFAULT_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
  private static final String ACTOR = "testkit-harness";

  private final Path workflowsDir;
  private final Path agentsDir;
  private final boolean shippedCatalog;
  private final FakeScript script;
  private final Clock clock;
  private final List<ToolProvider> toolProviders;
  private final ToolPolicy toolPolicy;
  private final ToolExecutionOptions toolExecutionOptions;
  private final Path fileSinkDir;

  private WorkflowTestHarness(Builder builder) {
    this.workflowsDir = builder.workflowsDir;
    this.agentsDir = builder.agentsDir;
    this.shippedCatalog = builder.shippedCatalog;
    this.script = Validate.notNull(builder.script, "script must not be null");
    this.clock = Validate.notNull(builder.clock, "clock must not be null");
    this.toolProviders = builder.toolProviders;
    this.toolPolicy = builder.toolPolicy;
    this.toolExecutionOptions = builder.toolExecutionOptions;
    this.fileSinkDir = builder.fileSinkDir;
    Validate.isTrue(shippedCatalog || workflowsDir != null,
        "either shippedCatalog(true) or workflowsDir must be set");
    Validate.isTrue(!(shippedCatalog && workflowsDir != null),
        "shippedCatalog and workflowsDir are mutually exclusive");
  }

  /**
   * Starts a new harness builder.
   *
   * @return a fresh builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Assembles a fake-backed runtime, runs the given workflow to completion or to its first
   * suspended state, and returns the captured result. Equivalent to {@link #run(String, List)} with
   * no scripted responses — the run is left at its first human-in-the-loop pause.
   *
   * @param workflowId id of a workflow present in the configured fixtures; must not be blank
   *
   * @return the run result: final state plus captured events and files
   */
  public WorkflowRunResult run(String workflowId) {
    return run(workflowId, List.of());
  }

  /**
   * Assembles a fake-backed runtime, starts the given workflow, and drives it forward by draining
   * one {@code response} at each human-in-the-loop pause (input / review / step-approval /
   * escalation) until the responses are exhausted. When the queue is exhausted at a pause, the run
   * is left at that pause so a scenario may assert a meaningful pending state. A response left over
   * after the run has already reached a terminal state is a scripting error — the scenario queued
   * more responses than the run paused for — and fails loudly rather than being silently dropped,
   * symmetric with the pause-mismatch check.
   *
   * @param workflowId id of a workflow present in the configured fixtures; must not be blank
   * @param responses  scripted human responses, one consumed per pause in order; must not be
   *                   {@code null} (may be empty)
   *
   * @return the run result: the last observed state plus captured events and files
   *
   * @throws IllegalStateException if a queued response does not match the pause the run is at, or if
   *                               the run reaches a terminal state while scripted responses remain
   *                               unconsumed
   */
  public WorkflowRunResult run(String workflowId, List<GateResponse> responses) {
    Validate.notBlank(workflowId, "workflowId must not be blank");
    Validate.notNull(responses, "responses must not be null");
    CapturingWorkflowEventLog eventLog =
        new CapturingWorkflowEventLog(new InMemoryWorkflowEventLog());
    CapturingFileSink capturingFileSink = fileSinkDir == null ? new CapturingFileSink() : null;
    FileSink fileSink =
        capturingFileSink != null ? capturingFileSink : new LocalFileSink(fileSinkDir);
    // The harness owns the pending-tool store so a scripted tool gate without an explicit id can
    // auto-target the run's single current pending invocation (the runtime is wired to this same
    // instance via the bootstrap).
    PendingToolInvocationStore pendingStore = new InMemoryPendingToolInvocationStore();
    AgentForge4j application = assemble(eventLog, fileSink, pendingStore);
    WorkflowRuntime runtime = application.runtime();
    String runId = runtime.start(workflowId);
    WorkflowState state = runtime.getState(runId);
    for (int i = 0; i < responses.size(); i++) {
      if (isTerminal(state.getStatus())) {
        throw new IllegalStateException(
            ("Run '%s' reached terminal status %s with %d scripted gate response(s) left "
                + "unconsumed; the scenario queued more responses than the run paused for")
                .formatted(runId, state.getStatus(), responses.size() - i));
      }
      applyResponse(runtime, runId, state, responses.get(i), pendingStore);
      state = runtime.getState(runId);
    }
    List<CapturedFile> files =
        capturingFileSink != null ? capturingFileSink.capturedFiles() : List.of();
    CaptureBundle captures = new CaptureBundle(eventLog.capturedEvents(), files);
    return new WorkflowRunResult(runId, state, captures);
  }

  private static boolean isTerminal(WorkflowStatus status) {
    return status == WorkflowStatus.COMPLETED
        || status == WorkflowStatus.FAILED
        || status == WorkflowStatus.CANCELLED;
  }

  private static void applyResponse(WorkflowRuntime runtime, String runId, WorkflowState state,
      GateResponse response, PendingToolInvocationStore pendingStore) {
    WorkflowStatus status = state.getStatus();
    String stepId = state.getCurrentStepId();
    if (response instanceof GateResponse.Input input) {
      requireStatus(status, WorkflowStatus.AWAITING_INPUT, response, stepId);
      runtime.submitInput(runId, input.answers(), ACTOR);
    } else if (response instanceof GateResponse.Review review) {
      requireStatus(status, WorkflowStatus.AWAITING_REVIEW, response, stepId);
      runtime.submitReview(runId, stepId, review.note(), ACTOR);
    } else if (response instanceof GateResponse.StepApproval stepApproval) {
      requireStatus(status, WorkflowStatus.AWAITING_STEP_APPROVAL, response, stepId);
      StepApprovalDecision decision = stepApproval.approve()
          ? new StepApprovalDecision.Approve(ACTOR, stepApproval.note())
          : new StepApprovalDecision.Reject(ACTOR, stepApproval.note());
      runtime.decideStepApproval(runId, stepId, decision);
    } else if (response instanceof GateResponse.Escalation escalation) {
      requireStatus(status, WorkflowStatus.AWAITING_APPROVAL, response, stepId);
      runtime.approve(runId, stepId, escalation.note(), ACTOR);
    } else if (response instanceof GateResponse.ToolApproval toolApproval) {
      requireStatus(status, WorkflowStatus.AWAITING_TOOL_APPROVAL, response, stepId);
      ApprovalDecision decision = toolApproval.approve()
          ? new ApprovalDecision.Approve(ACTOR)
          : new ApprovalDecision.Reject(ACTOR, toolApproval.reason());
      runtime.continueAfterToolApproval(runId,
          resolveToolInvocationId(toolApproval.toolInvocationId(), pendingStore, runId, response),
          decision);
    } else if (response instanceof GateResponse.ToolDecision toolDecision) {
      requireStatus(status, WorkflowStatus.AWAITING_TOOL_DECISION, response, stepId);
      ToolDecision decision = toolDecision.retry()
          ? new ToolDecision.Retry(ACTOR)
          : new ToolDecision.Continue(ACTOR);
      runtime.resolveToolDecision(runId,
          resolveToolInvocationId(toolDecision.toolInvocationId(), pendingStore, runId, response),
          decision);
    } else {
      throw new IllegalStateException("Unsupported gate response: " + response);
    }
  }

  /**
   * Resolves the tool invocation id a scripted tool gate targets: the explicit id when given, else
   * the run's single current pending invocation. Auto-targeting fails closed when the run has zero
   * or more than one pending invocation, so a scripted response can never silently resolve the wrong
   * call.
   */
  private static String resolveToolInvocationId(String explicitId,
      PendingToolInvocationStore pendingStore, String runId, GateResponse response) {
    if (explicitId != null) {
      return explicitId;
    }
    List<PendingToolInvocation> pending = pendingStore.findByRun(runId);
    Validate.isTrue(pending.size() == 1, () -> new IllegalStateException(
        ("Auto-targeted %s requires exactly one pending tool invocation for run '%s' but found %d; "
            + "use the explicit-id overload when more than one is pending")
            .formatted(response.getClass().getSimpleName(), runId, pending.size())));
    return pending.get(0).toolInvocationId();
  }

  private static void requireStatus(WorkflowStatus actual, WorkflowStatus expected,
      GateResponse response, String stepId) {
    Validate.isTrue(actual == expected, () -> new IllegalStateException(
        "Harness is at status %s (step %s) but the next queued response %s expects %s".formatted(
            actual, stepId, response.getClass().getSimpleName(), expected)));
  }

  private AgentForge4j assemble(CapturingWorkflowEventLog eventLog, FileSink fileSink,
      PendingToolInvocationStore pendingStore) {
    FakeResponseSource responseSource = new StaticFakeResponseSource(script);
    LlmClientResolver resolver = new FakeLlmClientResolver(new FakeLlmClient(responseSource));
    AgentForge4jBootstrap.Builder bootstrap = AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(resolver)
        .withLlmProviderSelectionStrategy(new FakeProviderSelectionStrategy())
        .withWorkflowEventLog(eventLog)
        .withFileSink(fileSink)
        .withClock(clock)
        .withPendingToolInvocationStore(pendingStore)
        .withLoadShippedAgents(shippedCatalog)
        .withLoadShippedWorkflows(shippedCatalog);
    if (!shippedCatalog) {
      bootstrap.withWorkflowsDir(workflowsDir);
      if (agentsDir != null) {
        bootstrap.withAgentsDir(agentsDir);
      }
    }
    if (!toolProviders.isEmpty()) {
      bootstrap.withToolProviders(toolProviders);
    }
    if (toolPolicy != null) {
      bootstrap.withToolPolicy(toolPolicy);
    }
    if (toolExecutionOptions != null) {
      bootstrap.withToolExecutionOptions(toolExecutionOptions);
    }
    return bootstrap.build();
  }

  /**
   * Fluent builder for {@link WorkflowTestHarness}. {@code workflowsDir} and {@code script} are
   * required; {@code agentsDir} is optional (agents bundled inside the workflow need none) and the
   * clock defaults to a fixed epoch clock for deterministic event timestamps.
   */
  public static final class Builder {

    private Path workflowsDir;
    private Path agentsDir;
    private boolean shippedCatalog;
    private FakeScript script;
    private Clock clock = DEFAULT_CLOCK;
    private List<ToolProvider> toolProviders = List.of();
    private ToolPolicy toolPolicy;
    private ToolExecutionOptions toolExecutionOptions;
    private Path fileSinkDir;

    private Builder() {
    }

    /**
     * Sets the directory the config-loader scans for workflow bundles. Required unless
     * {@link #shippedCatalog(boolean)} is enabled; mutually exclusive with it.
     *
     * @param value workflows directory; must not be {@code null}
     *
     * @return this builder
     */
    public Builder workflowsDir(Path value) {
      this.workflowsDir = Validate.notNull(value, "workflowsDir must not be null");
      return this;
    }

    /**
     * Loads the real shipped workflow + agent catalog from the classpath (the production
     * {@code loadShippedWorkflows} / {@code loadShippedAgents} path) instead of a fixtures
     * directory. Mutually exclusive with {@link #workflowsDir(Path)} / {@link #agentsDir(Path)}.
     *
     * @param value {@code true} to verify the genuine shipped catalog
     *
     * @return this builder
     */
    public Builder shippedCatalog(boolean value) {
      this.shippedCatalog = value;
      return this;
    }

    /**
     * Sets the directory the config-loader scans for standalone agent bundles. Optional — omit when
     * the fixture bundles its agents inside the workflow.
     *
     * @param value agents directory; must not be {@code null}
     *
     * @return this builder
     */
    public Builder agentsDir(Path value) {
      this.agentsDir = Validate.notNull(value, "agentsDir must not be null");
      return this;
    }

    /**
     * Sets the parsed fake script served to every run.
     *
     * @param value the script; must not be {@code null}
     *
     * @return this builder
     */
    public Builder script(FakeScript value) {
      this.script = Validate.notNull(value, "script must not be null");
      return this;
    }

    /**
     * Overrides the clock used for deterministic event timestamps.
     *
     * @param value the clock; must not be {@code null}
     *
     * @return this builder
     */
    public Builder clock(Clock value) {
      this.clock = Validate.notNull(value, "clock must not be null");
      return this;
    }

    /**
     * Routes {@code CREATE_FILE} writes to the production {@link LocalFileSink} rooted at the given
     * directory — exercising the real path-traversal guard and writing to disk — instead of the
     * default {@link CapturingFileSink} that records writes verbatim without filesystem enforcement.
     * When set, {@link WorkflowRunResult#captures()} carries no captured files; assert against the
     * directory instead.
     *
     * @param value root directory for file writes; must not be {@code null}
     *
     * @return this builder
     */
    public Builder fileSinkDir(Path value) {
      this.fileSinkDir = Validate.notNull(value, "fileSinkDir must not be null");
      return this;
    }

    /**
     * Wires in-process tool providers so an agent's {@code TOOL_INVOCATION} resolves to a real
     * provider without a network transport. The capability in the command is matched against each
     * provider's {@code listTools()} descriptors.
     *
     * @param value tool providers; must not be {@code null} (defensively copied)
     *
     * @return this builder
     */
    public Builder toolProviders(List<ToolProvider> value) {
      this.toolProviders = List.copyOf(Validate.notNull(value, "toolProviders must not be null"));
      return this;
    }

    /**
     * Overrides the tool policy evaluated before a tool invocation. Defaults to the bootstrap's
     * secure-by-default policy ({@code SecureDefaultToolPolicy}) when unset; supply
     * {@code ToolPolicy.allowAll()} or a custom policy returning {@code RequireApproval} or
     * {@code Deny} to exercise the approval/decision suspend-resume paths.
     *
     * @param value the tool policy; must not be {@code null}
     *
     * @return this builder
     */
    public Builder toolPolicy(ToolPolicy value) {
      this.toolPolicy = Validate.notNull(value, "toolPolicy must not be null");
      return this;
    }

    /**
     * Overrides the tool-invocation tunables, most usefully the authoritative per-invocation
     * timeout — pair a short timeout with a deliberately hanging provider to exercise the
     * timeout arm of the governance chokepoint. Defaults to the bootstrap's
     * {@code ToolExecutionOptions.defaults()} when unset.
     *
     * @param value the options; must not be {@code null}
     *
     * @return this builder
     */
    public Builder toolExecutionOptions(ToolExecutionOptions value) {
      this.toolExecutionOptions =
          Validate.notNull(value, "toolExecutionOptions must not be null");
      return this;
    }

    /**
     * Builds the harness.
     *
     * @return a configured {@link WorkflowTestHarness}
     */
    public WorkflowTestHarness build() {
      return new WorkflowTestHarness(this);
    }
  }
}
