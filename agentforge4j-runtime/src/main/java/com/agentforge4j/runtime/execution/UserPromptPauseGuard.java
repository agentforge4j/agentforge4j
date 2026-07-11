// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.CreateFileCommand;
import com.agentforge4j.core.command.EscalateCommand;
import com.agentforge4j.core.command.GenerateQuestionsCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.command.RunCommandCommand;
import com.agentforge4j.core.command.SetContextCommand;
import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.command.UserPromptCommand;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.exception.UserPromptLimitExceededException;
import java.util.List;
import java.util.Map;

/**
 * Enforces {@link StepDefinition#maxUserPromptRounds()} for blocking {@link UserPromptCommand}s.
 */
public final class UserPromptPauseGuard {

  public static final int DEFAULT_MAX_USER_PROMPT_ROUNDS = 8;

  private static final Map<Class<? extends LlmCommand>, Boolean> stopBeforeLaterCommands = indexCommands();

  private UserPromptPauseGuard() {
  }

  private static Map<Class<? extends LlmCommand>, Boolean> indexCommands() {
    return Map.of(
        UserPromptCommand.class, false,
        ContinueCommand.class, false,
        CreateFileCommand.class, false,
        SetContextCommand.class, false,
        RunCommandCommand.class, false,
        ToolInvocationCommand.class, false,
        RequestContextCommand.class, false,
        CompleteCommand.class, true,
        EscalateCommand.class, true,
        GenerateQuestionsCommand.class, true
    );
  }

  public static int maxRoundsFor(StepDefinition step) {
    Integer configured = step.maxUserPromptRounds();
    return configured != null && configured > 0 ? configured : DEFAULT_MAX_USER_PROMPT_ROUNDS;
  }

  /**
   * If the command batch would apply a blocking user prompt and the pause budget is exhausted,
   * records {@link WorkflowEventType#USER_PROMPT_LIMIT_REACHED} and throws.
   */
  public static void ensureBlockingUserPromptAllowed(
      EventRecorder eventRecorder,
      StepDefinition step,
      WorkflowState state,
      List<LlmCommand> commands) {
    if (!wouldApplyBlockingUserPrompt(commands, step.stepId())) {
      return;
    }
    int max = maxRoundsFor(step);
    int count = state.getUserPromptPauseCountForStep(step.stepId());
    if (count >= max) {
      String payload = "stepId=%s pauseCount=%d maxUserPromptRounds=%d"
          .formatted(step.stepId(), count, max);
      eventRecorder.record(
          state.getRunId(),
          step.stepId(),
          WorkflowEventType.USER_PROMPT_LIMIT_REACHED,
          payload,
          "runtime");
      throw new UserPromptLimitExceededException(step.stepId(), count, max);
    }
  }

  public static void afterCommandApplication(
      StepDefinition step, WorkflowState state, CommandApplicationResult result) {
    if (result == CommandApplicationResult.AWAITING_INPUT && state.getPendingUserPrompt() != null) {
      state.incrementUserPromptPauseCountForStep(step.stepId());
    } else if (result == CommandApplicationResult.CONTINUE
        || result == CommandApplicationResult.COMPLETE_SIGNAL) {
      state.resetUserPromptPauseCountForStep(step.stepId());
    }
  }

  /**
   * Returns whether the batch would apply a blocking user prompt before any terminator.
   *
   * <p>Commands after {@link CompleteCommand}, {@link EscalateCommand}, or
   * {@link GenerateQuestionsCommand} are unreachable. A blocking {@link UserPromptCommand} emitted
   * <em>after</em> one of these is treated as an agent protocol violation and throws
   * {@link IllegalStateException} so the run fails loudly rather than silently ignoring the
   * request.
   */
  private static boolean wouldApplyBlockingUserPrompt(List<LlmCommand> commands, String stepId) {
    for (int i = 0; i < commands.size(); i++) {
      LlmCommand command = commands.get(i);
      if (command instanceof UserPromptCommand up && up.responseRequired()) {
        return true;
      } else if (stopsBeforeLaterCommands(command)) {
        assertNoUnreachableBlockingPromptAfter(commands, i + 1, command, stepId);
        return false;
      }
    }
    return false;
  }

  private static void assertNoUnreachableBlockingPromptAfter(List<LlmCommand> commands,
      int fromIndex,
      LlmCommand terminator,
      String stepId) {
    for (int j = fromIndex; j < commands.size(); j++) {
      LlmCommand later = commands.get(j);
      if (later instanceof UserPromptCommand up && up.responseRequired()) {
        throw new IllegalStateException(
            "Agent protocol violation: blocking UserPromptCommand at index %d emitted after terminator %s; prompt is unreachable. stepId=%s"
                .formatted(j, terminatorName(terminator), stepId));
      }
    }
  }

  private static String terminatorName(LlmCommand terminator) {
    if (terminator instanceof CompleteCommand) {
      return "COMPLETE";
    }
    if (terminator instanceof EscalateCommand) {
      return "ESCALATE";
    }
    if (terminator instanceof GenerateQuestionsCommand) {
      return "GENERATE_QUESTIONS";
    }
    throw new IllegalStateException("Unhandled terminator: " + terminator.getClass());
  }

  private static boolean stopsBeforeLaterCommands(LlmCommand command) {
    if (stopBeforeLaterCommands.containsKey(command.getClass())) {
      return stopBeforeLaterCommands.get(command.getClass());
    }

    throw new IllegalStateException("Unhandled LlmCommand: " + command.getClass());
  }
}
