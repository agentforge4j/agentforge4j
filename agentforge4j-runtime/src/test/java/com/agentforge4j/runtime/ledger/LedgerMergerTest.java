// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.ledger;

import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerMergerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode json(String text) throws Exception {
    return mapper.readTree(text);
  }

  private static LedgerDefinition ledger(LedgerMergeStrategy strategy, String keyField) {
    return new LedgerDefinition("requirements", "schema/req.json", strategy, keyField);
  }

  @Test
  void replaceSectionIgnoresCurrentAndNormalizesDelta() throws Exception {
    JsonNode current = json("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":["old?"]}""");
    JsonNode delta = json("""
        {"entries":[{"id":"REQ-2"}]}""");

    JsonNode merged = LedgerMerger.merge(ledger(LedgerMergeStrategy.REPLACE_SECTION, null),
        current, delta, mapper);

    assertThat(merged.get("entries")).hasSize(1);
    assertThat(merged.get("entries").get(0).get("id").asText()).isEqualTo("REQ-2");
    assertThat(merged.get("openQuestions")).isEmpty();
    assertThat(merged.get("conflicts")).isEmpty();
  }

  @Test
  void appendConcatenatesEntriesAndStructuralFields() throws Exception {
    JsonNode current = json("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":["q1"],"conflicts":[]}""");
    JsonNode delta = json("""
        {"entries":[{"id":"REQ-2"}],"openQuestions":["q2"]}""");

    JsonNode merged = LedgerMerger.merge(ledger(LedgerMergeStrategy.APPEND, null), current, delta,
        mapper);

    assertThat(merged.get("entries")).hasSize(2);
    assertThat(merged.get("openQuestions")).extracting(JsonNode::asText).containsExactly("q1", "q2");
  }

  @Test
  void mergeByKeyReplacesMatchingEntryAndAddsNew() throws Exception {
    JsonNode current = json("""
        {"entries":[{"id":"REQ-1","status":"OPEN"},{"id":"REQ-2","status":"OPEN"}]}""");
    JsonNode delta = json("""
        {"entries":[{"id":"REQ-1","status":"DONE"},{"id":"REQ-3","status":"OPEN"}]}""");

    JsonNode merged = LedgerMerger.merge(ledger(LedgerMergeStrategy.MERGE_BY_KEY, "id"), current,
        delta, mapper);

    assertThat(merged.get("entries")).hasSize(3);
    assertThat(merged.get("entries").get(0).get("status").asText()).isEqualTo("DONE");
    assertThat(merged.get("entries").get(1).get("id").asText()).isEqualTo("REQ-2");
    assertThat(merged.get("entries").get(2).get("id").asText()).isEqualTo("REQ-3");
  }

  @Test
  void mergeByKeyRejectsEntryMissingKeyField() throws Exception {
    JsonNode delta = json("""
        {"entries":[{"noId":true}]}""");

    assertThatThrownBy(() -> LedgerMerger.merge(ledger(LedgerMergeStrategy.MERGE_BY_KEY, "id"), null,
        delta, mapper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void nullCurrentTreatedAsEmptyEnvelope() throws Exception {
    JsonNode delta = json("""
        {"entries":[{"id":"REQ-1"}]}""");

    JsonNode merged = LedgerMerger.merge(ledger(LedgerMergeStrategy.APPEND, null), null, delta,
        mapper);

    assertThat(merged.get("entries")).hasSize(1);
  }

  @Test
  void writeMergedThenReadCurrentRoundTrips() throws Exception {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.parse(
        "2026-01-01T00:00:00Z"));
    LedgerDefinition ledger = ledger(LedgerMergeStrategy.APPEND, null);
    JsonNode merged = json("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":[],"conflicts":[]}""");

    assertThat(LedgerMerger.readCurrent(state, ledger, mapper)).isNull();

    LedgerMerger.writeMerged(state, ledger, merged);
    JsonNode readBack = LedgerMerger.readCurrent(state, ledger, mapper);

    assertThat(readBack.get("entries").get(0).get("id").asText()).isEqualTo("REQ-1");
  }
}
