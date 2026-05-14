package com.agentforge4j.runtime;

import static com.agentforge4j.runtime.command.FileSink.NO_OP_FILE_SINK;
import static com.agentforge4j.runtime.command.ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER;

import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.integrations.IntegrationRegistry;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.CommandApplier;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.command.handler.CallEndpointCommandHandler;
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
import com.agentforge4j.runtime.execution.WorkflowExecutor;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.AgentBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.BranchBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.FailBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.InputBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.ResourceBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.RetryPreviousBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.SparBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.handler.WorkflowBehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.resource.SafeClasspathResourceResolver;
import com.agentforge4j.runtime.execution.loop.AgentSignalLoopStrategy;
import com.agentforge4j.runtime.execution.loop.DefaultLoopEvaluator;
import com.agentforge4j.runtime.execution.loop.EvaluatorLoopStrategy;
import com.agentforge4j.runtime.execution.loop.FixedCountLoopStrategy;
import com.agentforge4j.runtime.execution.loop.ForEachLoopStrategy;
import com.agentforge4j.runtime.execution.loop.LoopEvaluator;
import com.agentforge4j.runtime.execution.loop.MaxIterationsHandler;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;

/**
 * Fluent builder that wires up a {@link DefaultWorkflowRuntime} with the canonical internal graph
 * of executors, handlers, loop strategies, and command appliers.
 *
 * <p>Only the small number of external collaborators (repositories, LLM
 * resolver, optional file sink / shell runner) need to be supplied. Everything else has a sensible
 * default.
 *
 * <p>This builder is the only public entry point for constructing a
 * {@link com.agentforge4j.core.runtime.WorkflowRuntime}. {@link DefaultWorkflowRuntime}'s
 * constructors are package-private because they take internal execution-package collaborators that
 * are not part of the exported runtime API.
 */
public final class WorkflowRuntimeBuilder {

  private WorkflowRepository workflowRepository;
  private AgentRepository agentRepository;
  private WorkflowStateRepository workflowStateRepository;
  private WorkflowEventLog workflowEventLog;
  private LlmClientResolver llmClientResolver;
  private ObjectMapper objectMapper;
  private Clock clock;
  private FileSink fileSink;
  private ShellCommandRunner shellCommandRunner;
  private IntegrationRegistry integrationRegistry;
  private LoopEvaluator loopEvaluator;
  private SchemaProvider schemaProvider;
  private RunContextManager runContextManager = RunContextManager.NO_OP;
  private int maxNestingDepth = DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH;

