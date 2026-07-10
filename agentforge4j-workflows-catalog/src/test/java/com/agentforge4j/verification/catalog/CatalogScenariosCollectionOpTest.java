// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.testkit.scenario.CollectionOp;
import com.agentforge4j.testkit.scenario.GateResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the scenario DSL's {@code type: "collection"} gate-spec parsing path in
 * {@link CatalogScenarios#toGateResponse}: the {@code submit}/{@code replace}/{@code withdraw}/
 * {@code close} op vocabulary, the 0-based submit-{@code ordinal} extraction for replace/withdraw,
 * and the {@code closeReason} string-to-{@link CloseReason} conversion (including its invalid-value
 * error path). No shipped scenario fixture exercises this path yet (the catalog is empty during the
 * clean-slate window; see {@link ShippedCatalogScenarioTest}), and every existing test constructs
 * {@link CollectionOp}/{@link GateResponse.Collection} directly in Java, bypassing this parsing
 * entirely -- this is the sole coverage of the JSON scenario -&gt; {@code CollectionOp} translation.
 */
class CatalogScenariosCollectionOpTest {

  @Test
  void collectionGateSpecTranslatesSubmitOpToASubmitCollectionOp() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("submit", "{\"cv\":\"a\"}", null,
            "client-token-1", "dedupe-1", null, null, "actor-a")));

    GateResponse response = CatalogScenarios.toGateResponse(spec);

    assertThat(response).isInstanceOf(GateResponse.Collection.class);
    List<CollectionOp> ops = ((GateResponse.Collection) response).ops();
    assertThat(ops).hasSize(1);
    CollectionOp.Submit submit = (CollectionOp.Submit) ops.get(0);
    assertThat(submit.payload()).isEqualTo("{\"cv\":\"a\"}");
    assertThat(submit.clientToken()).isEqualTo("client-token-1");
    assertThat(submit.dedupeKey()).isEqualTo("dedupe-1");
    assertThat(submit.actorId()).isEqualTo("actor-a");
  }

  @Test
  void collectionGateSpecTranslatesReplaceOpToAReplaceCollectionOpUsingSubmissionIdAsOrdinal() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("replace", "{\"cv\":\"a2\"}", 0,
            null, null, null, null, "actor-a")));

    List<CollectionOp> ops = ((GateResponse.Collection) CatalogScenarios.toGateResponse(spec)).ops();

    CollectionOp.Replace replace = (CollectionOp.Replace) ops.get(0);
    assertThat(replace.target()).isEqualTo(0);
    assertThat(replace.payload()).isEqualTo("{\"cv\":\"a2\"}");
    assertThat(replace.actorId()).isEqualTo("actor-a");
  }

  @Test
  void collectionGateSpecTranslatesWithdrawOpToAWithdrawCollectionOpUsingSubmissionIdAsOrdinal() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("withdraw", null, 1,
            null, null, null, null, "actor-b")));

    List<CollectionOp> ops = ((GateResponse.Collection) CatalogScenarios.toGateResponse(spec)).ops();

    CollectionOp.Withdraw withdraw = (CollectionOp.Withdraw) ops.get(0);
    assertThat(withdraw.target()).isEqualTo(1);
    assertThat(withdraw.actorId()).isEqualTo("actor-b");
  }

  @Test
  void collectionGateSpecTranslatesCloseOpToACloseCollectionOpConvertingTheReasonString() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("close", null, null,
            null, null, "MANUAL", true, null)));

    List<CollectionOp> ops = ((GateResponse.Collection) CatalogScenarios.toGateResponse(spec)).ops();

    CollectionOp.Close close = (CollectionOp.Close) ops.get(0);
    assertThat(close.reason()).isEqualTo(CloseReason.MANUAL);
    assertThat(close.override()).isTrue();
  }

  @Test
  void collectionGateSpecTranslatesAFullMultiOpInteractionInOrder() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("submit", "{\"cv\":\"a\"}", null,
            null, null, null, null, "actor-a"),
        new ExpectedResult.GateSpec.CollectionOpSpec("submit", "{\"cv\":\"b\"}", null,
            null, null, null, null, "actor-b"),
        new ExpectedResult.GateSpec.CollectionOpSpec("replace", "{\"cv\":\"a2\"}", 0,
            null, null, null, null, "actor-a"),
        new ExpectedResult.GateSpec.CollectionOpSpec("withdraw", null, 1,
            null, null, null, null, "actor-b"),
        new ExpectedResult.GateSpec.CollectionOpSpec("close", null, null,
            null, null, "DEADLINE", null, null)));

    List<CollectionOp> ops = ((GateResponse.Collection) CatalogScenarios.toGateResponse(spec)).ops();

    assertThat(ops).hasSize(5);
    assertThat(ops.get(0)).isInstanceOf(CollectionOp.Submit.class);
    assertThat(ops.get(1)).isInstanceOf(CollectionOp.Submit.class);
    assertThat(ops.get(2)).isInstanceOf(CollectionOp.Replace.class);
    assertThat(ops.get(3)).isInstanceOf(CollectionOp.Withdraw.class);
    CollectionOp.Close close = (CollectionOp.Close) ops.get(4);
    assertThat(close.reason()).isEqualTo(CloseReason.DEADLINE);
  }

  @Test
  void replaceOpWithoutASubmissionIdOrdinalIsRejected() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("replace", "{}", null,
            null, null, null, null, null)));

    assertThatThrownBy(() -> CatalogScenarios.toGateResponse(spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("submissionId");
  }

  @Test
  void withdrawOpWithoutASubmissionIdOrdinalIsRejected() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("withdraw", null, null,
            null, null, null, null, null)));

    assertThatThrownBy(() -> CatalogScenarios.toGateResponse(spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("submissionId");
  }

  @Test
  void closeOpWithoutAReasonIsRejected() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("close", null, null,
            null, null, null, null, null)));

    assertThatThrownBy(() -> CatalogScenarios.toGateResponse(spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void closeOpWithAnUnknownReasonStringIsRejected() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("close", null, null,
            null, null, "NOT_A_REAL_REASON", null, null)));

    assertThatThrownBy(() -> CatalogScenarios.toGateResponse(spec))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownCollectionOpVerbIsRejected() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of(
        new ExpectedResult.GateSpec.CollectionOpSpec("teleport", null, null,
            null, null, null, null, null)));

    assertThatThrownBy(() -> CatalogScenarios.toGateResponse(spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("teleport");
  }

  @Test
  void emptyOpsListTranslatesToAnEmptyCollectionGateResponse() {
    ExpectedResult.GateSpec spec = collectionGateSpec(List.of());

    List<CollectionOp> ops = ((GateResponse.Collection) CatalogScenarios.toGateResponse(spec)).ops();

    assertThat(ops).isEmpty();
  }

  private static ExpectedResult.GateSpec collectionGateSpec(
      List<ExpectedResult.GateSpec.CollectionOpSpec> ops) {
    return new ExpectedResult.GateSpec("collection", null, null, null, null, null, ops);
  }
}
