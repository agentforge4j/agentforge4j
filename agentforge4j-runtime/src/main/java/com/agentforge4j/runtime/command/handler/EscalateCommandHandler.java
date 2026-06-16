// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.EscalateCommand;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import java.time.Clock;

/**
 * Handles {@link EscalateCommand} by moving the run to
 * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_APPROVAL} and emitting an
 * approval event.
 */
public final class EscalateCommandHandler implements CommandHandler<EscalateCommand> {

  private static final System.Logger LOG = System.getLogger(EscalateCommandHandler.class.getName());

  private final EventRecorder eventRecorder;
  private final Clock clock;

  /**
   * Creates a handler.
   *
   * @param eventRecorder event sink for approval transition
   * @param clock         wall clock for {@code lastUpdatedAt} when entering approval
   */
  public EscalateCommandHandler(EventRecorder eventRecorder, Clock clock) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  @Override
  public Class<EscalateCommand> getCommandClass() {
    return EscalateCommand.class;
  }

  /** {@inheritDoc} */
  @Override
  public CommandApplicationResult apply(EscalateCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "Applying EscalateCommand with reason: %s", cmd.reason());

    request.state().setStatus(WorkflowStatus.AWAITING_APPROVAL);
    request.state().setLastUpdatedAt(clock.instant());
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.AWAITING_APPROVAL, cmd.reason(), request.agentId());
    return CommandApplicationResult.AWAITING_APPROVAL;
  }
}
