package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.spar.SparConfig;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandApplier;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.CommandApplicationResults;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.UserPromptPauseGuard;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.spar.SparContinuationEvaluator;
import com.agentforge4j.runtime.execution.behaviour.spar.SparLoopTerminationReason;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Handles a {@link SparBehaviour}: runs primary vs challenger exchanges until neither side has a valid reason to
 * continue or {@code maxRounds} is reached, then a final resolution round where the primary agent is given the
 * resolution prompt.
 *
 * <p>Intermediate round outputs are recorded on the shared context under
 * reserved keys so the next round can see the previous responses. Only the final resolution round's commands are
 * applied via {@link CommandApplier} — earlier rounds contribute their outputs to the shared context only, not side
 * effects.
 */
public final class SparBehaviourHandler implements BehaviourHandler<SparBehaviour> {

  private static final System.Logger LOG = System.getLogger(SparBehaviourHandler.class.getName());

  /**
   * Reserved context key prefix for round-by-round primary responses.
   */
  public static final String SPAR_PRIMARY_PREFIX = "spar.primary.round.";

  /**
   * Reserved context key prefix for round-by-round challenger responses.
   */
  public static final String SPAR_CHALLENGER_PREFIX = "spar.challenger.round.";

  /**
   * Appended to the step prompt for SPAR exchange invocations so models return structured continuation metadata on
   * {@code CONTINUE}.
   */
  static final String SPAR_ROUND_STEP_PROMPT_SUFFIX = """
      SPAR round output (this is not the final resolution call):
      - Include your normal command array as required by your agent contract.
      - On any CONTINUE command you emit for this round, you may add optional SPAR fields:
        wantsAnotherRound (boolean), reason (string), unresolvedConcerns (string array, optional).
      - Set wantsAnotherRound to true only when there is a concrete unresolved issue (for example a
        requirement conflict, missing evidence, trade-off disagreement, security/compliance concern,
        incomplete design decision, or a specific implementation risk). Never continue just to be
        adversarial or to burn tokens.
      - If wantsAnotherRound is true, reason must name that concrete issue in plain language (not
        vague wording such as "needs more discussion" or "I disagree" without substance).
      - When the issue for this round is settled, set wantsAnotherRound to false (or omit it) and do
        not invent artificial disagreement.""".strip();

  private final AgentInvoker agentInvoker;
  private final CommandApplier commandApplier;
  private final EventRecorder eventRecorder;

