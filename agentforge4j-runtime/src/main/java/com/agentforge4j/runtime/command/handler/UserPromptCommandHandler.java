package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.UserPromptCommand;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.UUID;

public class UserPromptCommandHandler implements CommandHandler<UserPromptCommand> {

  private static final System.Logger LOG = System.getLogger(
      UserPromptCommandHandler.class.getName());
  public static final String USER_MESSAGE_CONTEXT_PREFIX = "user.message.";

  private final EventRecorder eventRecorder;
  private final Clock clock;

  public UserPromptCommandHandler(EventRecorder eventRecorder, Clock clock) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder is null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  @Override
  public Class<UserPromptCommand> getCommandClass() {
    return UserPromptCommand.class;
  }

  @Override
  public CommandApplicationResult apply(UserPromptCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG,
        "Applying UserPromptCommand message='{0}', responseRequired={1}",
        cmd.message(), cmd.responseRequired());
    if (cmd.responseRequired()) {
      request.state().setPendingUserPrompt(cmd.message());
      request.state().setPendingArtifact(null);
      request.state().setStatus(WorkflowStatus.AWAITING_INPUT);
      request.state().setLastUpdatedAt(clock.instant());

      eventRecorder.record(
          request.state().getRunId(),
          request.state().getCurrentStepId(),
          WorkflowEventType.AWAITING_INPUT,
          cmd.message(),
          request.agentId()
      );
      return CommandApplicationResult.AWAITING_INPUT;
    }
    String key = USER_MESSAGE_CONTEXT_PREFIX + UUID.randomUUID();
    request.state().putContextValue(key, new StringContextValue(cmd.message()));
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED, "user-visible message: %s".formatted(cmd.message()),
        request.agentId());
    return CommandApplicationResult.CONTINUE;
  }
}
