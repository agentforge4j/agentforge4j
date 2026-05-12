package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;

public final class ContinueCommandHandler implements CommandHandler<ContinueCommand> {

  private static final System.Logger LOG = System.getLogger(ContinueCommandHandler.class.getName());

  @Override
  public Class<ContinueCommand> getCommandClass() {
    return ContinueCommand.class;
  }

  @Override
  public CommandApplicationResult apply(ContinueCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "Applying ContinueCommand");

    return CommandApplicationResult.CONTINUE;
  }
}