  public SparBehaviourHandler(AgentInvoker agentInvoker, CommandApplier commandApplier,
      EventRecorder eventRecorder) {
    this.agentInvoker = Validate.notNull(agentInvoker, "agentInvoker must not be null");
    this.commandApplier = Validate.notNull(commandApplier, "commandApplier must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  @Override
  public Class<SparBehaviour> behaviourType() {
    return SparBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step, SparBehaviour behaviour,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    String activeWorkflowId = executionContext.getActiveWorkflowId();
    SparConfig config = behaviour.sparConfig();
    LOG.log(System.Logger.Level.DEBUG,
        "SPAR start stepId={0}, agentId={1}, challengerAgentId={2}, maxRounds={3}",
        step.stepId(), behaviour.agentRef(), config.challengerAgentId(), config.maxRounds());

    String sparRoundPrompt = buildSparRoundPrompt(step.stepPrompt());
    int executedRounds = 0;
    SparLoopTerminationReason loopTermination = SparLoopTerminationReason.MAX_ROUNDS_REACHED;

    for (int round = 1; round <= config.maxRounds(); round++) {
      ContextMapping roundMapping = buildRoundMapping(step.contextMapping(), round - 1);

      AgentInvocationResult primary = spar(behaviour.agentRef(),
          round, roundMapping, state, sparRoundPrompt, SPAR_PRIMARY_PREFIX, step.modelTier(),
          activeWorkflowId);
      AgentInvocationResult challenger = spar(config.challengerAgentId(),
          round, roundMapping, state, sparRoundPrompt, SPAR_CHALLENGER_PREFIX, step.modelTier(),
          activeWorkflowId);

      executedRounds = round;

      if (round < config.maxRounds()) {
        boolean primaryContinue =
            SparContinuationEvaluator.hasValidContinuationRequest(primary.commands());
        boolean challengerContinue =
            SparContinuationEvaluator.hasValidContinuationRequest(challenger.commands());
        if (!primaryContinue && !challengerContinue) {
          loopTermination = classifyEarlyStop(primary, challenger, round);
          break;
        }
      }
    }

    AgentInvocationResult resolution = finalResolutionRound(step, behaviour, state, config,
        executedRounds, activeWorkflowId);
    Integer currentStepUid = state.getStepExecutionUid().get(state.getCurrentStepId());
    UserPromptPauseGuard.ensureBlockingUserPromptAllowed(eventRecorder, step, state,
        resolution.commands());

    CommandApplicationResult result = applyCommands(step, behaviour, resolution, state,
        currentStepUid);
    LOG.log(System.Logger.Level.INFO,
        "SPAR completed stepId={0}, executedRounds={1}, maxRounds={2}, loopTermination={3}",
        step.stepId(), executedRounds, config.maxRounds(), loopTermination);
    return CommandApplicationResults.toExecutionOutcome(result);
  }

  private AgentInvocationResult finalResolutionRound(StepDefinition step,
      SparBehaviour behaviour, WorkflowState state, SparConfig config, int executedRounds,
      String activeWorkflowId) {
    state.putContextValue("spar.resolution.prompt",
        new StringContextValue(config.resolutionPrompt()));

    return agentInvoker.invoke(
        behaviour.agentRef(),
        buildResolutionMapping(step.contextMapping(), executedRounds),
        state,
        step.stepPrompt(),
        step.modelTier(),
        activeWorkflowId);
  }

  private CommandApplicationResult applyCommands(StepDefinition step,
      SparBehaviour behaviour, AgentInvocationResult resolution, WorkflowState state,
      Integer currentStepUid) {
    CommandApplicationResult result = commandApplier.apply(
        resolution.commands(),
        state,
        step.contextMapping(),
        behaviour.agentRef(),
        Validate.notNull(currentStepUid, "currentStepUid must not be null"));

    UserPromptPauseGuard.afterCommandApplication(step, state, result);

    if (result != CommandApplicationResult.AWAITING_INPUT
        && result != CommandApplicationResult.AWAITING_APPROVAL
        && result != CommandApplicationResult.AWAITING_TOOL_APPROVAL
        && result != CommandApplicationResult.AWAITING_TOOL_DECISION) {
      state.putStepOutput(step.stepId(), resolution.rawResponse());
    }
    return result;
  }

  private static SparLoopTerminationReason classifyEarlyStop(
      AgentInvocationResult primary, AgentInvocationResult challenger, int round) {
    SparLoopTerminationReason loopTermination;
    loopTermination = SparContinuationEvaluator.classifyEarlyStop(
        primary.commands(), challenger.commands());
    LOG.log(System.Logger.Level.DEBUG,
        "SPAR early stop after round={0}, reason={1}",
        round, loopTermination);
    return loopTermination;
  }

  private AgentInvocationResult spar(String agentRef, int round, ContextMapping roundMapping,
      WorkflowState state, String sparRoundPrompt, String sparPrefix, String stepModelTier,
      String activeWorkflowId) {
    LOG.log(System.Logger.Level.DEBUG, "SPAR round={0}, responder={1}", round,
        agentRef);
    AgentInvocationResult agent = agentInvoker.invoke(
        agentRef,
        roundMapping,
        state,
        sparRoundPrompt,
        stepModelTier,
        activeWorkflowId);
    state.putContextValue(sparPrefix + round,
        new StringContextValue(agent.rawResponse()));
    return agent;
  }

  private static String buildSparRoundPrompt(String stepPrompt) {
    if (StringUtils.isBlank(stepPrompt)) {
      return SPAR_ROUND_STEP_PROMPT_SUFFIX;
    }
    return stepPrompt.strip()
        + System.lineSeparator()
        + System.lineSeparator()
        + SPAR_ROUND_STEP_PROMPT_SUFFIX;
  }

  /**
   * Widens {@code inputKeys} so exchange invocations for round {@code N} can read prior rounds' outputs (round
   * {@code N} uses keys for rounds {@code 1 .. N-1}). {@code outputKeys} are unchanged.
   *
   * <p>Always returns a new {@link ContextMapping} instance (never the {@code original} reference)
   * so callers cannot accidentally share mutable intent across rounds.
   *
   * @param original       the step's declared context mapping
   * @param previousRounds number of completed SPAR rounds already stored in context (0 for round 1)
   */
  public static ContextMapping buildRoundMapping(ContextMapping original, int previousRounds) {
    Validate.isTrue(previousRounds >= 0, "previousRounds must be non-negative");
    List<String> inputKeys = new ArrayList<>(original.inputKeys());
    for (int r = 1; r <= previousRounds; r++) {
      inputKeys.add(SPAR_PRIMARY_PREFIX + r);
      inputKeys.add(SPAR_CHALLENGER_PREFIX + r);
    }
    return new ContextMapping(inputKeys, original.outputKeys());
  }

  private static ContextMapping buildResolutionMapping(ContextMapping original,
      int executedRounds) {
    // The resolution round needs access to the spar round outputs so it can reason
    // over both sides. We widen inputKeys by adding the reserved keys but leave
    // outputKeys untouched so the original write contract still applies.
    List<String> widenedInputs = new ArrayList<>(original.inputKeys());
    for (int round = 1; round <= executedRounds; round++) {
      widenedInputs.add(SPAR_PRIMARY_PREFIX + round);
      widenedInputs.add(SPAR_CHALLENGER_PREFIX + round);
    }
    widenedInputs.add("spar.resolution.prompt");
    return new ContextMapping(widenedInputs, original.outputKeys());
  }
}
