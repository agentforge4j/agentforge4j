// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.requirement.DefaultRequirementResolver;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.runtime.command.CommandApplier;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.command.handler.CompleteCommandHandler;
import com.agentforge4j.runtime.command.handler.ContinueCommandHandler;
import com.agentforge4j.runtime.command.handler.CreateFileCommandHandler;
import com.agentforge4j.runtime.command.handler.EscalateCommandHandler;
import com.agentforge4j.runtime.command.handler.GeneralQuestionCommandHandler;
import com.agentforge4j.runtime.command.handler.RunCommandHandler;
import com.agentforge4j.runtime.command.handler.SetContextCommandHandler;
import com.agentforge4j.runtime.command.handler.UserPromptCommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.BlueprintExecutor;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.StepExecutor;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.execution.WorkflowExecutor;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.AgentBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.AssignContextBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.BranchBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.FailBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.InputBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.ResourceBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.RetryPreviousBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.SparBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.ValidateBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.WorkflowBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.resource.SafeClasspathResourceResolver;
import com.agentforge4j.runtime.execution.loop.AgentSignalLoopStrategy;
import com.agentforge4j.runtime.execution.loop.DefaultLoopEvaluator;
import com.agentforge4j.runtime.execution.loop.EvaluatorLoopStrategy;
import com.agentforge4j.runtime.execution.loop.FixedCountLoopStrategy;
import com.agentforge4j.runtime.execution.loop.ForEachLoopStrategy;
import com.agentforge4j.runtime.execution.loop.MaxIterationsHandler;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.tool.ToolInvocationCommandHandler;
import com.agentforge4j.runtime.tool.ToolResultApplier;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.ObjectUtils;

import static com.agentforge4j.runtime.command.FileSink.NO_OP_FILE_SINK;
import static com.agentforge4j.runtime.command.ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER;

/**
 * Fluent builder that wires a {@link DefaultWorkflowRuntime} with the canonical executor graph, behaviour handlers,
 * loop strategies, and command handlers.
 *
 * <p>Required collaborators are repositories, a pre-built {@link AgentInvoker}, {@link FileSink},
 * and {@link ShellCommandRunner}. {@link java.time.Clock}, {@link LoopEvaluator}, and {@link RunContextManager} default
 * when omitted. A {@link com.agentforge4j.schema.SchemaProvider} may be configured but is not read by the current
 * {@link #build()} implementation.
 *
 * <p>Public construction path for {@link com.agentforge4j.core.runtime.WorkflowRuntime};
 * {@link DefaultWorkflowRuntime} constructors stay package-private because they accept non-exported execution types.
 */
public final class WorkflowRuntimeBuilder {

  private WorkflowRepository workflowRepository;
  private WorkflowStateRepository workflowStateRepository;
  private WorkflowEventLog workflowEventLog;
  private Clock clock;
  private FileSink fileSink;
  private ShellCommandRunner shellCommandRunner;
  private LoopEvaluator loopEvaluator;
  private RunContextManager runContextManager = RunContextManager.NO_OP;
  private int maxNestingDepth = DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH;
  private AgentInvoker agentInvoker;
  private EventRecorder eventRecorder;
  private ToolExecutionService toolExecutionService;
  private PendingToolInvocationStore pendingToolInvocationStore;
  private RequirementResolver requirementResolver;
  private RunExecutionInterceptor runExecutionInterceptor = RunExecutionInterceptor.NO_OP;
  private GeneratedArtifactStore generatedArtifactStore;
  private List<ArtifactValidator> artifactValidators = List.of();

