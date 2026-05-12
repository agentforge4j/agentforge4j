package com.agentforge4j.runtime.command;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CommandApplier {

  private static final System.Logger LOG = System.getLogger(CommandApplier.class.getName());

  private final Map<Class<? extends LlmCommand>, CommandHandler<? extends LlmCommand>> commandAppliers;

  public CommandApplier(List<CommandHandler<? extends LlmCommand>> commandHandlers) {
    commandAppliers = indexCommands(
        Validate.notEmpty(commandHandlers, "commandHandlers must not be empty"));
  }

  public CommandApplicationResult apply(List<LlmCommand> commands,
      WorkflowState state,
      ContextMapping contextMapping,
      String agentId,
      int currentStepUid) {
    Validate.notNull(commands, "commands must not be null");
    Validate.notNull(state, "state must not be null");
    Validate.notNull(contextMapping, "contextMapping must not be null");
    Validate.notNull(agentId, "agentId must not be null");

    CommandApplicationRequest request = new CommandApplicationRequest(state, contextMapping,
        agentId, currentStepUid);
    for (LlmCommand command : commands) {
      CommandApplicationResult result = applyOne(command, request);
      if (result != CommandApplicationResult.CONTINUE) {
        return result;
      }
    }
    return CommandApplicationResult.CONTINUE;
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
