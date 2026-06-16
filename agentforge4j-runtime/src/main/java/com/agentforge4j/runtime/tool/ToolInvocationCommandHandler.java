// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.util.Validate;
import java.time.Clock;

/**
 * Dispatches a {@link ToolInvocationCommand} through the {@link ToolExecutionService} chokepoint.
 * On an allowed, executed call it writes the result into context via the shared
 * {@link ToolResultApplier} and continues; on approval-pending it suspends the run in
 * {@link WorkflowStatus#AWAITING_TOOL_APPROVAL}; on a policy denial or a failure (after the
 * service's retries) it suspends in {@link WorkflowStatus#AWAITING_TOOL_DECISION} for an operator
 * continue/retry decision (the service has already persisted the pending row and audited the
 * outcome).
 */
public final class ToolInvocationCommandHandler implements CommandHandler<ToolInvocationCommand> {

  private static final System.Logger LOG = System.getLogger(
      ToolInvocationCommandHandler.class.getName());

  private final ToolExecutionService toolExecutionService;
  private final ToolResultApplier toolResultApplier;
  private final Clock clock;

  /**
   * Creates the handler over the execution chokepoint.
   *
   * @param toolExecutionService the execution chokepoint
   * @param toolResultApplier    shared result-to-state routine
   * @param clock                clock for the approval-suspend timestamp
   */
  public ToolInvocationCommandHandler(ToolExecutionService toolExecutionService,
      ToolResultApplier toolResultApplier, Clock clock) {
    this.toolExecutionService =
        Validate.notNull(toolExecutionService, "toolExecutionService must not be null");
    this.toolResultApplier = Validate.notNull(toolResultApplier,
        "toolResultApplier must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  @Override
  public Class<ToolInvocationCommand> getCommandClass() {
    return ToolInvocationCommand.class;
  }

  @Override
  public CommandApplicationResult apply(ToolInvocationCommand cmd,
      CommandApplicationRequest request) {
    WorkflowState state = request.state();
    ToolInvocationContext ctx = new ToolInvocationContext(
        state.getRunId(),
        String.valueOf(request.currentStepUid()),
        request.agentId(),
        new ToolScope(state.getWorkflowId(), state.getRunId()));

    ToolExecutionOutcome outcome = toolExecutionService.execute(cmd, ctx);
    LOG.log(System.Logger.Level.DEBUG, "Tool invocation outcome capability={0}, status={1}",
        cmd.capability(), outcome.status());

    return switch (outcome.status()) {
      case EXECUTED -> {
        toolResultApplier.apply(cmd.capability(), outcome.result(), state, request.agentId());
        yield CommandApplicationResult.CONTINUE;
      }
      case APPROVAL_PENDING -> {
        state.setStatus(WorkflowStatus.AWAITING_TOOL_APPROVAL);
        state.setLastUpdatedAt(clock.instant());
        yield CommandApplicationResult.AWAITING_TOOL_APPROVAL;
      }
      // DENIED or FAILED (after retries): the service has persisted a pending row; suspend for an
      // operator continue/retry decision rather than silently advancing.
      case DENIED, FAILED -> {
        state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
        state.setLastUpdatedAt(clock.instant());
        yield CommandApplicationResult.AWAITING_TOOL_DECISION;
      }
    };
  }
}
