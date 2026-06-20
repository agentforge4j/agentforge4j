// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Escape-hatch end-to-end test proving the harness assembles a fake-backed runtime, runs a minimal
 * workflow to completion, and exposes the captured result to the verb engine.
 */
class WorkflowHarnessEndToEndTest {

  private static final String WORKFLOW_JSON = """
      {
        "kind": "WORKFLOW",
        "id": "testkit-min",
        "name": "Testkit Minimal",
        "steps": [
          {
            "kind": "STEP",
            "stepId": "run-agent",
            "name": "Run Agent",
            "behaviour": { "type": "AGENT", "agentRef": "testkit-min-agent", "transition": "AUTO" }
          }
        ]
      }
      """;

  private static final String AGENT_JSON = """
      {
        "id": "testkit-min-agent",
        "name": "Testkit Min Agent",
        "locality": "CLOUD",
        "enabled": true,
        "version": "1.0.0",
        "providerPreferences": [ { "provider": "fake" } ],
        "supportedCommands": ["COMPLETE"]
      }
      """;

  private static final String SCRIPT_JSON = """
      {
        "schemaVersion": 1,
        "responses": [
          {
            "workflowId": "testkit-min",
            "stepId": "run-agent",
            "agentId": "testkit-min-agent",
            "ordinal": 0,
            "responseText": "[{\\"type\\":\\"COMPLETE\\"}]"
          }
        ]
      }
      """;

  @Test
  void runsMinimalWorkflowToCompletion(@TempDir Path workflowsDir) throws IOException {
    writeFixture(workflowsDir);

    WorkflowTestHarness harness = WorkflowTestHarness.builder()
        .workflowsDir(workflowsDir)
        .script(new ScenarioScriptLoader().fromJson(SCRIPT_JSON))
        .build();

    WorkflowRunResult result = harness.run("testkit-min");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .visitedStep("run-agent")
        .providerCallCount(1)
        .emittedEvent(WorkflowEventType.RUN_COMPLETED);
  }

  private static void writeFixture(Path workflowsDir) throws IOException {
    Path bundle = workflowsDir.resolve("testkit-min.workflow");
    Path agentDir = bundle.resolve("agents").resolve("testkit-min-agent.agent");
    Files.createDirectories(agentDir);
    Files.writeString(bundle.resolve("workflow.json"), WORKFLOW_JSON);
    Files.writeString(agentDir.resolve("agent.json"), AGENT_JSON);
    Files.writeString(agentDir.resolve("systemprompt.md"), "You are a deterministic test agent.");
  }
}
