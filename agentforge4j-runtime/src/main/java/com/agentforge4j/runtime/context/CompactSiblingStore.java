// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/**
 * Reads and writes a compaction step's {@link CompactSibling} at the reserved compact-sibling context
 * key for a canonical source id (see {@link ContextSourceId}). Content and metadata are stored
 * together so they can never drift apart.
 */
public final class CompactSiblingStore {

  private static final String CONTENT_FIELD = "content";
  private static final String METADATA_FIELD = "metadata";

  private CompactSiblingStore() {
  }

  /**
   * Reads the compact sibling stored for {@code sourceId}, if one exists.
   *
   * @param state    run state to read from; must not be {@code null}
   * @param sourceId the canonical source id; must not be blank
   * @param mapper   used to parse the stored JSON; must not be {@code null}
   *
   * @return the stored compact sibling, or empty when none has been produced yet
   */
  public static Optional<CompactSibling> read(WorkflowState state, String sourceId,
      ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(sourceId, "sourceId must not be blank");
    Validate.notNull(mapper, "mapper must not be null");
    Optional<ContextValue> stored = state.getContextValue(ReservedContextKeys.compactKey(sourceId));
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    Validate.isTrue(stored.get() instanceof JsonContextValue,
        "Compact-sibling context key for '%s' does not hold JSON content".formatted(sourceId));
    return Optional.of(parse((JsonContextValue) stored.get(), sourceId, mapper));
  }

  /**
   * Writes {@code sibling} to the reserved compact-sibling context key for {@code sourceId}, as
   * {@link ContextProvenance#SYSTEM_GENERATED} content. Deliberate, not a default: a compact sibling
   * is a deterministic, non-LLM transform ({@code DeterministicExtract}) of a whole-ledger
   * {@code LEDGER_SECTION} source (the only source kind {@code COMPACT} steps may target), and ledger
   * content itself is produced only by {@code LedgerMerger}'s deterministic merge — no LLM
   * participates in either step, so the compact form inherits the same framework-owned trust level as
   * its source.
   *
   * @param state    run state to write to; must not be {@code null}
   * @param sourceId the canonical source id; must not be blank
   * @param sibling  the compact sibling to persist; must not be {@code null}
   * @param mapper   used to serialize the sibling; must not be {@code null}
   */
  public static void write(WorkflowState state, String sourceId, CompactSibling sibling,
      ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(sourceId, "sourceId must not be blank");
    Validate.notNull(sibling, "sibling must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    ObjectNode root = mapper.createObjectNode();
    root.put(CONTENT_FIELD, sibling.content());
    root.set(METADATA_FIELD, mapper.valueToTree(sibling.metadata()));
    state.putContextValue(ReservedContextKeys.compactKey(sourceId),
        new JsonContextValue(root.toString(), ContextProvenance.SYSTEM_GENERATED));
  }

  private static CompactSibling parse(JsonContextValue value, String sourceId, ObjectMapper mapper) {
    try {
      JsonNode node = mapper.readTree(value.json());
      String content = node.get(CONTENT_FIELD).asText();
      CompactSiblingMetadata metadata = mapper.treeToValue(node.get(METADATA_FIELD),
          CompactSiblingMetadata.class);
      return new CompactSibling(content, metadata);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse stored compact sibling for '%s': %s".formatted(sourceId, e.getMessage()),
          e);
    }
  }
}