  /**
   * Configures the workflow definition source.
   *
   * @param value repository instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder workflowRepository(WorkflowRepository value) {
    this.workflowRepository = Validate.notNull(value, "workflowRepository must not be null");
    return this;
  }

  /**
   * Configures persistence for {@link com.agentforge4j.core.workflow.state.WorkflowState} between drives.
   *
   * @param value repository instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder workflowStateRepository(WorkflowStateRepository value) {
    this.workflowStateRepository = Validate.notNull(value,
        "workflowStateRepository must not be null");
    return this;
  }

  /**
   * Configures the append-only event log receiving {@link com.agentforge4j.core.workflow.event.WorkflowEvent}
   * instances.
   *
   * @param value event log instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder workflowEventLog(WorkflowEventLog value) {
    this.workflowEventLog = Validate.notNull(value, "workflowEventLog must not be null");
    return this;
  }

  /**
   * Configures the clock used for timestamps on state updates and events. Defaults to
   * {@link java.time.Clock#systemUTC()} when omitted.
   *
   * @param value clock instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder clock(Clock value) {
    this.clock = Validate.notNull(value, "clock must not be null");
    return this;
  }

  /**
   * Configures where {@link com.agentforge4j.core.command.CreateFileCommand} content is written.
   *
   * @param value file sink instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder fileSink(FileSink value) {
    this.fileSink = Validate.notNull(value, "fileSink must not be null");
    return this;
  }

  /**
   * Configures execution of {@link com.agentforge4j.core.command.RunCommandCommand} shell strings.
   *
   * @param value shell runner instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder shellCommandRunner(ShellCommandRunner value) {
    this.shellCommandRunner = Validate.notNull(value, "shellCommandRunner must not be null");
    return this;
  }

  /**
   * Configures the evaluator loop strategy dependency. Defaults to {@link DefaultLoopEvaluator} when omitted.
   *
   * @param value loop evaluator instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder loopEvaluator(LoopEvaluator value) {
    this.loopEvaluator = Validate.notNull(value, "loopEvaluator must not be null");
    return this;
  }

  /**
   * Configures the maximum nested workflow depth passed to
   * {@link com.agentforge4j.runtime.execution.ExecutionContext}.
   *
   * @param value maximum nesting depth (at least 1)
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder maxNestingDepth(int value) {
    this.maxNestingDepth = Validate.isGreaterThanZero(value, "maxNestingDepth must be at least 1")
        .intValue();
    return this;
  }

  /**
   * Configures correlation scope hooks for each drive. Defaults to {@link RunContextManager#NO_OP} when omitted.
   *
   * @param value run context manager instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder runContextManager(RunContextManager value) {
    this.runContextManager = Validate.notNull(value, "runContextManager must not be null");
    return this;
  }

  /**
   * Configures the {@link AgentInvoker} used for agent and SPAR steps (for example from Spring auto-configuration or
   * constructed explicitly by non-Spring callers).
   *
   * @param value invoker instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder agentInvoker(AgentInvoker value) {
    this.agentInvoker = Validate.notNull(value, "agentInvoker must not be null");
    return this;
  }

  /**
   * Configures the shared {@link EventRecorder} used by command handlers, loop strategies, and step executors. When
   * omitted, {@link #build()} constructs one from {@link #workflowEventLog(WorkflowEventLog)} and
   * {@link #clock(Clock)}.
   *
   * @param value event recorder instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder eventRecorder(EventRecorder value) {
    this.eventRecorder = Validate.notNull(value, "eventRecorder must not be null");
    return this;
  }

  /**
   * Configures the optional tool-execution chokepoint. When set, a {@code ToolInvocationCommandHandler} is registered
   * so {@code TOOL_INVOCATION} commands are dispatched, and tool-approval resume becomes available; when omitted, tool
   * invocation is unavailable and behaviour is unchanged.
   *
   * @param value tool execution service instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder toolExecutionService(ToolExecutionService value) {
    this.toolExecutionService = Validate.notNull(value, "toolExecutionService must not be null");
    return this;
  }

  /**
   * Configures the store used to resume approval-pending tool invocations. Required alongside
   * {@link #toolExecutionService(ToolExecutionService)} for {@code continueAfterToolApproval} to function.
   *
   * @param value pending tool invocation store instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder pendingToolInvocationStore(PendingToolInvocationStore value) {
    this.pendingToolInvocationStore =
        Validate.notNull(value, "pendingToolInvocationStore must not be null");
    return this;
  }

  /**
   * Configures the resolver used to satisfy declared {@code requirements} at the run-start checkpoint and at deferred
   * first use. Defaults to {@link DefaultRequirementResolver} (default- or-empty) when omitted, so a pure-{@code core}
   * runtime still enforces the fail-fast guarantee.
   *
   * @param value requirement resolver instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder requirementResolver(RequirementResolver value) {
    this.requirementResolver = Validate.notNull(value, "requirementResolver must not be null");
    return this;
  }

  /**
   * Configures the control interceptor fired before main execution and before each LLM call. Defaults to
   * {@link RunExecutionInterceptor#NO_OP}.
   *
   * @param value run-execution interceptor instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder runExecutionInterceptor(RunExecutionInterceptor value) {
    this.runExecutionInterceptor = Validate.notNull(value, "runExecutionInterceptor must not be null");
    return this;
  }

  /**
   * Configures the run-scoped store that captures emitted {@code CREATE_FILE} bytes for in-process
   * artifact validation. Defaults to a retaining {@link InMemoryGeneratedArtifactStore} when omitted.
   *
   * @param value generated-artifact store instance
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder generatedArtifactStore(GeneratedArtifactStore value) {
    this.generatedArtifactStore =
        Validate.notNull(value, "generatedArtifactStore must not be null");
    return this;
  }

  /**
   * Configures the {@link ArtifactValidator}s a {@code VALIDATE} step may select by {@code validatorId}.
   * Defaults to none; an embedding assembly (for example bootstrap) supplies the defaults.
   *
   * @param value validators registered by id (no duplicate ids)
   *
   * @return this builder
   */
  public WorkflowRuntimeBuilder artifactValidators(List<ArtifactValidator> value) {
    this.artifactValidators =
        List.copyOf(Validate.notNull(value, "artifactValidators must not be null"));
    return this;
  }

