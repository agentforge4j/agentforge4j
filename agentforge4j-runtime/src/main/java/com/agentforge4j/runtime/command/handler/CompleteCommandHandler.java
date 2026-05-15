package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import org.apache.commons.lang3.StringUtils;

/**
 * Handles {@link CompleteCommand} by recording loop completion and signalling the enclosing loop.
 */
public final class CompleteCommandHandler implements CommandHandler<CompleteCommand> {

  private static final System.Logger LOG = System.getLogger(CompleteCommandHandler.class.getName());

  private final EventRecorder eventRecorder;

  /**
   * Creates a handler.
   *
   * @param eventRecorder event sink for completion signalling
   * @throws IllegalArgumentException if {@code eventRecorder} is {@code null}
   */
  public CompleteCommandHandler(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder can't be null");
  }

  @Override
  public Class<CompleteCommand> getCommandClass() {
    return CompleteCommand.class;
  }

  /** {@inheritDoc} */
  @Override
  public CommandApplicationResult apply(CompleteCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "Complete command issued");

    String payload = StringUtils.isBlank(cmd.summary())
        ? "agent signalled completion"
        : "agent signalled completion: %s".formatted(cmd.summary());
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.LOOP_ITERATION_COMPLETED, payload, request.agentId());
    return CommandApplicationResult.COMPLETE_SIGNAL;
  }
}
