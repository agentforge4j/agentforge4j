// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.testkit.capture.CaptureBundle;
import com.agentforge4j.testkit.capture.CapturedFile;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the data-driven {@code contextPresent} / {@code contextMatches} / {@code
 * stepVisitCounts} / {@code orderedSteps} / {@code failedBecause} / {@code absentFiles} /
 * {@code notEmittedEvents} projections of {@link CatalogScenarios#assertExpectations}: each
 * assertion reachable from an {@code expected-result.json} fixture (not just from hand-written
 * Java) is proven to pass when satisfied and throw when not.
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
        null, null, null, null, null, null, null);
  }

  private static ExpectedResult.ExpectSpec expectStepVisitCounts(
      Map<String, Integer> stepVisitCounts) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null, null, null,
        null, stepVisitCounts, null, null);
  }

  private static ExpectedResult.ExpectSpec expectOrderedSteps(List<String> orderedSteps) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null, null, null,
        null, null, orderedSteps, null);
  }

  private static ExpectedResult.ExpectSpec expectNotEmittedEvents(List<String> notEmittedEvents) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null,
        notEmittedEvents, null, null, null, null, null);
  }

  private static ExpectedResult.ExpectSpec expectAbsentFiles(List<String> absentFiles) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null, null, null,
        absentFiles, null, null, null);
  }

  private static ExpectedResult.ExpectSpec expectFailedBecause(String fragment) {
    return new ExpectedResult.ExpectSpec(null, null, null, null, null, null, null, null, null,
        null, null, null, fragment);
  }

  private static WorkflowRunResult failedResult(String failureReason) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    state.setStatus(WorkflowStatus.FAILED);
    state.setRunFailure(new RunFailure.ExceptionFailure(failureReason, "s1", "support-1"));
    return new WorkflowRunResult("run-1", state, new CaptureBundle(List.of(), List.of()));
  }

  private static WorkflowRunResult resultWithFile(String path) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    return new WorkflowRunResult("run-1", state, new CaptureBundle(List.of(),
        List.of(new CapturedFile("run-1", "s1", path, "content"))));
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

  @Test
  void failedBecausePassesWhenReasonContainsFragment() {
    WorkflowRunResult result = failedResult("Validator rejected the bundle: missing README");

    assertThatCode(() -> CatalogScenarios.assertExpectations(result,
        expectFailedBecause("missing README")))
        .doesNotThrowAnyException();
  }

  @Test
  void failedBecauseFailsWhenReasonLacksFragment() {
    WorkflowRunResult result = failedResult("Validator rejected the bundle: missing README");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expectFailedBecause("path traversal")))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void failedBecauseFailsWhenTheRunDidNotFail() {
    WorkflowRunResult result = resultWith("grade", "HIGH");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expectFailedBecause("anything")))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void absentFilesPassesWhenNoSuchFileWasCreated() {
    WorkflowRunResult result = resultWithFile("out/other.txt");

    assertThatCode(() -> CatalogScenarios.assertExpectations(result,
        expectAbsentFiles(List.of("out/forbidden.txt"))))
        .doesNotThrowAnyException();
  }

  @Test
  void absentFilesFailsWhenTheFileWasCreated() {
    WorkflowRunResult result = resultWithFile("out/forbidden.txt");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expectAbsentFiles(List.of("out/forbidden.txt"))))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void notEmittedEventsPassesWhenAbsent() {
    WorkflowRunResult result = resultWithVisitedSteps("s1");

    assertThatCode(() -> CatalogScenarios.assertExpectations(result,
        expectNotEmittedEvents(List.of("RUN_COMPLETED"))))
        .doesNotThrowAnyException();
  }

  @Test
  void notEmittedEventsFailsWhenEmitted() {
    WorkflowRunResult result = resultWithVisitedSteps("s1");

    assertThatThrownBy(() -> CatalogScenarios.assertExpectations(result,
        expectNotEmittedEvents(List.of("STEP_STARTED"))))
        .isInstanceOf(AssertionError.class);
  }
}
