// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizationException;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.CollectionOp;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  private static String dedupeWorkflowJson() {
    return """
        {
          "kind": "WORKFLOW",
          "id": "testkit-collection-dedupe",
          "name": "Testkit Collection Dedupe",
          "steps": [
            {
              "kind": "STEP",
              "stepId": "cv-intake",
              "name": "Collect CVs",
              "behaviour": {
                "type": "COLLECTION",
                "minItems": 1,
                "duplicatePolicy": "REJECT_BY_DEDUPE_KEY",
                "reopenPolicy": "NONE",
                "authorizationMode": "OPEN",
                "transition": "AUTO"
              },
              "contextMapping": { "inputKeys": [], "outputKeys": ["collectedCvs"] }
            }
          ]
        }
        """;
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
          },
          {
            "id": "req-replace-own",
            "type": "rbac_step_action_allowed",
            "scope": "STEP_ACTION",
            "stepId": "cv-intake",
            "action": "replace_own",
            "required": false,
            "resolution": "DEFERRED"
          },
          {
            "id": "req-withdraw-own",
            "type": "rbac_step_action_allowed",
            "scope": "STEP_ACTION",
            "stepId": "cv-intake",
            "action": "withdraw_own",
            "required": false,
            "resolution": "DEFERRED"
          },
          {
            "id": "req-view",
            "type": "rbac_step_action_allowed",
            "scope": "STEP_ACTION",
            "stepId": "cv-intake",
            "action": "view",
            "required": false,
            "resolution": "DEFERRED"
          }
        ]
      }
      """;

  private static CollectionOp submit(String value) {
    return new CollectionOp.Submit("{\"cv\":\"%s\"}".formatted(value), null, null, null);
  }

  private static CollectionOp submit(String value, String actorId) {
    return new CollectionOp.Submit("{\"cv\":\"%s\"}".formatted(value), null, null, actorId);
  }

  private static CollectionOp submitWithDedupeKey(String value, String dedupeKey) {
    return new CollectionOp.Submit("{\"cv\":\"%s\"}".formatted(value), null, dedupeKey, null);
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
            new CollectionOp.Replace(0, "{\"cv\":\"a2\"}", null),
            new CollectionOp.Withdraw(1, null),
            close()))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_REPLACED)
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_WITHDRAWN);
    // The replace/withdraw events firing is not enough on its own -- confirm the materialized
    // output actually carries the replaced value and excludes the withdrawn item, rather than
    // just recording that the operations happened.
    List<JsonNode> collected = materializedItems(result, "collectedCvs");
    assertThat(collected).hasSize(1);
    assertThat(collected.get(0).path("inline").path("cv").asText()).isEqualTo("a2");
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
  void submitRejectedByAGateConstraintSurfacesAsAHarnessFailureInsteadOfANullSubmissionId(
      @TempDir Path workflowsDir) throws IOException {
    WorkflowTestHarness harness = dedupeHarness(workflowsDir);

    // Two submits sharing a dedupeKey under REJECT_BY_DEDUPE_KEY: the second is refused by the
    // gate (SubmissionResult.Status.REJECTED, submissionId null). Previously the harness silently
    // recorded the null id and let the scenario continue; it must now surface the rejection.
    assertThatThrownBy(() -> harness.run("testkit-collection-dedupe",
        List.of(GateResponse.collection(List.of(
            submitWithDedupeKey("a", "same-key"),
            submitWithDedupeKey("b", "same-key"),
            close())))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rejected")
        .hasMessageContaining("DUPLICATE");
  }

  @Test
  void nonOwnerReplaceIsDeniedUnderOwnerReplacePolicy(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowTestHarness harness = harness(workflowsDir, "NONE");

    assertThatThrownBy(() -> harness.run("testkit-collection",
        List.of(GateResponse.collection(List.of(
            submit("a", "actor-owner"),
            new CollectionOp.Replace(0, "{\"cv\":\"a2\"}", "actor-intruder"))))))
        .isInstanceOf(CollectionAuthorizationException.class)
        .hasMessageContaining("restricted to the submitting actor");
  }

  @Test
  void nonOwnerWithdrawIsDeniedUnderOwnerWithdrawPolicy(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowTestHarness harness = harness(workflowsDir, "NONE");

    assertThatThrownBy(() -> harness.run("testkit-collection",
        List.of(GateResponse.collection(List.of(
            submit("a", "actor-owner"),
            new CollectionOp.Withdraw(0, "actor-intruder"))))))
        .isInstanceOf(CollectionAuthorizationException.class)
        .hasMessageContaining("restricted to the submitting actor");
  }

  @Test
  void ownerReplaceSucceedsWhenTheSameActorThatSubmittedReplaces(@TempDir Path workflowsDir)
      throws IOException {
    WorkflowRunResult result = harness(workflowsDir, "NONE").run("testkit-collection",
        List.of(GateResponse.collection(List.of(
            submit("a", "actor-owner"),
            new CollectionOp.Replace(0, "{\"cv\":\"a2\"}", "actor-owner"),
            close()))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.COLLECTION_ITEM_REPLACED);
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

  private static WorkflowTestHarness dedupeHarness(Path workflowsDir) throws IOException {
    Path bundle = workflowsDir.resolve("testkit-collection-dedupe.workflow");
    Files.createDirectories(bundle);
    Files.writeString(bundle.resolve("workflow.json"), dedupeWorkflowJson());
    return WorkflowTestHarness.builder()
        .workflowsDir(workflowsDir)
        .script(new ScenarioScriptLoader().fromJson(SCRIPT_JSON))
        .build();
  }

  /**
   * Reads a step's materialized collection output back out of the final context as a list of item
   * nodes, so a test can assert on the actual published content rather than only on which events
   * fired.
   */
  private static List<JsonNode> materializedItems(WorkflowRunResult result, String contextKey)
      throws IOException {
    ContextValue value = result.finalState().getContextValue(contextKey)
        .orElseThrow(() -> new AssertionError(
            "Expected context key '%s' to be present".formatted(contextKey)));
    assertThat(value).isInstanceOf(JsonContextValue.class);
    JsonNode array = new ObjectMapper().readTree(((JsonContextValue) value).json());
    List<JsonNode> items = new ArrayList<>();
    array.forEach(items::add);
    return items;
  }
}
