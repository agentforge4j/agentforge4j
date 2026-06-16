// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.runtime.execution.behaviour.spar.SparContinuationEvaluator;
import com.agentforge4j.runtime.execution.behaviour.spar.SparLoopTerminationReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class SparContinuationEvaluatorTest {

  private static final String CONCRETE_REASON =
      "The challenger assumes synchronous I/O but the design uses async boundaries; evidence is missing.";

  @Test
  void last_continue_prefers_last_in_batch() {
    ContinueCommand first = new ContinueCommand(true, "x", null);
    ContinueCommand second = new ContinueCommand(false, null, null);
    assertThat(SparContinuationEvaluator.lastContinueCommand(
        List.of(first, new CompleteCommand("x"), second)))
        .isSameAs(second);
  }

  @Test
  void no_continue_command_means_no_valid_continuation() {
    assertThat(SparContinuationEvaluator.hasValidContinuationRequest(
        List.of(new CompleteCommand("done")))).isFalse();
  }

  @Test
  void wants_false_is_not_valid_continuation() {
    assertThat(SparContinuationEvaluator.hasValidContinuationRequest(
        List.of(new ContinueCommand(false, CONCRETE_REASON, null)))).isFalse();
  }

  @Test
  void wants_true_with_concrete_reason_is_valid() {
    assertThat(SparContinuationEvaluator.hasValidContinuationRequest(
        List.of(new ContinueCommand(true, CONCRETE_REASON, null)))).isTrue();
  }

  @Test
  void wants_true_with_short_reason_is_invalid() {
    assertThat(SparContinuationEvaluator.hasValidContinuationRequest(
        List.of(new ContinueCommand(true, "too short", null)))).isFalse();
  }

  @Test
  void wants_true_with_vague_phrase_is_invalid() {
    assertThat(SparContinuationEvaluator.hasValidContinuationRequest(
        List.of(new ContinueCommand(true, "I disagree with the conclusion stated above", null))))
        .isFalse();
    assertThat(SparContinuationEvaluator.hasValidContinuationRequest(
        List.of(new ContinueCommand(true, "This needs more discussion before we can agree", null))))
        .isFalse();
  }

  @Test
  void classify_both_not_wanting_another_round() {
    assertThat(SparContinuationEvaluator.classifyEarlyStop(
        List.of(new ContinueCommand(false, null, null)),
        List.of(new ContinueCommand(null, null, null))))
        .isEqualTo(SparLoopTerminationReason.EARLY_STOP_BOTH_DONE);
  }

  @Test
  void classify_when_one_side_still_wants_but_invalid_reason() {
    assertThat(SparContinuationEvaluator.classifyEarlyStop(
        List.of(new ContinueCommand(true, "I disagree with everything", null)),
        List.of(new ContinueCommand(false, null, null))))
        .isEqualTo(SparLoopTerminationReason.EARLY_STOP_NO_VALID_CONTINUATION);
  }
}
