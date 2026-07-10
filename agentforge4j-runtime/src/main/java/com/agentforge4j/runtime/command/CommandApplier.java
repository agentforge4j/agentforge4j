// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispatches each {@link LlmCommand} to its registered {@link CommandHandler} and returns the first
 * non-{@link CommandApplicationResult#CONTINUE} result, or {@link CommandApplicationResult#CONTINUE}
 * when every command continues.
 */
public final class CommandApplier {

  private static final System.Logger LOG = System.getLogger(CommandApplier.class.getName());

  private final Map<Class<? extends LlmCommand>, CommandHandler<? extends LlmCommand>> commandAppliers;

  /**
   * Indexes handlers by {@link CommandHandler#getCommandClass()}; duplicate command classes fail
   * construction.
   *
   * @param commandHandlers one handler per distinct command class, non-empty
   * @throws IllegalArgumentException if {@code commandHandlers} is empty
   * @throws IllegalStateException    if two handlers declare the same command class
   */
  public CommandApplier(List<CommandHandler<? extends LlmCommand>> commandHandlers) {
    commandAppliers = indexCommands(
        Validate.notEmpty(commandHandlers, "commandHandlers must not be empty"));
  }

  /**
   * Applies commands in list order until one yields a non-continue outcome or the list ends.
   *
   * @param commands         parsed commands in application order
   * @param state            workflow state updated by handlers
   * @param contextMapping   allowed output keys for {@code agentId}
   * @param agentId          agent id recorded on events and permission checks
   * @param currentStepUid   step instance id used when recording context writes
   * @param step             the step whose commands are being applied
   * @param enclosingWorkflow the workflow definition enclosing {@code step}
   * @return aggregated control-flow result for the batch
   * @throws IllegalArgumentException if any argument is {@code null} or no handler is registered
   *                                  for a command's concrete class
   */
  public CommandApplicationResult apply(List<LlmCommand> commands,
      WorkflowState state,
      ContextMapping contextMapping,
      String agentId,
      int currentStepUid,
      StepDefinition step,
      WorkflowDefinition enclosingWorkflow) {
    Validate.notNull(commands, "commands must not be null");
    Validate.notNull(state, "state must not be null");
    Validate.notNull(contextMapping, "contextMapping must not be null");
    Validate.notNull(agentId, "agentId must not be null");
    Validate.notNull(step, "step must not be null");
    Validate.notNull(enclosingWorkflow, "enclosingWorkflow must not be null");

    // Starts from the count already persisted for this step execution uid, not zero, so
    // maxExpansions bounds the total requested expansions across every command-application batch
    // belonging to the same step invocation (a pause/resume or retry that reuses the same uid), not
    // just this batch. A genuinely new step invocation gets a new uid (see
    // ExecutionContext#allocateStepSequenceUid), so its count naturally starts at zero.
    int requestContextExpansions = readPersistedExpansionCount(state, currentStepUid);
    for (LlmCommand command : commands) {
      // The prior-expansion count is meaningful only on a RequestContextCommand; every other
      // command type carries 0, as CommandApplicationRequest documents. Counting SELECTORS (not
      // commands) makes maxExpansions bound the total requested expansions in the batch — packing
      // many selectors into one command must not evade the limit.
      int priorExpansions = 0;
      if (command instanceof RequestContextCommand requestContext) {
        priorExpansions = requestContextExpansions;
        requestContextExpansions += requestContext.requestedSelectors().size();
        writePersistedExpansionCount(state, currentStepUid, requestContextExpansions);
      }
      CommandApplicationRequest request = new CommandApplicationRequest(state, contextMapping,
          agentId, currentStepUid, step, enclosingWorkflow, priorExpansions);
      CommandApplicationResult result = applyOne(command, request);
      if (result != CommandApplicationResult.CONTINUE) {
        return result;
      }
    }
    return CommandApplicationResult.CONTINUE;
  }

  private static int readPersistedExpansionCount(WorkflowState state, int stepExecutionUid) {
    return state.getContextValue(ReservedContextKeys.expansionCountKey(stepExecutionUid))
        .map(CommandApplier::expansionCountContentOf)
        .map(Integer::parseInt)
        .orElse(0);
  }

  private static void writePersistedExpansionCount(WorkflowState state, int stepExecutionUid,
      int count) {
    state.putContextValue(ReservedContextKeys.expansionCountKey(stepExecutionUid),
        new StringContextValue(Integer.toString(count), ContextProvenance.SYSTEM_GENERATED));
  }

  private static String expansionCountContentOf(ContextValue value) {
    if (value instanceof StringContextValue text) {
      return text.value();
    }
    throw new IllegalStateException(
        "Persisted expansion-count key holds an unexpected value type: %s"
            .formatted(value.getClass()));
  }

  private CommandApplicationResult applyOne(LlmCommand command, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "Applying command commandType={0}, stepId={1}",
        command.getClass().getSimpleName(), request.state().getCurrentStepId());

    return lookupHandler(command).apply(command, request);
  }

  @SuppressWarnings("unchecked")
  private CommandHandler<LlmCommand> lookupHandler(LlmCommand command) {
    return (CommandHandler<LlmCommand>) Validate.notNull(commandAppliers.get(command.getClass()),
        "No CommandHandler registered for command type: %s".formatted(
            command.getClass().getName()));
  }

  private Map<Class<? extends LlmCommand>, CommandHandler<? extends LlmCommand>> indexCommands(
      List<CommandHandler<? extends LlmCommand>> commandHandlers) {
    return commandHandlers.stream().collect(Collectors.toUnmodifiableMap(
        CommandHandler::getCommandClass,
        handler -> handler,
        (h1, h2) -> {
          throw new IllegalStateException(
              "Duplicate CommandHandler for command type: %s (%s vs %s)"
                  .formatted(h1.getCommandClass().getName(),
                      h1.getClass().getName(), h2.getClass().getName()));
        }
    ));
  }
}
