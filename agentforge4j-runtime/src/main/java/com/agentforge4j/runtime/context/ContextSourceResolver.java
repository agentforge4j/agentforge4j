// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.runtime.exception.CompactSiblingUnavailableException;
import com.agentforge4j.runtime.ledger.LedgerMerger;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * Resolves a single {@link ContextSelector} to text content, honoring its {@link ContextVariant}: the
 * full source, or (when a compact sibling exists and its fingerprint matches the current source) the
 * compact sibling.
 *
 * <p>{@link ContextSourceKind#CONTEXT_PACK} is not resolvable here: no context-pack registry exists
 * until context packs are wired into bootstrap/runtime (a later phase). Resolving one throws
 * {@link UnsupportedOperationException}.
 */
public final class ContextSourceResolver {

  private final ContextRenderer contextRenderer;
  private final ObjectMapper objectMapper;

  public ContextSourceResolver(ContextRenderer contextRenderer, ObjectMapper objectMapper) {
    this.contextRenderer = Validate.notNull(contextRenderer, "contextRenderer must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
  }

  /**
   * Resolves {@code selector} honoring its declared variant.
   *
   * @param selector the selector to resolve; must not be {@code null}
   * @param state    run state to resolve against; must not be {@code null}
   * @param workflow the enclosing workflow (for ledger and artifact declarations); must not be
   *                 {@code null}
   *
   * @return the resolved content; never {@code null}
   *
   * @throws CompactSiblingUnavailableException if the variant is {@code COMPACT_ONLY} and no fresh
   *                                             compact sibling exists
   */
  public String resolve(ContextSelector selector, WorkflowState state, WorkflowDefinition workflow) {
    Validate.notNull(selector, "selector must not be null");
    if (selector.variant() == ContextVariant.FULL) {
      return resolveFull(selector, state, workflow);
    }
    String sourceId = ContextSourceId.of(selector);
    String fullContent = resolveFull(selector, state, workflow);
    String currentFingerprint = ContextFingerprint.of(fullContent);
    Optional<CompactSibling> sibling = CompactSiblingStore.read(state, sourceId, objectMapper);
    boolean fresh = sibling.isPresent()
        && sibling.get().metadata().sourceFingerprint().equals(currentFingerprint);
    if (fresh) {
      return sibling.get().content();
    }
    if (selector.variant() == ContextVariant.COMPACT_ONLY) {
      throw new CompactSiblingUnavailableException(sourceId,
          sibling.map(s -> s.metadata().sourceFingerprint()).orElse(null), currentFingerprint);
    }
    return fullContent;
  }

  /**
   * Resolves {@code selector}'s full (uncompacted) source content, ignoring any compact sibling. Used
   * both for {@link ContextVariant#FULL} selectors and by a {@code COMPACT} step to read the source it
   * compacts.
   *
   * @param selector the selector to resolve; must not be {@code null}
   * @param state    run state to resolve against; must not be {@code null}
   * @param workflow the enclosing workflow; must not be {@code null}
   *
   * @return the resolved full content; never {@code null}
   */
  public String resolveFull(ContextSelector selector, WorkflowState state,
      WorkflowDefinition workflow) {
    Validate.notNull(selector, "selector must not be null");
    Validate.notNull(state, "state must not be null");
    Validate.notNull(workflow, "workflow must not be null");
    ContextSourceKind kind = selector.kind();
    if (kind == ContextSourceKind.LEDGER_SECTION) {
      return resolveLedgerSection(selector.ref(), state, workflow);
    }
    if (kind == ContextSourceKind.ARTIFACT || kind == ContextSourceKind.STATE_KEY) {
      return resolveContextKey(selector.ref(), state);
    }
    if (kind == ContextSourceKind.STEP_OUTPUT) {
      return resolveStepOutput(selector.ref(), state);
    }
    throw new UnsupportedOperationException(
        "CONTEXT_PACK selector '%s' cannot be resolved: no context-pack registry is wired into the "
            + "runtime yet".formatted(selector.ref()));
  }

  private String resolveLedgerSection(String ref, WorkflowState state, WorkflowDefinition workflow) {
    int dot = ref.indexOf('.');
    String ledgerId = dot < 0 ? ref : ref.substring(0, dot);
    String subpath = dot < 0 ? null : ref.substring(dot + 1);
    LedgerDefinition ledger = findLedger(workflow, ledgerId);
    JsonNode section = LedgerMerger.readCurrent(state, ledger, objectMapper);
    JsonNode content = section != null ? section : LedgerMerger.merge(ledger, null,
        objectMapper.createObjectNode(), objectMapper);
    if (subpath != null) {
      JsonNode field = content.get(subpath);
      content = field != null ? field : objectMapper.createArrayNode();
    }
    return CanonicalJson.render(content, objectMapper);
  }

  private static LedgerDefinition findLedger(WorkflowDefinition workflow, String ledgerId) {
    return workflow.ledgers().stream()
        .filter(ledger -> ledger.id().equals(ledgerId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown ledger '%s' in workflow '%s'".formatted(ledgerId, workflow.id())));
  }

  private String resolveContextKey(String key, WorkflowState state) {
    Optional<ContextValue> value = state.getContextValue(key);
    Validate.isTrue(value.isPresent(),
        () -> new IllegalStateException("No context value for key '%s'".formatted(key)));
    return contextRenderer.renderSingleValue(value.get()).toString();
  }

  private static String resolveStepOutput(String stepId, WorkflowState state) {
    return state.getStepOutput(stepId).orElseThrow(() -> new IllegalStateException(
        "No output recorded for step '%s'".formatted(stepId)));
  }
}
