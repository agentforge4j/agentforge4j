// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.spar;

import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.LlmCommand;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Interprets {@link ContinueCommand} batches from SPAR participants to decide whether another
 * exchange round is warranted.
 */
public final class SparContinuationEvaluator {

  /**
   * Minimum trimmed length for a continuation {@code reason} to count as substantive.
   */
  static final int MIN_REASON_LENGTH = 12;

  private static final List<String> VAGUE_OR_DISALLOWED_PHRASES = List.of(
      "i disagree",
      "i do not agree",
      "needs more discussion",
      "more discussion",
      "no reason",
      "n/a",
      "none",
      "vague preference",
      "vague",
      "just because",
      "keep discussing",
      "continue debating",
      "be adversarial");

  private SparContinuationEvaluator() {
  }

  /**
   * {@code true} when the participant asks for another round with a reason that passes
   * meaningfulness checks.
   */
  public static boolean hasValidContinuationRequest(List<LlmCommand> commands) {
    ContinueCommand cmd = lastContinueCommand(commands);
    return cmd != null && hasValidContinuationRequest(cmd);
  }

  /**
   * Returns the last {@link ContinueCommand} in the batch, or {@code null} if none.
   */
  public static ContinueCommand lastContinueCommand(List<LlmCommand> commands) {
    ContinueCommand last = null;
    for (LlmCommand command : commands) {
      if (command instanceof ContinueCommand cc) {
        last = cc;
      }
    }
    return last;
  }

  static boolean hasValidContinuationRequest(ContinueCommand cmd) {
    Objects.requireNonNull(cmd, "cmd");
    if (!Boolean.TRUE.equals(cmd.wantsAnotherRound())) {
      return false;
    }
    return isMeaningfulContinuationReason(cmd.reason());
  }

  static boolean isMeaningfulContinuationReason(String reason) {
    if (reason == null) {
      return false;
    }
    String trimmed = reason.strip();
    if (trimmed.length() < MIN_REASON_LENGTH) {
      return false;
    }
    String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    for (String phrase : VAGUE_OR_DISALLOWED_PHRASES) {
      if (normalized.contains(phrase)) {
        return false;
      }
    }
    return true;
  }

  /**
   * When neither side produced a valid continuation request, classifies why the SPAR loop ended
   * early for logging.
   */
  public static SparLoopTerminationReason classifyEarlyStop(
      List<LlmCommand> primaryCommands, List<LlmCommand> challengerCommands) {
    ContinueCommand p = lastContinueCommand(primaryCommands);
    ContinueCommand c = lastContinueCommand(challengerCommands);
    boolean primaryWants = p != null && Boolean.TRUE.equals(p.wantsAnotherRound());
    boolean challengerWants = c != null && Boolean.TRUE.equals(c.wantsAnotherRound());
    if (!primaryWants && !challengerWants) {
      return SparLoopTerminationReason.EARLY_STOP_BOTH_DONE;
    }
    return SparLoopTerminationReason.EARLY_STOP_NO_VALID_CONTINUATION;
  }
}
