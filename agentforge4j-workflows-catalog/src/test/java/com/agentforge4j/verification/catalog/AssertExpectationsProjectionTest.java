// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.testkit.capture.CaptureBundle;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the data-driven {@code contextPresent} / {@code contextMatches} projections added to
 * {@link CatalogScenarios#assertExpectations}: presence-only and regex-shape assertions reachable
 * from an {@code expected-result.json} fixture (not just from hand-written Java).
 */
class AssertExpectationsProjectionTest {

  private static WorkflowRunResult resultWith(String key, String value) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    state.putContextValue(key, new StringContextValue(value, ContextProvenance.SYSTEM_GENERATED));
    return new WorkflowRunResult("run-1", state, new CaptureBundle(List.of(), List.of()));
  }

  private static ExpectedResult.ExpectSpec expect(List<String> contextPresent,
      Map<String, String> contextMatches) {
    return new ExpectedResult.ExpectSpec(null, null, contextPresent, contextMatches, null, null,
        null, null);
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
}
