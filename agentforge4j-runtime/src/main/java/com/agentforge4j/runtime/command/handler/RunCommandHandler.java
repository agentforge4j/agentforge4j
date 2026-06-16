// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.RunCommandCommand;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import org.apache.commons.lang3.StringUtils;

/**
 * Handles {@link RunCommandCommand} by delegating to {@link ShellCommandRunner} and storing captured
 * stdout on the current step state.
 */
public final class RunCommandHandler implements CommandHandler<RunCommandCommand> {

  private static final System.Logger LOG = System.getLogger(RunCommandHandler.class.getName());

  private final EventRecorder eventRecorder;
  private final ShellCommandRunner shellCommandRunner;

  /**
   * Creates a handler.
   *
   * @param eventRecorder        event sink for command side effects
   * @param shellCommandRunner   delegate used for command execution (often non-no-op in production)
   */
  public RunCommandHandler(EventRecorder eventRecorder, ShellCommandRunner shellCommandRunner) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder can't be null");
    this.shellCommandRunner = Validate.notNull(shellCommandRunner,
        "shellCommandRunner can't be null");
  }

  @Override
  public Class<RunCommandCommand> getCommandClass() {
    return RunCommandCommand.class;
  }

  /** {@inheritDoc} */
  @Override
  public CommandApplicationResult apply(RunCommandCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "RunCommand command issued");
    String stdout = shellCommandRunner.run(request.state().getRunId(), cmd.command());
    request.state().putStepOutput(request.state().getCurrentStepId() + ".stdout",
        StringUtils.defaultString(stdout));
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED, "ran command: %s".formatted(cmd.command()),
        request.agentId());
    return CommandApplicationResult.CONTINUE;
  }
}
