// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.contextpack.ContextPackVariant;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.runtime.exception.CompactSiblingUnavailableException;
import com.agentforge4j.runtime.ledger.LedgerMerger;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextSourceResolverTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ContextSourceResolver resolver =
      new ContextSourceResolver(new ContextRenderer(mapper), mapper, ContextPackRegistry.EMPTY);

  private static LedgerDefinition ledger(String id) {
    return new LedgerDefinition(id, "ledger/requirement-ledger.schema.json",
        LedgerMergeStrategy.APPEND, null);
  }

  private static WorkflowDefinition workflow(List<LedgerDefinition> ledgers) {
    StepDefinition step = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(new FailBehaviour("stop")).build();
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        List.of(), ledgers);
  }

  private static WorkflowState state() {
    return new WorkflowState("run-1", "wf", null, Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static ContextSelector selector(ContextSourceKind kind, String ref, ContextVariant variant) {
    return new ContextSelector(kind, ref, variant);
  }

  @Test
  void resolvesWholeLedgerSection() throws Exception {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));
    JsonNode merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":[],"conflicts":[]}""");
    LedgerMerger.writeMerged(state, ledger("requirements"), merged);

    String resolved = resolver.resolveFull(
        selector(ContextSourceKind.LEDGER_SECTION, "requirements", ContextVariant.FULL), state, wf);

    assertThat(mapper.readTree(resolved).get("entries").get(0).get("id").asText())
        .isEqualTo("REQ-1");
  }

  @Test
  void resolvesLedgerSubpath() throws Exception {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));
    JsonNode merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":["q1"]}""");
    LedgerMerger.writeMerged(state, ledger("requirements"), merged);

    String resolved = resolver.resolveFull(
        selector(ContextSourceKind.LEDGER_SECTION, "requirements.openQuestions", ContextVariant.FULL),
        state, wf);

    assertThat(mapper.readTree(resolved).get(0).asText()).isEqualTo("q1");
  }

  @Test
  void ledgerSectionDefaultsToEmptyEnvelopeWhenNoneWritten() {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));

    String resolved = resolver.resolveFull(
        selector(ContextSourceKind.LEDGER_SECTION, "requirements", ContextVariant.FULL), state, wf);

    assertThat(resolved).contains("\"entries\":[]");
  }

  @Test
  void rejectsUnknownLedgerId() {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    assertThatThrownBy(() -> resolver.resolveFull(
        selector(ContextSourceKind.LEDGER_SECTION, "nope", ContextVariant.FULL), state, wf))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnknownLedgerSectionNamingAvailableSections() throws Exception {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));
    JsonNode merged = mapper.readTree("""
        {"entries":[{"id":"REQ-1"}],"openQuestions":[],"conflicts":[]}""");
    LedgerMerger.writeMerged(state, ledger("requirements"), merged);

    assertThatThrownBy(() -> resolver.resolveFull(
        selector(ContextSourceKind.LEDGER_SECTION, "requirements.entires", ContextVariant.FULL),
        state, wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entires")
        .hasMessageContaining("entries");
  }

  @Test
  void resolvesStateKeyAndArtifactViaContextValue() {
    WorkflowState state = state();
    state.putContextValue("design.md", new StringContextValue("hello", ContextProvenance.USER_SUPPLIED));
    WorkflowDefinition wf = workflow(List.of());

    assertThat(resolver.resolveFull(
        selector(ContextSourceKind.STATE_KEY, "design.md", ContextVariant.FULL), state, wf))
        .isEqualTo("\"hello\"");
    assertThat(resolver.resolveFull(
        selector(ContextSourceKind.ARTIFACT, "design.md", ContextVariant.FULL), state, wf))
        .isEqualTo("\"hello\"");
  }

  @Test
  void resolvesStepOutput() {
    WorkflowState state = state();
    state.putStepOutput("s1", "raw output");
    WorkflowDefinition wf = workflow(List.of());

    assertThat(resolver.resolveFull(
        selector(ContextSourceKind.STEP_OUTPUT, "s1", ContextVariant.FULL), state, wf))
        .isEqualTo("raw output");
  }

  @Test
  void contextPackUnknownNameThrows() {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    assertThatThrownBy(() -> resolver.resolveFull(
        selector(ContextSourceKind.CONTEXT_PACK, "any-pack", ContextVariant.FULL), state, wf))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ContextPack pack(String name, boolean withCompactVariant) {
    java.util.Map<String, ContextPackVariant> variants = new java.util.LinkedHashMap<>();
    variants.put("full", new ContextPackVariant("full", "FULL CONTENT", "fp-full"));
    if (withCompactVariant) {
      variants.put("compact", new ContextPackVariant("compact", "COMPACT CONTENT", "fp-compact"));
    }
    return new ContextPack(name, "1.0.0", null, List.of(), variants);
  }

  @Test
  void contextPackFullVariantResolvesDirectly() {
    ContextSourceResolver withPacks = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.of(List.of(pack("coding-standards", true))));
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    String content = withPacks.resolve(
        selector(ContextSourceKind.CONTEXT_PACK, "coding-standards", ContextVariant.FULL), state, wf);

    assertThat(content).isEqualTo("FULL CONTENT");
  }

  @Test
  void contextPackCompactPreferredUsesCompactVariantWhenDeclared() {
    ContextSourceResolver withPacks = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.of(List.of(pack("coding-standards", true))));
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    String content = withPacks.resolve(
        selector(ContextSourceKind.CONTEXT_PACK, "coding-standards", ContextVariant.COMPACT_PREFERRED),
        state, wf);

    assertThat(content).isEqualTo("COMPACT CONTENT");
  }

  @Test
  void contextPackCompactPreferredFallsBackToFullWhenNoCompactVariant() {
    ContextSourceResolver withPacks = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.of(List.of(pack("coding-standards", false))));
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    String content = withPacks.resolve(
        selector(ContextSourceKind.CONTEXT_PACK, "coding-standards", ContextVariant.COMPACT_PREFERRED),
        state, wf);

    assertThat(content).isEqualTo("FULL CONTENT");
  }

  @Test
  void contextPackCompactOnlyFailsClosedWhenNoCompactVariant() {
    ContextSourceResolver withPacks = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.of(List.of(pack("coding-standards", false))));
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    assertThatThrownBy(() -> withPacks.resolve(
        selector(ContextSourceKind.CONTEXT_PACK, "coding-standards", ContextVariant.COMPACT_ONLY),
        state, wf))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void resolveFullOnContextPackIgnoresTheDeclaredVariant() {
    // resolveFull's contract is the full (uncompacted) source: for a pack selector it must return
    // the pack's "full" variant even when the selector declares COMPACT_PREFERRED or COMPACT_ONLY.
    ContextSourceResolver withPacks = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.of(List.of(pack("coding-standards", true))));
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of());

    assertThat(withPacks.resolveFull(
        selector(ContextSourceKind.CONTEXT_PACK, "coding-standards", ContextVariant.COMPACT_PREFERRED),
        state, wf))
        .isEqualTo("FULL CONTENT");
    assertThat(withPacks.resolveFull(
        selector(ContextSourceKind.CONTEXT_PACK, "coding-standards", ContextVariant.COMPACT_ONLY),
        state, wf))
        .isEqualTo("FULL CONTENT");
  }

  @Test
  void compactPreferredUsesFreshSiblingAndFallsBackWhenStale() {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));
    ContextSelector selector = selector(ContextSourceKind.LEDGER_SECTION, "requirements",
        ContextVariant.COMPACT_PREFERRED);
    String fullContent = resolver.resolveFull(selector, state, wf);
    String freshFingerprint = ContextFingerprint.of(fullContent);
    String sourceId = ContextSourceId.of(selector);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId, freshFingerprint,
        new DeterministicExtract(), 100, 10, "compact-step", new CompactionPolicy(0, 0));
    CompactSiblingStore.write(state, sourceId, new CompactSibling("compact form", metadata), mapper);

    assertThat(resolver.resolve(selector, state, wf)).isEqualTo("compact form");

    // Source changes after compaction: the stored fingerprint no longer matches.
    LedgerMerger.writeMerged(state, ledger("requirements"),
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-9")))));
    assertThat(resolver.resolve(selector, state, wf)).isNotEqualTo("compact form");
  }

  @Test
  void compactOnlyFailsClosedWhenSiblingAbsent() {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));
    ContextSelector selector = selector(ContextSourceKind.LEDGER_SECTION, "requirements",
        ContextVariant.COMPACT_ONLY);

    assertThatThrownBy(() -> resolver.resolve(selector, state, wf))
        .isInstanceOf(CompactSiblingUnavailableException.class);
  }

  @Test
  void compactOnlyUsesFreshSibling() {
    WorkflowState state = state();
    WorkflowDefinition wf = workflow(List.of(ledger("requirements")));
    ContextSelector selector = selector(ContextSourceKind.LEDGER_SECTION, "requirements",
        ContextVariant.COMPACT_ONLY);
    String fullContent = resolver.resolveFull(selector, state, wf);
    String sourceId = ContextSourceId.of(selector);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId,
        ContextFingerprint.of(fullContent), new DeterministicExtract(), 100, 10, "compact-step",
        new CompactionPolicy(0, 0));
    CompactSiblingStore.write(state, sourceId, new CompactSibling("compact only", metadata), mapper);

    assertThat(resolver.resolve(selector, state, wf)).isEqualTo("compact only");
  }
}
