// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.contextpack.ContextPackVariant;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.runtime.ContextPackRegistry;
import com.agentforge4j.runtime.exception.CompactSiblingUnavailableException;
import com.agentforge4j.runtime.ledger.LedgerMerger;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a single {@link ContextSelector} to text content, honoring its {@link ContextVariant}: the
 * full source, or (when a compact sibling exists and its fingerprint matches the current source) the
 * compact sibling.
 *
 * <p>{@link ContextSourceKind#CONTEXT_PACK} is resolved directly against the configured
 * {@link ContextPackRegistry} and bypasses the compact-sibling machinery entirely: a pack's
 * {@code compact} variant ({@code "full"} and {@code "compact"} are the conventional variant key
 * names) is an author-provided file, not a runtime-computed sibling, so there is no
 * fingerprint-staleness concept for it — it either exists in the pack manifest or it does not.
 */
public final class ContextSourceResolver {

  private static final String FULL_VARIANT = "full";
  private static final String COMPACT_VARIANT = "compact";

  private final ContextRenderer contextRenderer;
  private final ObjectMapper objectMapper;
  private final ContextPackRegistry contextPackRegistry;

  public ContextSourceResolver(ContextRenderer contextRenderer, ObjectMapper objectMapper,
      ContextPackRegistry contextPackRegistry) {
    this.contextRenderer = Validate.notNull(contextRenderer, "contextRenderer must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.contextPackRegistry = Validate.notNull(contextPackRegistry,
        "contextPackRegistry must not be null");
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
    if (selector.kind() == ContextSourceKind.CONTEXT_PACK) {
      return resolveContextPack(selector);
    }
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
   * compacts. For a {@code CONTEXT_PACK} selector this is the pack's {@code "full"} variant,
   * regardless of the selector's declared variant.
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
    // A CONTEXT_PACK's "full" source is its author-provided "full" variant — resolveFull must never
    // honour the selector's variant, or its "ignoring any compact form" contract would break for
    // pack selectors (e.g. a COMPACT_ONLY pack selector failing closed on a full-content read).
    return packFullVariant(lookUpPack(selector));
  }

  /**
   * Resolves a {@code CONTEXT_PACK} selector directly against the pack registry: {@code FULL} reads
   * the pack's {@code "full"} variant; {@code COMPACT_PREFERRED} reads {@code "compact"} and falls
   * back to {@code "full"} when the pack declares no compact variant; {@code COMPACT_ONLY} reads
   * {@code "compact"} and fails closed when absent. No fingerprint or compact-sibling machinery is
   * involved — a pack variant is an author-provided file, not a runtime-computed sibling.
   */
  private String resolveContextPack(ContextSelector selector) {
    ContextPack pack = lookUpPack(selector);
    if (selector.variant() == ContextVariant.FULL) {
      return packFullVariant(pack);
    }
    Optional<String> compact = variantContent(pack, COMPACT_VARIANT);
    if (compact.isPresent()) {
      return compact.get();
    }
    if (selector.variant() == ContextVariant.COMPACT_ONLY) {
      throw new IllegalStateException(
          "Context pack '%s' declares no '%s' variant (COMPACT_ONLY)".formatted(pack.name(),
              COMPACT_VARIANT));
    }
    return packFullVariant(pack);
  }

  private ContextPack lookUpPack(ContextSelector selector) {
    return contextPackRegistry.get(selector.ref())
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown context pack '%s'".formatted(selector.ref())));
  }

  private static String packFullVariant(ContextPack pack) {
    return variantContent(pack, FULL_VARIANT)
        .orElseThrow(() -> new IllegalStateException(
            "Context pack '%s' declares no '%s' variant".formatted(pack.name(), FULL_VARIANT)));
  }

  private static Optional<String> variantContent(ContextPack pack, String variantName) {
    return Optional.ofNullable(pack.variants().get(variantName)).map(ContextPackVariant::content);
  }

  private String resolveLedgerSection(String ref, WorkflowState state, WorkflowDefinition workflow) {
    int dot = ref.indexOf('.');
    String ledgerId = dot < 0 ? ref : ref.substring(0, dot);
    String subpath = dot < 0 ? null : ref.substring(dot + 1);
    LedgerDefinition ledger = findLedger(workflow, ledgerId);
    JsonNode section = LedgerMerger.readCurrent(state, ledger, objectMapper);
    JsonNode envelope = section != null ? section : LedgerMerger.merge(ledger, null,
        objectMapper.createObjectNode(), objectMapper);
    JsonNode content = envelope;
    if (subpath != null) {
      JsonNode field = envelope.get(subpath);
      // Fail loud on an unknown section: silently resolving a typo'd section name to an empty
      // array would hide the mistake from both the author and the model.
      Validate.notNull(field, () -> new IllegalArgumentException(
          "Ledger '%s' has no section '%s'; available sections: %s"
              .formatted(ledgerId, subpath, fieldNames(envelope))));
      content = field;
    }
    return CanonicalJson.render(content, objectMapper);
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
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
    // Canonicalize, same as resolveLedgerSection: an ARTIFACT/STATE_KEY value can be JSON-shaped
    // (JsonContextValue), and its field order is not stable across re-serializations of
    // semantically identical content. Fingerprinting non-canonical text would make compact-sibling
    // staleness and regrant change-detection spuriously flip on incidental key reordering.
    return CanonicalJson.render(contextRenderer.renderSingleValue(value.get()), objectMapper);
  }

  private static String resolveStepOutput(String stepId, WorkflowState state) {
    return state.getStepOutput(stepId).orElseThrow(() -> new IllegalStateException(
        "No output recorded for step '%s'".formatted(stepId)));
  }
}
