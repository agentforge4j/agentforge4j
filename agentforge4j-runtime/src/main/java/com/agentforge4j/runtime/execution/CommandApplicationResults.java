// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.runtime.command.CommandApplicationResult;

/**
 * Maps {@link CommandApplicationResult} to {@link ExecutionOutcome} for behaviour handlers.
 *
 * <p>Kept separate from the exported {@code com.agentforge4j.runtime.command} package so the
 * public command API does not reference non-exported execution types.
 */
public final class CommandApplicationResults {

  private CommandApplicationResults() {
  }

  public static ExecutionOutcome toExecutionOutcome(CommandApplicationResult result) {
    return switch (result) {
      case CONTINUE, COMPLETE_SIGNAL -> ExecutionOutcome.COMPLETED;
      case AWAITING_INPUT, AWAITING_APPROVAL, AWAITING_TOOL_APPROVAL, AWAITING_TOOL_DECISION ->
          ExecutionOutcome.PAUSED;
    };
  }
}
