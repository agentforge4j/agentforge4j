// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the {@link GateResponse} sealed hierarchy: every static factory maps to the
 * right variant with the right components, and the validating compact constructors reject blank ids
 * / null answer maps.
 */
class GateResponseTest {

  @Test
  void inputFactoryDefensivelyCopiesAnswers() {
    Map<String, String> source = new java.util.HashMap<>();
    source.put("item-1", "answer");

    GateResponse response = GateResponse.input(source);

    assertThat(response).isInstanceOf(GateResponse.Input.class);
    GateResponse.Input input = (GateResponse.Input) response;
    assertThat(input.answers()).containsEntry("item-1", "answer");

    source.put("item-2", "mutated");
    assertThat(input.answers()).doesNotContainKey("item-2");
  }

  @Test
  void inputRejectsNullAnswers() {
    assertThatThrownBy(() -> GateResponse.input(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reviewFactoryCarriesNote() {
    GateResponse response = GateResponse.review("looks good");

    assertThat(response).isInstanceOf(GateResponse.Review.class);
    assertThat(((GateResponse.Review) response).note()).isEqualTo("looks good");
  }

  @Test
  void approveStepIsApprovingStepApproval() {
    GateResponse response = GateResponse.approveStep("ship it");

    assertThat(response).isInstanceOf(GateResponse.StepApproval.class);
    GateResponse.StepApproval approval = (GateResponse.StepApproval) response;
    assertThat(approval.approve()).isTrue();
    assertThat(approval.note()).isEqualTo("ship it");
  }

  @Test
  void rejectStepIsRejectingStepApproval() {
    GateResponse response = GateResponse.rejectStep("needs work");

    GateResponse.StepApproval approval = (GateResponse.StepApproval) response;
    assertThat(approval.approve()).isFalse();
    assertThat(approval.note()).isEqualTo("needs work");
  }

  @Test
  void escalationFactoryCarriesNote() {
    GateResponse response = GateResponse.escalationApproval("approved by lead");

    assertThat(response).isInstanceOf(GateResponse.Escalation.class);
    assertThat(((GateResponse.Escalation) response).note()).isEqualTo("approved by lead");
  }

  @Test
  void toolApproveIsApprovingToolApproval() {
    GateResponse response = GateResponse.toolApprove("inv-1");

    assertThat(response).isInstanceOf(GateResponse.ToolApproval.class);
    GateResponse.ToolApproval approval = (GateResponse.ToolApproval) response;
    assertThat(approval.toolInvocationId()).isEqualTo("inv-1");
    assertThat(approval.approve()).isTrue();
    assertThat(approval.reason()).isEmpty();
  }

  @Test
  void toolRejectIsRejectingToolApproval() {
    GateResponse response = GateResponse.toolReject("inv-2", "untrusted");

    GateResponse.ToolApproval approval = (GateResponse.ToolApproval) response;
    assertThat(approval.approve()).isFalse();
    assertThat(approval.reason()).isEqualTo("untrusted");
  }

  @Test
  void toolApprovalRejectsBlankInvocationId() {
    assertThatThrownBy(() -> GateResponse.toolApprove("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toolContinueIsNonRetryingToolDecision() {
    GateResponse response = GateResponse.toolContinue("inv-3");

    assertThat(response).isInstanceOf(GateResponse.ToolDecision.class);
    GateResponse.ToolDecision decision = (GateResponse.ToolDecision) response;
    assertThat(decision.toolInvocationId()).isEqualTo("inv-3");
    assertThat(decision.retry()).isFalse();
  }

  @Test
  void toolRetryIsRetryingToolDecision() {
    GateResponse.ToolDecision decision = (GateResponse.ToolDecision) GateResponse.toolRetry("inv-4");

    assertThat(decision.retry()).isTrue();
  }

  @Test
  void toolDecisionRejectsBlankInvocationId() {
    assertThatThrownBy(() -> GateResponse.toolContinue(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
