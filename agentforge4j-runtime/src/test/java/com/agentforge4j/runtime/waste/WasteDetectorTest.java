// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.waste;

import com.agentforge4j.core.spi.governance.TokenGovernanceSignal;
import com.agentforge4j.core.spi.governance.WasteSignalKind;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.llm.api.ModelTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WasteDetectorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static StepDefinition step(String id, ContextSelection selection) {
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop")).withContextSelection(selection).build();
  }

  private static WorkflowDefinition workflow(StepDefinition... steps) {
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(steps),
        List.of(), List.of());
  }

  @Test
  void duplicateInvocationFiresOnMatchingFingerprintsNonRetry() {
    Optional<TokenGovernanceSignal> signal = WasteDetector.evaluateDuplicateInvocation(
        "s1", "agent-1", "ctx-fp", "in-fp", "ctx-fp", "in-fp", false);

    assertThat(signal).isPresent();
    assertThat(signal.get().kind()).isEqualTo(WasteSignalKind.DUPLICATE_INVOCATION);
  }

  @Test
  void duplicateInvocationSkipsOnRetry() {
    assertThat(WasteDetector.evaluateDuplicateInvocation(
        "s1", "agent-1", "ctx-fp", "in-fp", "ctx-fp", "in-fp", true)).isEmpty();
  }

  @Test
  void duplicateInvocationSkipsWhenNoPriorInvocation() {
    assertThat(WasteDetector.evaluateDuplicateInvocation(
        "s1", "agent-1", "ctx-fp", "in-fp", null, null, false)).isEmpty();
  }

  @Test
  void duplicateInvocationSkipsWhenFingerprintsDiffer() {
    assertThat(WasteDetector.evaluateDuplicateInvocation(
        "s1", "agent-1", "ctx-fp", "in-fp", "ctx-fp", "different", false)).isEmpty();
  }

  @Test
  void tierEscalationFiresWhenTierIncreasesWithUnchangedInputs() {
    Optional<TokenGovernanceSignal> signal = WasteDetector.evaluateUnjustifiedTierEscalation(
        "s1", "agent-1", ModelTier.POWERFUL, ModelTier.STANDARD, "ctx-fp", "ctx-fp", "in-fp",
        "in-fp");

    assertThat(signal).isPresent();
    assertThat(signal.get().kind()).isEqualTo(WasteSignalKind.UNJUSTIFIED_TIER_ESCALATION);
  }

  @Test
  void tierEscalationSkipsWhenTierDidNotIncrease() {
    assertThat(WasteDetector.evaluateUnjustifiedTierEscalation(
        "s1", "agent-1", ModelTier.STANDARD, ModelTier.STANDARD, "ctx-fp", "ctx-fp", "in-fp",
        "in-fp")).isEmpty();
  }

  @Test
  void tierEscalationSkipsWhenInputsChanged() {
    assertThat(WasteDetector.evaluateUnjustifiedTierEscalation(
        "s1", "agent-1", ModelTier.POWERFUL, ModelTier.STANDARD, "ctx-fp", "different", "in-fp",
        "in-fp")).isEmpty();
  }

  @Test
  void tierEscalationSkipsWhenNoPriorInvocation() {
    assertThat(WasteDetector.evaluateUnjustifiedTierEscalation(
        "s1", "agent-1", ModelTier.POWERFUL, null, "ctx-fp", null, "in-fp", null)).isEmpty();
  }

  @Test
  void unchangedLoopContextFiresWhenFingerprintMatchesPriorIteration() {
    Optional<TokenGovernanceSignal> signal = WasteDetector.evaluateUnchangedLoopContext(
        "body-step", "bp-1", 2, "ctx-fp", "ctx-fp");

    assertThat(signal).isPresent();
    assertThat(signal.get().kind()).isEqualTo(WasteSignalKind.UNCHANGED_LOOP_CONTEXT);
  }

  @Test
  void unchangedLoopContextSkipsOnFirstIteration() {
    assertThat(WasteDetector.evaluateUnchangedLoopContext(
        "body-step", "bp-1", 1, "ctx-fp", null)).isEmpty();
  }

  @Test
  void unchangedLoopContextSkipsWhenFingerprintDiffers() {
    assertThat(WasteDetector.evaluateUnchangedLoopContext(
        "body-step", "bp-1", 2, "ctx-fp", "different")).isEmpty();
  }

  @Test
  void repeatedLoopOutputFiresWhenFingerprintSeenBefore() {
    Optional<TokenGovernanceSignal> signal = WasteDetector.evaluateRepeatedLoopOutput(
        "body-step", "bp-1", 3, "out-fp", Set.of("out-fp", "other-fp"));

    assertThat(signal).isPresent();
    assertThat(signal.get().kind()).isEqualTo(WasteSignalKind.REPEATED_LOOP_OUTPUT);
  }

  @Test
  void repeatedLoopOutputSkipsWhenFingerprintNotSeenBefore() {
    assertThat(WasteDetector.evaluateRepeatedLoopOutput(
        "body-step", "bp-1", 1, "out-fp", Set.of())).isEmpty();
  }

  @Test
  void overbroadContextFiresWhenOtherStepDeclaresSelection() {
    StepDefinition scoped = step("s1", new ContextSelection(List.of(), List.of(), null));
    StepDefinition unscoped = step("s2", null);
    WorkflowDefinition wf = workflow(scoped, unscoped);

    Optional<TokenGovernanceSignal> signal = WasteDetector.evaluateOverbroadContext(wf, unscoped);

    assertThat(signal).isPresent();
    assertThat(signal.get().kind()).isEqualTo(WasteSignalKind.OVERBROAD_CONTEXT);
    assertThat(signal.get().stepId()).isEqualTo("s2");
  }

  @Test
  void overbroadContextSkipsWhenNoStepDeclaresSelection() {
    StepDefinition unscoped1 = step("s1", null);
    StepDefinition unscoped2 = step("s2", null);
    WorkflowDefinition wf = workflow(unscoped1, unscoped2);

    assertThat(WasteDetector.evaluateOverbroadContext(wf, unscoped2)).isEmpty();
  }

  @Test
  void overbroadContextSkipsWhenStepItselfDeclaresSelection() {
    StepDefinition scoped = step("s1", new ContextSelection(List.of(), List.of(), null));
    WorkflowDefinition wf = workflow(scoped);

    assertThat(WasteDetector.evaluateOverbroadContext(wf, scoped)).isEmpty();
  }

  @Test
  void normalizeOutputCanonicalizesJson() {
    String a = WasteDetector.normalizeOutput("{\"b\":1,\"a\":2}", mapper);
    String b = WasteDetector.normalizeOutput("{\"a\":2,\"b\":1}", mapper);

    assertThat(a).isEqualTo(b);
  }

  @Test
  void normalizeOutputCollapsesWhitespaceForNonJson() {
    assertThat(WasteDetector.normalizeOutput("  hello   world  \n", mapper))
        .isEqualTo("hello world");
  }
}
