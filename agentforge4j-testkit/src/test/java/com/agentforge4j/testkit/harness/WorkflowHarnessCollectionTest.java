// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizationException;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.CollectionOp;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the harness over an {@code AWAITING_COLLECTION} pause via a {@link GateResponse.Collection}:
 * an empty queue leaves the run at the gate; a scripted op list (submit/replace/withdraw then close)
 * advances it; the reopen-{@code ALLOWED} close path is auto-continued; and a mismatched response is
 * rejected.
 */
class WorkflowHarnessCollectionTest {

  private static final String SCRIPT_JSON = """
      { "schemaVersion": 1, "responses": [] }
      """;

  private static String workflowJson(String reopenPolicy) {
    return """
        {
          "kind": "WORKFLOW",
          "id": "testkit-collection",
          "name": "Testkit Collection",
          "steps": [
            {
              "kind": "STEP",
              "stepId": "cv-intake",
              "name": "Collect CVs",
              "behaviour": {
                "type": "COLLECTION",
                "minItems": 1,
                "replacementPolicy": "OWNER_REPLACE",
                "withdrawalPolicy": "OWNER_WITHDRAW",
                "reopenPolicy": "%s",
                "authorizationMode": "OPEN",
                "transition": "AUTO"
              },
              "contextMapping": { "inputKeys": [], "outputKeys": ["collectedCvs"] }
            }
          ]
        }
        """.formatted(reopenPolicy);
  }

  private static final String ENFORCED_WORKFLOW_JSON = """
      {
        "kind": "WORKFLOW",
        "id": "testkit-collection-enforced",
        "name": "Testkit Collection Enforced",
        "steps": [
          {
            "kind": "STEP",
            "stepId": "cv-intake",
            "name": "Collect CVs",
            "behaviour": {
              "type": "COLLECTION",
              "minItems": 0,
              "replacementPolicy": "OWNER_REPLACE",
              "withdrawalPolicy": "OWNER_WITHDRAW",
              "reopenPolicy": "NONE",
              "authorizationMode": "ENFORCED",
              "transition": "AUTO"
            },
            "contextMapping": { "inputKeys": [], "outputKeys": ["collectedCvs"] }
          }
        ],
        "requirements": [
          {
            "id": "req-submit",
            "type": "rbac_step_action_allowed",
            "scope": "STEP_ACTION",
            "stepId": "cv-intake",
            "action": "submit",
            "required": false,
            "resolution": "DEFERRED"
          },
          {
            "id": "req-close",
            "type": "rbac_step_action_allowed",
            "scope": "STEP_ACTION",
            "stepId": "cv-intake",
            "action": "close",
            "required": false,
            "resolution": "DEFERRED"
          }
        ]
      }
      """;

  private static CollectionOp submit(String value) {
    return new CollectionOp.Submit("{\"cv\":\"%s\"}".formatted(value), null, null);
  }

  private static CollectionOp close() {
    return new CollectionOp.Close(CloseReason.MANUAL, false);
  }

  @Test
  void pausesAwaitingCollectionWhenNoResponseQueued(@TempDir Path workflowsDir) throws IOException {
    WorkflowRunResult result = harness(workflowsDir, "NONE").run("testkit-collection");

    WorkflowRunAssert.assertThat(result).reachedPendingState(WorkflowStatus.AWAITING_COLLECTION);
  }

  @Test
  void submitsThenCloseCompletesAndPublishesOutput(@TempDir Path workflowsDir) throws IOException {
    WorkflowRunResult result = harness(workflowsDir, "NONE").run("testkit-collection",
        List.of(GateResponse.collection(List.of(submit("a"), submit("b"), close()))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_SUBMITTED)
        .emittedEvent(WorkflowEventType.COLLECTION_CLOSED);
  }

  @Test
  void reopenAllowedCloseIsAutoContinued(@TempDir Path workflowsDir) throws IOException {
    WorkflowRunResult result = harness(workflowsDir, "ALLOWED").run("testkit-collection",
        List.of(GateResponse.collection(List.of(submit("a"), close()))));

    WorkflowRunAssert.assertThat(result).isCompleted();
  }

  @Test
  void replaceAndWithdrawTargetPriorSubmitsByOrdinal(@TempDir Path workflowsDir) throws IOException {
    WorkflowRunResult result = harness(workflowsDir, "NONE").run("testkit-collection",
        List.of(GateResponse.collection(List.of(
            submit("a"),
            submit("b"),
            new CollectionOp.Replace(0, "{\"cv\":\"a2\"}"),
            new CollectionOp.Withdraw(1),
            close()))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_REPLACED)
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_WITHDRAWN);
  }

  @Test
  void rejectsResponseThatDoesNotMatchTheCollectionPause(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowTestHarness harness = harness(workflowsDir, "NONE");

    assertThatThrownBy(() -> harness.run("testkit-collection",
        List.of(GateResponse.review("nope"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWAITING_COLLECTION");
  }

  @Test
  void enforcedGateAllowsSubmitWhenAuthorizerWiredToAllowAll(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowRunResult result = enforcedHarness(workflowsDir, FakeCollectionAuthorizer.allowAll())
        .run("testkit-collection-enforced",
            List.of(GateResponse.collection(List.of(submit("a"), close()))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_SUBMITTED);
  }

  @Test
  void enforcedGateDeniesSubmitWhenAuthorizerWiredToDenyAll(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowTestHarness harness = enforcedHarness(workflowsDir, FakeCollectionAuthorizer.denyAll());

    assertThatThrownBy(() -> harness.run("testkit-collection-enforced",
        List.of(GateResponse.collection(List.of(submit("a"))))))
        .isInstanceOf(CollectionAuthorizationException.class);
  }

  private static WorkflowTestHarness harness(Path workflowsDir, String reopenPolicy)
      throws IOException {
    Path bundle = workflowsDir.resolve("testkit-collection.workflow");
    Files.createDirectories(bundle);
    Files.writeString(bundle.resolve("workflow.json"), workflowJson(reopenPolicy));
    return WorkflowTestHarness.builder()
        .workflowsDir(workflowsDir)
        .script(new ScenarioScriptLoader().fromJson(SCRIPT_JSON))
        .build();
  }

  private static WorkflowTestHarness enforcedHarness(Path workflowsDir,
      FakeCollectionAuthorizer authorizer) throws IOException {
    Path bundle = workflowsDir.resolve("testkit-collection-enforced.workflow");
    Files.createDirectories(bundle);
    Files.writeString(bundle.resolve("workflow.json"), ENFORCED_WORKFLOW_JSON);
    return WorkflowTestHarness.builder()
        .workflowsDir(workflowsDir)
        .script(new ScenarioScriptLoader().fromJson(SCRIPT_JSON))
        .collectionAuthorizer(authorizer)
        .build();
  }
}