  public WorkflowRuntimeBuilder workflowRepository(WorkflowRepository value) {
    this.workflowRepository = Validate.notNull(value, "workflowRepository must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder agentRepository(AgentRepository value) {
    this.agentRepository = Validate.notNull(value, "agentRepository must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder workflowStateRepository(WorkflowStateRepository value) {
    this.workflowStateRepository = Validate.notNull(value,
        "workflowStateRepository must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder workflowEventLog(WorkflowEventLog value) {
    this.workflowEventLog = Validate.notNull(value, "workflowEventLog must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder llmClientResolver(LlmClientResolver value) {
    this.llmClientResolver = Validate.notNull(value, "llmClientResolver must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder objectMapper(ObjectMapper value) {
    this.objectMapper = Validate.notNull(value, "objectMapper must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder clock(Clock value) {
    this.clock = Validate.notNull(value, "clock must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder fileSink(FileSink value) {
    this.fileSink = Validate.notNull(value, "fileSink must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder shellCommandRunner(ShellCommandRunner value) {
    this.shellCommandRunner = Validate.notNull(value, "shellCommandRunner must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder loopEvaluator(LoopEvaluator value) {
    this.loopEvaluator = Validate.notNull(value, "loopEvaluator must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder schemaProvider(SchemaProvider schemaProvider) {
    this.schemaProvider = Validate.notNull(schemaProvider, "schemaProvider must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder integrationRegistry(IntegrationRegistry value) {
    this.integrationRegistry = Validate.notNull(value, "integrationRegistry must not be null");
    return this;
  }

  public WorkflowRuntimeBuilder maxNestingDepth(int value) {
    this.maxNestingDepth = Validate.isGreaterThanZero(value, "maxNestingDepth must be at least 1")
        .intValue();
    return this;
  }

  public WorkflowRuntimeBuilder runContextManager(RunContextManager value) {
    this.runContextManager = Validate.notNull(value, "runContextManager must not be null");
    return this;
  }

  public WorkflowRuntime build() {
    validateRequired();
    ObjectMapper mapper = resolveObjectMapper();
    Clock resolvedClock = resolveClock();
    IntegrationRegistry resolvedRegistry = resolveIntegrationRegistry();
    FileSink resolvedFileSink = getResolvedFileSink();
    ShellCommandRunner resolvedShell = resolveShellCommandRunner();
    EventRecorder eventRecorder = new EventRecorder(workflowEventLog, resolvedClock);

    CommandApplier commandApplier = new CommandApplier(determineCommandHandlers(
        eventRecorder, resolvedFileSink, resolvedShell, resolvedClock, resolvedRegistry));

    AgentInvoker agentInvoker = new AgentInvoker(
        agentRepository,
        llmClientResolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        eventRecorder);

    LoopEvaluator resolvedEvaluator = resolveLoopEvaluator(agentInvoker);

    MaxIterationsHandler maxIterationsHandler = new MaxIterationsHandler(eventRecorder,
        resolvedClock);

    // The executor graph has a cycle: ExecutableExecutor needs BlueprintExecutor
    // and WorkflowExecutor; both of those need a StepSequenceExecutor that in
    // turn needs the ExecutableExecutor. We break the cycle with late-bound
    // setters on BlueprintExecutor and WorkflowExecutor.
    WorkflowExecutor workflowExecutor = new WorkflowExecutor();
    BlueprintExecutor blueprintExecutor = new BlueprintExecutor();

    BranchBehaviourHandler branchBehaviourHandler = new BranchBehaviourHandler(eventRecorder);
    RetryPreviousBehaviourHandler retryPreviousBehaviourHandler = new RetryPreviousBehaviourHandler(
        eventRecorder);
    StepExecutor stepExecutor = buildStepExecutor(
        agentInvoker,
        commandApplier,
        eventRecorder,
        resolvedClock,
        workflowExecutor,
        branchBehaviourHandler,
        retryPreviousBehaviourHandler);

    ExecutableExecutor executableExecutor =
        new ExecutableExecutor(stepExecutor, blueprintExecutor, workflowExecutor);
    branchBehaviourHandler.setExecutableExecutor(executableExecutor);
    retryPreviousBehaviourHandler.setExecutableExecutor(executableExecutor);
    StepSequenceExecutor stepSequenceExecutor = new StepSequenceExecutor(executableExecutor);

    setupBlueprintLoopStrategies(blueprintExecutor, stepSequenceExecutor, eventRecorder,
        maxIterationsHandler,
        resolvedEvaluator);
    blueprintExecutor.setStepSequenceExecutor(stepSequenceExecutor);
    workflowExecutor.setStepSequenceExecutor(stepSequenceExecutor);

    return new DefaultWorkflowRuntime(
        workflowRepository,
        workflowStateRepository,
        stepSequenceExecutor,
        executableExecutor,
        eventRecorder,
        resolvedClock,
        runContextManager,
        maxNestingDepth);
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

  private ShellCommandRunner resolveShellCommandRunner() {
    return shellCommandRunner != null ? shellCommandRunner : NO_OP_SHELL_COMMAND_RUNNER;
  }

  private FileSink getResolvedFileSink() {
    return fileSink != null ? fileSink : NO_OP_FILE_SINK;
  }

  private IntegrationRegistry resolveIntegrationRegistry() {
    return integrationRegistry != null ? integrationRegistry : NoOpIntegrationRegistry.INSTANCE;
  }

  private List<CommandHandler<? extends LlmCommand>> determineCommandHandlers(
      EventRecorder eventRecorder, FileSink resolvedFileSink, ShellCommandRunner resolvedShell,
      Clock resolvedClock, IntegrationRegistry resolvedRegistry) {
    return List.of(new CallEndpointCommandHandler(eventRecorder, resolvedRegistry),
        new CompleteCommandHandler(eventRecorder),
        new ContinueCommandHandler(),
        new CreateFileCommandHandler(eventRecorder, resolvedFileSink),
        new EscalateCommandHandler(eventRecorder, resolvedClock),
        new GeneralQuestionCommandHandler(eventRecorder, resolvedClock),
        new RunCommandHandler(eventRecorder, resolvedShell),
        new SetContextCommandHandler(eventRecorder),
        new UserPromptCommandHandler(eventRecorder, resolvedClock));
  }

  private StepExecutor buildStepExecutor(AgentInvoker agentInvoker,
      CommandApplier commandApplier,
      EventRecorder eventRecorder,
      Clock resolvedClock,
      WorkflowExecutor workflowExecutor,
      BranchBehaviourHandler branchBehaviourHandler,
      RetryPreviousBehaviourHandler retryPreviousBehaviourHandler) {
    List<BehaviourHandler<? extends StepBehaviour>> handlers = List.of(
        new AgentBehaviourHandler(agentInvoker, commandApplier, eventRecorder),
        new SparBehaviourHandler(agentInvoker, commandApplier, eventRecorder),
        new WorkflowBehaviourHandler(workflowRepository, workflowExecutor),
        new InputBehaviourHandler(eventRecorder, resolvedClock),
        new ResourceBehaviourHandler(new SafeClasspathResourceResolver()),
        branchBehaviourHandler,
        new FailBehaviourHandler(),
        retryPreviousBehaviourHandler);
    return new StepExecutor(handlers, eventRecorder, resolvedClock);
  }

  private LoopEvaluator resolveLoopEvaluator(AgentInvoker agentInvoker) {
    return loopEvaluator != null ? loopEvaluator : new DefaultLoopEvaluator(agentInvoker);
  }

  private ObjectMapper resolveObjectMapper() {
    return objectMapper != null ? objectMapper : new ObjectMapper();
  }

  private Clock resolveClock() {
    return clock != null ? clock : Clock.systemUTC();
  }

  private void validateRequired() {
    Validate.notNull(workflowRepository, "workflowRepository is required");
    Validate.notNull(agentRepository, "agentRepository is required");
    Validate.notNull(workflowStateRepository, "workflowStateRepository is required");
    Validate.notNull(workflowEventLog, "workflowEventLog is required");
    Validate.notNull(llmClientResolver, "llmClientResolver is required");
    Validate.notNull(fileSink, "fileSink is required");
    Validate.notNull(shellCommandRunner, "shellCommandRunner is required");
    Validate.notNull(integrationRegistry, "integrationRegistry is required");
    Validate.notNull(runContextManager, "runContextManager is required");
  }
}