  /**
   * Validates required dependencies, wires executors and handlers, and returns a runnable
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime}.
   *
   * @return configured runtime instance
   *
   * @throws IllegalArgumentException if a required dependency is missing or invalid
   */
  public WorkflowRuntime build() {
    validateRequired();
    Clock resolvedClock = resolveClock();
    FileSink resolvedFileSink = getResolvedFileSink();
    ShellCommandRunner resolvedShell = resolveShellCommandRunner();
    RunContextManager runContextManager = resolveRunContextManager();
    GeneratedArtifactStore resolvedGeneratedArtifactStore = resolveGeneratedArtifactStore();
    EventRecorder resolvedEventRecorder = eventRecorder != null
        ? eventRecorder
        : new EventRecorder(workflowEventLog, resolvedClock);

    CommandApplier commandApplier = new CommandApplier(determineCommandHandlers(
        resolvedEventRecorder, resolvedFileSink, resolvedShell, resolvedClock,
        resolvedGeneratedArtifactStore));

    LoopEvaluator resolvedEvaluator = resolveLoopEvaluator(agentInvoker);

    MaxIterationsHandler maxIterationsHandler = new MaxIterationsHandler(resolvedEventRecorder,
        resolvedClock);

    RequirementResolver resolvedRequirementResolver = ObjectUtils.getIfNull(requirementResolver,
        DefaultRequirementResolver::new);

    // The executor graph has a cycle: ExecutableExecutor needs BlueprintExecutor
    // and WorkflowExecutor; both of those need a StepSequenceExecutor that in
    // turn needs the ExecutableExecutor. We break the cycle with late-bound
    // setters on BlueprintExecutor and WorkflowExecutor.
    WorkflowExecutor workflowExecutor = new WorkflowExecutor(resolvedRequirementResolver);
    BlueprintExecutor blueprintExecutor = new BlueprintExecutor();

    BranchBehaviourHandler branchBehaviourHandler = new BranchBehaviourHandler(
        resolvedEventRecorder);
    RetryPreviousBehaviourHandler retryPreviousBehaviourHandler = new RetryPreviousBehaviourHandler(
        resolvedEventRecorder, resolvedGeneratedArtifactStore);
    TransitionGate transitionGate = new TransitionGate(resolvedEventRecorder);
    StepExecutor stepExecutor = buildStepExecutor(
        agentInvoker,
        commandApplier,
        resolvedEventRecorder,
        resolvedClock,
        workflowExecutor,
        branchBehaviourHandler,
        retryPreviousBehaviourHandler,
        transitionGate,
        resolvedGeneratedArtifactStore);

    ExecutableExecutor executableExecutor =
        new ExecutableExecutor(stepExecutor, blueprintExecutor, workflowExecutor,
            resolvedRequirementResolver);
    branchBehaviourHandler.setExecutableExecutor(executableExecutor);
    retryPreviousBehaviourHandler.setExecutableExecutor(executableExecutor);
    StepSequenceExecutor stepSequenceExecutor = new StepSequenceExecutor(executableExecutor);

    setupBlueprintLoopStrategies(blueprintExecutor, stepSequenceExecutor, resolvedEventRecorder,
        maxIterationsHandler,
        resolvedEvaluator);
    blueprintExecutor.setStepSequenceExecutor(stepSequenceExecutor);
    blueprintExecutor.setTransitionGate(transitionGate);
    workflowExecutor.setStepSequenceExecutor(stepSequenceExecutor);

    return new DefaultWorkflowRuntime(
        workflowRepository,
        workflowStateRepository,
        stepSequenceExecutor,
        resolvedEventRecorder,
        resolvedClock,
        runContextManager,
        maxNestingDepth,
        toolExecutionService,
        pendingToolInvocationStore,
        resolvedRequirementResolver,
        transitionGate,
        runExecutionInterceptor,
        resolvedGeneratedArtifactStore);
  }

