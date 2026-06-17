// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandApplier;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.CommandApplicationResults;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.UserPromptPauseGuard;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.util.Validate;

/**
 * Handles an {@link AgentBehaviour}: invokes the configured agent, applies its commands, and maps the application
 * result to an {@link ExecutionOutcome}.
 *
 * <p>Retries driven by {@code RetryPolicy} are orchestrated by the runtime, not
 * by this handler — a single invocation per handler call keeps the logic simple and makes the retry decision observable
 * in the event log.
 */
public final class AgentBehaviourHandler implements BehaviourHandler<AgentBehaviour> {

  private static final System.Logger LOG = System.getLogger(AgentBehaviourHandler.class.getName());

  private final AgentInvoker agentInvoker;
  private final CommandApplier commandApplier;
  private final EventRecorder eventRecorder;

  public AgentBehaviourHandler(AgentInvoker agentInvoker,
      CommandApplier commandApplier,
      EventRecorder eventRecorder) {
    this.agentInvoker = Validate.notNull(agentInvoker, "agentInvoker must not be null");
    this.commandApplier = Validate.notNull(commandApplier, "commandApplier must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  @Override
  public Class<AgentBehaviour> behaviourType() {
    return AgentBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      AgentBehaviour behaviour,
      ExecutionContext executionContext) {

    AgentInvocationResult result = invokeAgent(step, behaviour, executionContext);
    CommandApplicationResult applicationResult = applyCommand(step, behaviour, executionContext,
        result);

    if (applicationResult != CommandApplicationResult.AWAITING_INPUT
        && applicationResult != CommandApplicationResult.AWAITING_APPROVAL
        && applicationResult != CommandApplicationResult.AWAITING_TOOL_APPROVAL
        && applicationResult != CommandApplicationResult.AWAITING_TOOL_DECISION) {
      executionContext.getState().putStepOutput(step.stepId(), result.rawResponse());
    }

    // Surface a COMPLETE command to an enclosing AGENT_SIGNAL loop without altering the execution
    // outcome (which stays COMPLETED so step gating and sequence continuation are unaffected).
    executionContext.setAgentCompletionSignalled(applicationResult == CommandApplicationResult.COMPLETE_SIGNAL);

    return CommandApplicationResults.toExecutionOutcome(applicationResult);
  }

  private CommandApplicationResult applyCommand(StepDefinition step,
      AgentBehaviour behaviour, ExecutionContext executionContext, AgentInvocationResult result) {
    Integer currentStepUid = executionContext.getState().getStepExecutionUid()
        .get(executionContext.getState().getCurrentStepId());

    UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder,
        step,
        executionContext.getState(),
        result.commands());

    CommandApplicationResult applicationResult = commandApplier.apply(
        result.commands(),
        executionContext.getState(),
        step.contextMapping(),
        behaviour.agentRef(),
        Validate.notNull(currentStepUid, "currentStepUid must not be null"));

    UserPromptPauseGuard.afterCommandApplication(step, executionContext.getState(),
        applicationResult);

    return applicationResult;
  }

  private AgentInvocationResult invokeAgent(StepDefinition step,
      AgentBehaviour behaviour, ExecutionContext executionContext) {
    LOG.log(System.Logger.Level.DEBUG,
        "Agent behaviour start stepId={0}, agentId={1}, transition=invoke",
        step.stepId(), behaviour.agentRef());
    AgentInvocationResult result = agentInvoker.invoke(
        behaviour.agentRef(),
        step.contextMapping(),
        executionContext.getState(),
        step.stepPrompt(),
        step.modelTier(),
        executionContext.getActiveWorkflowId());
    LOG.log(System.Logger.Level.INFO, "Agent call completed stepId={0}, agentId={1}, commands={2}",
        step.stepId(), behaviour.agentRef(), result.commands().size());
    return result;
  }
}
