// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the harness gate loop over an {@code INPUT} behaviour: an empty response queue leaves the
 * run paused at {@code AWAITING_INPUT}, a queued {@link GateResponse.Input} resumes it to completion,
 * and a response that does not match the pending pause is rejected.
 */
class WorkflowHarnessGateTest {

  private static final String WORKFLOW_JSON = """
      {
        "kind": "WORKFLOW",
        "id": "testkit-input",
        "name": "Testkit Input",
        "steps": [
          {
            "kind": "STEP",
            "stepId": "collect",
            "name": "Collect Input",
            "behaviour": { "type": "INPUT", "artifactId": "user-form", "transition": "AUTO" }
          }
        ]
      }
      """;

  private static final String ARTIFACT_JSON = """
      {
        "id": "user-form",
        "items": [ { "type": "TEXT", "id": "name", "label": "Your name", "required": true } ]
      }
      """;

  private static final String SCRIPT_JSON = """
      { "schemaVersion": 1, "responses": [] }
      """;

  @Test
  void pausesAwaitingInputWhenNoResponseQueued(@TempDir Path workflowsDir) throws IOException {
    WorkflowRunResult result = harness(workflowsDir).run("testkit-input");

    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_INPUT)
        .inputRequested("collect");
  }

  @Test
  void resumesOnSubmittedInput(@TempDir Path workflowsDir) throws IOException {
    WorkflowRunResult result = harness(workflowsDir)
        .run("testkit-input", List.of(GateResponse.input(Map.of("name", "Alice"))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .contextEquals("user-form.name", "Alice");
  }

  @Test
  void rejectsResponseThatDoesNotMatchThePause(@TempDir Path workflowsDir) throws IOException {
    WorkflowTestHarness harness = harness(workflowsDir);

    assertThatThrownBy(() -> harness.run("testkit-input", List.of(GateResponse.review("nope"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWAITING_INPUT");
  }

  @Test
  void rejectsResponsesLeftUnconsumedAfterTheRunCompletes(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowTestHarness harness = harness(workflowsDir);

    // The first input drives the run to COMPLETED; the surplus response has no pause to resolve.
    assertThatThrownBy(() -> harness.run("testkit-input", List.of(
        GateResponse.input(Map.of("name", "Alice")),
        GateResponse.review("surplus"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unconsumed")
        .hasMessageContaining("COMPLETED");
  }

  private static WorkflowTestHarness harness(Path workflowsDir) throws IOException {
    Path bundle = workflowsDir.resolve("testkit-input.workflow");
    Files.createDirectories(bundle);
    Files.writeString(bundle.resolve("workflow.json"), WORKFLOW_JSON);
    Files.writeString(bundle.resolve("user-form.artifact.json"), ARTIFACT_JSON);
    return WorkflowTestHarness.builder()
        .workflowsDir(workflowsDir)
        .script(new ScenarioScriptLoader().fromJson(SCRIPT_JSON))
        .build();
  }
}
