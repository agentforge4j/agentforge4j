package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.SetContextCommand;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;

/**
 * Handles {@link SetContextCommand} by writing a context value when the key passes output-key policy.
 */
public final class SetContextCommandHandler implements CommandHandler<SetContextCommand> {

  private static final System.Logger LOG = System.getLogger(
      SetContextCommandHandler.class.getName());

  private final EventRecorder eventRecorder;

  /**
   * Creates a handler.
   *
   * @param eventRecorder event sink for context updates
   */
  public SetContextCommandHandler(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  @Override
  public Class<SetContextCommand> getCommandClass() {
    return SetContextCommand.class;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@link CommandHandler#ensureContextOutputKeyAllowed(String, com.agentforge4j.core.workflow.context.ContextMapping, String)} rejects the key
   */
  @Override
  public CommandApplicationResult apply(SetContextCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "SetContext command key={0}", cmd.key());
    CommandHandler.ensureContextOutputKeyAllowed(cmd.key(), request.contextMapping(), request.agentId());
    request.state().putContextValue(cmd.key(), cmd.value());
    request.state().putContextKeyWrittenAtUid(cmd.key(), request.currentStepUid());
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED, "set context key: %s".formatted(cmd.key()),
        request.agentId());
    return CommandApplicationResult.CONTINUE;
  }
}
