// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.testkit.capture.CaptureBundle;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the data-driven {@code contextPresent} / {@code contextMatches} / {@code
 * stepVisitCounts} / {@code orderedSteps} projections added to
 * {@link CatalogScenarios#assertExpectations}: presence-only, regex-shape, visit-count, and
 * ordering assertions reachable from an {@code expected-result.json} fixture (not just from
 * hand-written Java).
 */
class AssertExpectationsProjectionTest {

  private static WorkflowRunResult resultWith(String key, String value) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    state.putContextValue(key, new StringContextValue(value, ContextProvenance.SYSTEM_GENERATED));
    return new WorkflowRunResult("run-1", state, new CaptureBundle(List.of(), List.of()));
  }

  private static WorkflowRunResult resultWithVisitedSteps(String... stepIds) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    List<WorkflowEvent> events = new ArrayList<>();
    int seq = 0;
    for (String stepId : stepIds) {
      seq++;
      events.add(new WorkflowEvent(
          "e" + seq, "run-1", stepId, WorkflowEventType.STEP_STARTED, null, "runtime",
          Instant.EPOCH));
    }
    return new WorkflowRunResult("run-1", state, new CaptureBundle(events, List.of()));
  }

  private static ExpectedResult.ExpectSpec expect(List<String> contextPresent,
      Map<String, String> contextMatches) {
    return new ExpectedResult.ExpectSpec(null, null, contextPresent, contextMatches, null, null,
        null, null, null, null);
  }

  private static ExpectedResult.ExpectSpec expectStepVisitCounts(
      Map<String, Integer> stepVisitCounts) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null, null,
        stepVisitCounts, null);
  }

  private static ExpectedResult.ExpectSpec expectOrderedSteps(List<String> orderedSteps) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null, null, null,
        orderedSteps);
  }

  @Test
  void contextPresentAndContextMatchesPassWhenSatisfied() {
    WorkflowRunResult result = resultWith("grade", "HIGH");

    assertThatCode(() -> CatalogScenarios.assertExpectations(result,
        expect(List.of("grade"), Map.of("grade", "HIGH|MEDIUM|LOW|VERY_LOW"))))
        .doesNotThrowAnyException();
  }

  @Test
  void contextPresentFailsWhenKeyAbsent() {
    WorkflowRunResult result = resultWith("grade", "HIGH");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expect(List.of("estimatedMaxTokens"), null)))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void contextMatchesFailsOnRegexMismatch() {
    WorkflowRunResult result = resultWith("grade", "HIGH");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expect(null, Map.of("grade", "LOW"))))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void stepVisitCountsPassesWhenCountMatches() {
    WorkflowRunResult result = resultWithVisitedSteps("s1", "s2", "s1");

    assertThatCode(() -> CatalogScenarios.assertExpectations(result,
        expectStepVisitCounts(Map.of("s1", 2, "s2", 1))))
        .doesNotThrowAnyException();
  }

  @Test
  void stepVisitCountsFailsOnCountMismatch() {
    WorkflowRunResult result = resultWithVisitedSteps("s1", "s2", "s1");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expectStepVisitCounts(Map.of("s1", 1))))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void orderedStepsPassesWhenVisitedInOrder() {
    WorkflowRunResult result = resultWithVisitedSteps("s1", "s2", "s3");

    assertThatCode(() -> CatalogScenarios.assertExpectations(result,
        expectOrderedSteps(List.of("s1", "s3"))))
        .doesNotThrowAnyException();
  }

  @Test
  void orderedStepsFailsWhenVisitedOutOfOrder() {
    WorkflowRunResult result = resultWithVisitedSteps("s1", "s2", "s3");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expectOrderedSteps(List.of("s3", "s1"))))
        .isInstanceOf(AssertionError.class);
  }
}
