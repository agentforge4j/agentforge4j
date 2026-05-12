package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.CreateFileCommand;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;

public final class CreateFileCommandHandler implements CommandHandler<CreateFileCommand> {

  private final EventRecorder eventRecorder;
  private final FileSink fileSink;

  private static final System.Logger LOG = System.getLogger(
      CreateFileCommandHandler.class.getName());

  public CreateFileCommandHandler(EventRecorder eventRecorder, FileSink fileSink) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.fileSink = Validate.notNull(fileSink, "fileSink must not be null");
  }

  @Override
  public Class<CreateFileCommand> getCommandClass() {
    return CreateFileCommand.class;
  }

  @Override
  public CommandApplicationResult apply(CreateFileCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "CreateFile command path={0}", cmd.path());

    fileSink.write(request.state().getRunId(),
        request.state().getCurrentStepId(),
        cmd.path(),
        cmd.content());
    eventRecorder.record(request.state().getRunId(),
        request.state().getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED,
        "created file: %s".formatted(cmd.path()),
        request.agentId());
    return CommandApplicationResult.CONTINUE;
  }
}
