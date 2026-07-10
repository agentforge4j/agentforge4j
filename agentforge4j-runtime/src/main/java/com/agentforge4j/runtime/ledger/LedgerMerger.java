// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.ledger;

import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministically merges a ledger delta into the current ledger section, per
 * {@link LedgerDefinition#mergeStrategy()}. No LLM participates in the merge.
 *
 * <p>Both the current section and the delta follow the shipped ledger envelope shape:
 * {@code {entries: [...], openQuestions: [...], conflicts: [...]}}. Any field absent from a document
 * defaults to an empty array. {@code entries} is always shaped by {@code mergeStrategy}.
 * {@code openQuestions} and {@code conflicts} are structural — carried forward under
 * {@link LedgerMergeStrategy#APPEND} and {@link LedgerMergeStrategy#MERGE_BY_KEY}, where they are
 * concatenated (current followed by delta). {@link LedgerMergeStrategy#REPLACE_SECTION} does not carry
 * them forward: it discards the current section's {@code openQuestions}/{@code conflicts} entirely and
 * normalizes the delta's own (possibly absent, defaulting to empty) values instead.
 *
 * <p>Concatenation is deliberately verbatim — a delta that repeats an already-present
 * {@code openQuestions} or {@code conflicts} entry accumulates a duplicate. Deduplication would
 * require an identity rule for free-form entries, which no strategy declares; emitters should send
 * only new structural entries in a delta.
 */
public final class LedgerMerger {

  private static final String ENTRIES = "entries";
  private static final String OPEN_QUESTIONS = "openQuestions";
  private static final String CONFLICTS = "conflicts";

  private LedgerMerger() {
  }

  /**
   * Merges {@code delta} into {@code current} per {@code ledger}'s declared strategy.
   *
   * @param ledger  the ledger declaration; must not be {@code null}
   * @param current the current ledger section, or {@code null} when none exists yet
   * @param delta   the incoming delta; must not be {@code null}
   * @param mapper  used to construct result nodes; must not be {@code null}
   *
   * @return the merged ledger section, always carrying all three envelope fields; never {@code null}
   */
  public static JsonNode merge(LedgerDefinition ledger, JsonNode current, JsonNode delta,
      ObjectMapper mapper) {
    Validate.notNull(ledger, "ledger must not be null");
    Validate.notNull(delta, "delta must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    JsonNode existing = current != null ? current : emptyEnvelope(mapper);
    if (ledger.mergeStrategy() == LedgerMergeStrategy.REPLACE_SECTION) {
      return normalize(delta, mapper);
    }
    if (ledger.mergeStrategy() == LedgerMergeStrategy.APPEND) {
      return appendMerge(existing, delta, mapper);
    }
    return mergeByKey(existing, delta, ledger.mergeKeyField(), mapper);
  }

  /**
   * Reads the current section for {@code ledger} from {@code state}'s reserved ledger context key.
   *
   * @param state  run state to read from; must not be {@code null}
   * @param ledger the ledger declaration; must not be {@code null}
   * @param mapper used to parse the stored JSON; must not be {@code null}
   *
   * @return the parsed section, or {@code null} when no section has been written yet
   */
  public static JsonNode readCurrent(WorkflowState state, LedgerDefinition ledger,
      ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notNull(ledger, "ledger must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    Optional<ContextValue> stored = state.getContextValue(ReservedContextKeys.ledgerKey(ledger.id()));
    if (stored.isEmpty()) {
      return null;
    }
    Validate.isTrue(stored.get() instanceof JsonContextValue,
        "Ledger context key for '%s' does not hold JSON content".formatted(ledger.id()));
    return readTree((JsonContextValue) stored.get(), mapper);
  }

  /**
   * Writes {@code merged} into {@code state}'s reserved ledger context key for {@code ledger}, as
   * {@link ContextProvenance#SYSTEM_GENERATED} content. Deliberate: the merge algorithm this class
   * runs is deterministic and no LLM participates in it (see the class Javadoc). No production
   * command currently calls this method to submit a ledger delta — that write path is unwired in this
   * runtime version, so this stance concerns only test and future callers; it must be re-validated
   * once a real delta-submission path exists, specifically whether the SOURCE of a delta (as opposed
   * to the deterministic merge step itself) can be LLM-authored and needs a distinct provenance label,
   * the same distinction {@link com.agentforge4j.runtime.command.handler.RequestContextCommandHandler}
   * makes for granted content.
   *
   * @param state  run state to write to; must not be {@code null}
   * @param ledger the ledger declaration; must not be {@code null}
   * @param merged the merged section to persist; must not be {@code null}
   */
  public static void writeMerged(WorkflowState state, LedgerDefinition ledger, JsonNode merged) {
    Validate.notNull(state, "state must not be null");
    Validate.notNull(ledger, "ledger must not be null");
    Validate.notNull(merged, "merged must not be null");
    state.putContextValue(ReservedContextKeys.ledgerKey(ledger.id()),
        new JsonContextValue(merged.toString(), ContextProvenance.SYSTEM_GENERATED));
  }

  private static JsonNode readTree(JsonContextValue value, ObjectMapper mapper) {
    try {
      return mapper.readTree(value.json());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse stored ledger JSON: %s".formatted(e.getMessage()),
          e);
    }
  }

  private static JsonNode normalize(JsonNode delta, ObjectMapper mapper) {
    ObjectNode result = emptyEnvelope(mapper);
    result.set(ENTRIES, arrayOrEmpty(delta, ENTRIES, mapper));
    result.set(OPEN_QUESTIONS, arrayOrEmpty(delta, OPEN_QUESTIONS, mapper));
    result.set(CONFLICTS, arrayOrEmpty(delta, CONFLICTS, mapper));
    return result;
  }

  private static JsonNode appendMerge(JsonNode current, JsonNode delta, ObjectMapper mapper) {
    ObjectNode result = emptyEnvelope(mapper);
    result.set(ENTRIES, concat(current, delta, ENTRIES, mapper));
    result.set(OPEN_QUESTIONS, concat(current, delta, OPEN_QUESTIONS, mapper));
    result.set(CONFLICTS, concat(current, delta, CONFLICTS, mapper));
    return result;
  }

  private static JsonNode mergeByKey(JsonNode current, JsonNode delta, String keyField,
      ObjectMapper mapper) {
    Map<String, JsonNode> byKey = new LinkedHashMap<>();
    for (JsonNode entry : arrayOrEmpty(current, ENTRIES, mapper)) {
      byKey.put(keyOf(entry, keyField), entry);
    }
    for (JsonNode entry : arrayOrEmpty(delta, ENTRIES, mapper)) {
      byKey.put(keyOf(entry, keyField), entry);
    }
    ArrayNode entries = mapper.createArrayNode();
    byKey.values().forEach(entries::add);
    ObjectNode result = emptyEnvelope(mapper);
    result.set(ENTRIES, entries);
    result.set(OPEN_QUESTIONS, concat(current, delta, OPEN_QUESTIONS, mapper));
    result.set(CONFLICTS, concat(current, delta, CONFLICTS, mapper));
    return result;
  }

  private static String keyOf(JsonNode entry, String keyField) {
    JsonNode value = entry.get(keyField);
    Validate.notNull(value, () -> new IllegalArgumentException(
        "Ledger entry missing merge key field '%s': %s".formatted(keyField, entry)));
    return value.asText();
  }

  private static ArrayNode concat(JsonNode current, JsonNode delta, String field,
      ObjectMapper mapper) {
    ArrayNode result = mapper.createArrayNode();
    arrayOrEmpty(current, field, mapper).forEach(result::add);
    arrayOrEmpty(delta, field, mapper).forEach(result::add);
    return result;
  }

  private static ArrayNode arrayOrEmpty(JsonNode node, String field, ObjectMapper mapper) {
    JsonNode value = node.get(field);
    return value instanceof ArrayNode arrayNode ? arrayNode : mapper.createArrayNode();
  }

  private static ObjectNode emptyEnvelope(ObjectMapper mapper) {
    ObjectNode node = mapper.createObjectNode();
    node.set(ENTRIES, mapper.createArrayNode());
    node.set(OPEN_QUESTIONS, mapper.createArrayNode());
    node.set(CONFLICTS, mapper.createArrayNode());
    return node;
  }
}