  private static void setupBlueprintLoopStrategies(BlueprintExecutor blueprintExecutor,
      StepSequenceExecutor stepSequenceExecutor, EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler, LoopEvaluator resolvedEvaluator) {
    blueprintExecutor.setLoopStrategies(List.of(
        new FixedCountLoopStrategy(stepSequenceExecutor, eventRecorder, maxIterationsHandler),
        new ForEachLoopStrategy(stepSequenceExecutor, eventRecorder, maxIterationsHandler),
        new AgentSignalLoopStrategy(stepSequenceExecutor, eventRecorder, maxIterationsHandler),
        new EvaluatorLoopStrategy(
            stepSequenceExecutor, eventRecorder, maxIterationsHandler, resolvedEvaluator)));
  }

  private List<CommandHandler<? extends LlmCommand>> determineCommandHandlers(
      EventRecorder eventRecorder, FileSink resolvedFileSink, ShellCommandRunner resolvedShell,
      Clock resolvedClock, GeneratedArtifactStore resolvedGeneratedArtifactStore) {
    List<CommandHandler<? extends LlmCommand>> handlers = new ArrayList<>(List.of(
        new CompleteCommandHandler(eventRecorder),
        new ContinueCommandHandler(),
        new CreateFileCommandHandler(eventRecorder, resolvedFileSink, resolvedGeneratedArtifactStore),
        new EscalateCommandHandler(eventRecorder, resolvedClock),
        new GeneralQuestionCommandHandler(eventRecorder, resolvedClock),
        new RunCommandHandler(eventRecorder, resolvedShell),
        new SetContextCommandHandler(eventRecorder),
        new UserPromptCommandHandler(eventRecorder, resolvedClock)));
    // TOOL_INVOCATION is dispatched only when a ToolExecutionService is configured; otherwise the
    // command is never advertised (opt-in) and never reaches the applier.
    if (toolExecutionService != null) {
      handlers.add(new ToolInvocationCommandHandler(
          toolExecutionService, new ToolResultApplier(eventRecorder), resolvedClock));
    }
    return List.copyOf(handlers);
  }

  private StepExecutor buildStepExecutor(AgentInvoker agentInvoker,
      CommandApplier commandApplier,
      EventRecorder eventRecorder,
      Clock resolvedClock,
      WorkflowExecutor workflowExecutor,
      BranchBehaviourHandler branchBehaviourHandler,
      RetryPreviousBehaviourHandler retryPreviousBehaviourHandler,
      TransitionGate transitionGate,
      GeneratedArtifactStore generatedArtifactStore) {
    List<BehaviourHandler<? extends StepBehaviour>> handlers = List.of(
        new AgentBehaviourHandler(agentInvoker, commandApplier, eventRecorder),
        new SparBehaviourHandler(agentInvoker, commandApplier, eventRecorder),
        new WorkflowBehaviourHandler(workflowRepository, workflowExecutor),
        new InputBehaviourHandler(eventRecorder, resolvedClock),
        new ResourceBehaviourHandler(new SafeClasspathResourceResolver()),
        branchBehaviourHandler,
        new FailBehaviourHandler(),
        retryPreviousBehaviourHandler,
        new ValidateBehaviourHandler(generatedArtifactStore, artifactValidators, eventRecorder),
        new AssignContextBehaviourHandler(eventRecorder));
    return new StepExecutor(handlers, eventRecorder, resolvedClock, transitionGate);
  }

  private ShellCommandRunner resolveShellCommandRunner() {
    return shellCommandRunner != null ? shellCommandRunner : NO_OP_SHELL_COMMAND_RUNNER;
  }

  private FileSink getResolvedFileSink() {
    return fileSink != null ? fileSink : NO_OP_FILE_SINK;
  }

  private LoopEvaluator resolveLoopEvaluator(AgentInvoker agentInvoker) {
    return loopEvaluator != null ? loopEvaluator : new DefaultLoopEvaluator(agentInvoker);
  }

  private Clock resolveClock() {
    return clock != null ? clock : Clock.systemUTC();
  }

  private RunContextManager resolveRunContextManager() {
    return runContextManager != null ? runContextManager : RunContextManager.NO_OP;
  }

  private GeneratedArtifactStore resolveGeneratedArtifactStore() {
    return generatedArtifactStore != null
        ? generatedArtifactStore
        : new InMemoryGeneratedArtifactStore();
  }

  private void validateRequired() {
    Validate.notNull(workflowRepository, "workflowRepository is required");
    Validate.notNull(workflowStateRepository, "workflowStateRepository is required");
    Validate.notNull(workflowEventLog, "workflowEventLog is required");
    Validate.notNull(agentInvoker, "agentInvoker is required");
    Validate.notNull(runContextManager, "runContextManager is required");
  }
}
