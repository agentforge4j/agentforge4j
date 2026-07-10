// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.runtime.ContextPackRegistry;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.context.CompactSibling;
import com.agentforge4j.runtime.context.CompactSiblingStore;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.context.ContextSourceId;
import com.agentforge4j.runtime.context.ContextSourceResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.exception.CompactSiblingUnavailableException;
import com.agentforge4j.runtime.ledger.LedgerMerger;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextCommandHandlerTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"),
      ZoneOffset.UTC);

  private final ObjectMapper mapper = new ObjectMapper();
  private final InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
  private final RequestContextCommandHandler handler = new RequestContextCommandHandler(
      new ContextSourceResolver(new ContextRenderer(mapper), mapper, ContextPackRegistry.EMPTY),
      new EventRecorder(eventLog, CLOCK));

  private static ContextSelector selector(String ref) {
    return new ContextSelector(ContextSourceKind.STATE_KEY, ref, ContextVariant.FULL);
  }

  private static ContextSelector ledgerSelector(String ref, ContextVariant variant) {
    return new ContextSelector(ContextSourceKind.LEDGER_SECTION, ref, variant);
  }

  private static LedgerDefinition ledger(String id) {
    return new LedgerDefinition(id, "ledger/requirement-ledger.schema.json",
        LedgerMergeStrategy.APPEND, null);
  }

  private static StepDefinition step(ContextSelection selection) {
    return StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(new FailBehaviour("stop")).withContextSelection(selection).build();
  }

  private static WorkflowDefinition workflow(StepDefinition step) {
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        List.of(), List.of());
  }

  private static WorkflowDefinition workflowWithLedger(StepDefinition step, LedgerDefinition ledger) {
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        List.of(), List.of(ledger));
  }

  private CommandApplicationRequest request(WorkflowState state, StepDefinition step,
      int priorExpansions) {
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1, step,
        workflow(step), priorExpansions);
  }

  private CommandApplicationRequest request(WorkflowState state, StepDefinition step,
      WorkflowDefinition enclosingWorkflow, int priorExpansions) {
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1, step,
        enclosingWorkflow, priorExpansions);
  }

  private static WorkflowState state() {
    WorkflowState state = new WorkflowState("run-1", "wf", null, Instant.parse(
        "2026-01-01T00:00:00Z"));
    state.setCurrentStepId("s1");
    state.putContextValue("design.md",
        new com.agentforge4j.core.workflow.context.StringContextValue("hello",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));
    return state;
  }

  @Test
  void grantsRequestInExpandableScopeAndWritesTheReservedGrantedKey() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 0));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue(
        ReservedContextKeys.grantedKey(ContextSourceId.of(selector("design.md"))))).isPresent();
  }

  @Test
  void grantNeverTouchesTheSourceKeyItself() {
    // "design.md" carries an author-supplied StringContextValue. A granted context-read request
    // writes its copy under the reserved granted key and must never mutate the source key's value,
    // type, or provenance.
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 0));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue("design.md"))
        .get()
        .isInstanceOf(com.agentforge4j.core.workflow.context.StringContextValue.class)
        .isEqualTo(new com.agentforge4j.core.workflow.context.StringContextValue("hello",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));
  }

  @Test
  void unchangedReRequestServesTheSameValueWithoutAChangeAuditEntry() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        2);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    String grantedKey = ReservedContextKeys.grantedKey(ContextSourceId.of(selector("design.md")));

    handler.apply(new RequestContextCommand(List.of(selector("design.md"))),
        request(state, step, 0));
    var firstValue = state.getContextValue(grantedKey).orElseThrow();
    handler.apply(new RequestContextCommand(List.of(selector("design.md"))),
        request(state, step, 1));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(2);
    assertThat(events).extracting(WorkflowEvent::eventType)
        .containsOnly(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(events.get(1).payload()).doesNotContain("changedSincePriorGrant");
    assertThat(state.getContextValue(grantedKey)).contains(firstValue);
  }

  @Test
  void changedReRequestServesTheFreshValueAndRecordsFingerprints() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        2);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    String grantedKey = ReservedContextKeys.grantedKey(ContextSourceId.of(selector("design.md")));

    handler.apply(new RequestContextCommand(List.of(selector("design.md"))),
        request(state, step, 0));
    String priorContent = ((com.agentforge4j.core.workflow.context.JsonContextValue)
        state.getContextValue(grantedKey).orElseThrow()).json();
    // The source changes between grants: the re-request must serve the CURRENT value, never the
    // previously stored grant.
    state.removeContextValue("design.md");
    state.putContextValue("design.md",
        new com.agentforge4j.core.workflow.context.StringContextValue("hello v2",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));
    handler.apply(new RequestContextCommand(List.of(selector("design.md"))),
        request(state, step, 1));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(2);
    String secondPayload = events.get(1).payload();
    String newContent = ((com.agentforge4j.core.workflow.context.JsonContextValue)
        state.getContextValue(grantedKey).orElseThrow()).json();
    assertThat(newContent).contains("hello v2").isNotEqualTo(priorContent);
    assertThat(secondPayload)
        .contains("changedSincePriorGrant=true")
        .contains("priorFingerprint=" + ContextFingerprint.of(priorContent))
        .contains("newFingerprint=" + ContextFingerprint.of(newContent));
  }

  @Test
  void deniesRequestNotInExpandableScope() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 0));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
    assertThat(events.get(0).payload()).contains("NOT_IN_EXPANDABLE_SCOPE");
  }

  @Test
  void stepWithNoContextSelectionDeniesEveryRequest() {
    StepDefinition step = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(new FailBehaviour("stop")).build();
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 0));

    assertThat(eventLog.getEvents("run-1")).hasSize(1);
    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
  }

  @Test
  void deniesWithMaxExpansionsReachedBeforeCheckingScope() {
    // Selector IS in scope, but one expansion was already consumed earlier in the batch and
    // maxExpansions is the default 1: the expansion limit must be checked before the scope check,
    // so this denies for MAX_EXPANSIONS_REACHED, not NOT_IN_EXPANDABLE_SCOPE.
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 1));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
    assertThat(events.get(0).payload()).contains("MAX_EXPANSIONS_REACHED");
  }

  @Test
  void respectsConfiguredMaxExpansions() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")), 2);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 1));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
  }

  @Test
  void handlesMultipleSelectorsInOneCommandIndependently() {
    // maxExpansions 2 so both selectors are within the expansion limit and the deny below is
    // scope-based, not limit-based.
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        2);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(
        List.of(selector("design.md"), selector("other-key")));

    handler.apply(command, request(state, step, 0));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(2);
    assertThat(events).extracting(WorkflowEvent::eventType)
        .containsExactlyInAnyOrder(WorkflowEventType.CONTEXT_EXPANSION_GRANTED,
            WorkflowEventType.CONTEXT_EXPANSION_DENIED);
  }

  @Test
  void compactPreferredGrantServesFreshSiblingAndFallsBackToFullWhenStale() {
    LedgerDefinition ledgerDef = ledger("requirements");
    ContextSelector compactSelector = ledgerSelector("requirements", ContextVariant.COMPACT_PREFERRED);
    ContextSelection selection = new ContextSelection(List.of(), List.of(compactSelector), null);
    StepDefinition step = step(selection);
    WorkflowDefinition workflow = workflowWithLedger(step, ledgerDef);
    WorkflowState state = state();
    LedgerMerger.writeMerged(state, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-1")))));
    String fullContent = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.EMPTY)
        .resolveFull(compactSelector, state, workflow);
    String sourceId = ContextSourceId.of(compactSelector);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId,
        ContextFingerprint.of(fullContent), new DeterministicExtract(), 100, 10, "compact-step",
        new CompactionPolicy(0, 0));
    CompactSiblingStore.write(state, sourceId, new CompactSibling("compact form", metadata), mapper,
        ContextProvenance.SYSTEM_GENERATED);
    RequestContextCommand command = new RequestContextCommand(List.of(compactSelector));

    handler.apply(command, request(state, step, workflow, 0));

    String grantedKey = ReservedContextKeys.grantedKey(sourceId);
    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue(grantedKey))
        .get()
        .isInstanceOf(com.agentforge4j.core.workflow.context.JsonContextValue.class)
        .extracting(value -> ((com.agentforge4j.core.workflow.context.JsonContextValue) value).json())
        .isEqualTo("compact form");

    // The ledger changes after the first grant, making the stored sibling stale. A re-request in
    // the SAME run state must serve the fresh full content (COMPACT_PREFERRED fallback), replace
    // the stored grant, and record the change with both fingerprints.
    LedgerMerger.writeMerged(state, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-9")))));
    handler.apply(new RequestContextCommand(List.of(compactSelector)),
        request(state, step, workflow, 0));

    String regrantedContent = ((com.agentforge4j.core.workflow.context.JsonContextValue)
        state.getContextValue(grantedKey).orElseThrow()).json();
    assertThat(regrantedContent).contains("REQ-9").isNotEqualTo("compact form");
    assertThat(eventLog.getEvents("run-1").get(1).payload())
        .contains("changedSincePriorGrant=true")
        .contains("priorFingerprint=" + ContextFingerprint.of("compact form"))
        .contains("newFingerprint=" + ContextFingerprint.of(regrantedContent));
  }

  @Test
  void compactOnlyGrantPropagatesCompactSiblingUnavailableExceptionWhenNoFreshSiblingExists() {
    LedgerDefinition ledgerDef = ledger("requirements");
    ContextSelector compactOnlySelector = ledgerSelector("requirements", ContextVariant.COMPACT_ONLY);
    ContextSelection selection = new ContextSelection(List.of(), List.of(compactOnlySelector), null);
    StepDefinition step = step(selection);
    WorkflowDefinition workflow = workflowWithLedger(step, ledgerDef);
    WorkflowState state = state();
    LedgerMerger.writeMerged(state, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-1")))));
    RequestContextCommand command = new RequestContextCommand(List.of(compactOnlySelector));

    assertThatThrownBy(() -> handler.apply(command, request(state, step, workflow, 0)))
        .isInstanceOf(CompactSiblingUnavailableException.class);
  }

  @Test
  void grantHonoursTheDeclaredVariantNotTheRequestedOne() {
    // expandableScope declares the ledger with COMPACT_PREFERRED and a fresh compact sibling
    // exists; the agent requests the same source as FULL. The grant must serve the DECLARED form —
    // the requester cannot widen a compact-form source to its full form.
    LedgerDefinition ledgerDef = ledger("requirements");
    ContextSelector declaredCompact = ledgerSelector("requirements", ContextVariant.COMPACT_PREFERRED);
    ContextSelection selection = new ContextSelection(List.of(), List.of(declaredCompact), null);
    StepDefinition step = step(selection);
    WorkflowDefinition workflow = workflowWithLedger(step, ledgerDef);
    WorkflowState state = state();
    LedgerMerger.writeMerged(state, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-1")))));
    String fullContent = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.EMPTY)
        .resolveFull(declaredCompact, state, workflow);
    String sourceId = ContextSourceId.of(declaredCompact);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId,
        ContextFingerprint.of(fullContent), new DeterministicExtract(), 100, 10, "compact-step",
        new CompactionPolicy(0, 0));
    CompactSiblingStore.write(state, sourceId, new CompactSibling("compact form", metadata), mapper,
        ContextProvenance.SYSTEM_GENERATED);
    ContextSelector requestedFull = ledgerSelector("requirements", ContextVariant.FULL);
    RequestContextCommand command = new RequestContextCommand(List.of(requestedFull));

    handler.apply(command, request(state, step, workflow, 0));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue(ReservedContextKeys.grantedKey(sourceId)))
        .get()
        .extracting(value -> ((com.agentforge4j.core.workflow.context.JsonContextValue) value).json())
        .isEqualTo("compact form");
  }

  @Test
  void grantHonoursACompactOnlyDeclaredVariantEvenWhenFullIsRequested() {
    // Same widening-denial contract as grantHonoursTheDeclaredVariantNotTheRequestedOne, for the
    // stricter COMPACT_ONLY declaration: a requester asking for FULL must still be served the
    // compact sibling, never the full disclosure the author restricted the scope to.
    LedgerDefinition ledgerDef = ledger("requirements");
    ContextSelector declaredCompactOnly = ledgerSelector("requirements", ContextVariant.COMPACT_ONLY);
    ContextSelection selection = new ContextSelection(List.of(), List.of(declaredCompactOnly), null);
    StepDefinition step = step(selection);
    WorkflowDefinition workflow = workflowWithLedger(step, ledgerDef);
    WorkflowState state = state();
    LedgerMerger.writeMerged(state, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-1")))));
    String fullContent = new ContextSourceResolver(new ContextRenderer(mapper), mapper,
        ContextPackRegistry.EMPTY)
        .resolveFull(declaredCompactOnly, state, workflow);
    String sourceId = ContextSourceId.of(declaredCompactOnly);
    CompactSiblingMetadata metadata = new CompactSiblingMetadata(sourceId,
        ContextFingerprint.of(fullContent), new DeterministicExtract(), 100, 10, "compact-step",
        new CompactionPolicy(0, 0));
    CompactSiblingStore.write(state, sourceId, new CompactSibling("compact only form", metadata),
        mapper, ContextProvenance.SYSTEM_GENERATED);
    ContextSelector requestedFull = ledgerSelector("requirements", ContextVariant.FULL);
    RequestContextCommand command = new RequestContextCommand(List.of(requestedFull));

    handler.apply(command, request(state, step, workflow, 0));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    // The recorded variant is the DECLARED one (COMPACT_ONLY), not the requester's FULL ask.
    assertThat(eventLog.getEvents("run-1").get(0).payload()).contains("variant=COMPACT_ONLY");
    assertThat(state.getContextValue(ReservedContextKeys.grantedKey(sourceId)))
        .get()
        .extracting(value -> ((com.agentforge4j.core.workflow.context.JsonContextValue) value).json())
        .isEqualTo("compact only form");
  }

  @Test
  void grantedStepOutputIsStoredAsStringContent() {
    // A step's raw output is arbitrary text, not JSON — storing it as a JSON context value would
    // hand downstream JSON consumers unparseable content.
    ContextSelector stepOutput = new ContextSelector(ContextSourceKind.STEP_OUTPUT, "s0",
        ContextVariant.FULL);
    ContextSelection selection = new ContextSelection(List.of(), List.of(stepOutput), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    state.putStepOutput("s0", "plain text, not JSON");
    RequestContextCommand command = new RequestContextCommand(List.of(stepOutput));

    handler.apply(command, request(state, step, 0));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue(ReservedContextKeys.grantedKey(ContextSourceId.of(stepOutput))))
        .get()
        .isInstanceOf(com.agentforge4j.core.workflow.context.StringContextValue.class)
        .extracting(
            value -> ((com.agentforge4j.core.workflow.context.StringContextValue) value).value())
        .isEqualTo("plain text, not JSON");
  }

  @Test
  void grantedStateKeyContentCopiesTheSourceValuesOwnProvenanceForward() {
    // "design.md" is USER_SUPPLIED (untrusted). Granting it must copy that provenance forward, never
    // elevate it to SYSTEM_GENERATED (trusted) — that would let a grant launder untrusted content past
    // the untrusted-input envelope.
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 0));

    assertThat(state.getContextValue(
        ReservedContextKeys.grantedKey(ContextSourceId.of(selector("design.md")))))
        .get()
        .extracting(com.agentforge4j.core.workflow.context.ContextValue::provenance)
        .isEqualTo(com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED);
  }

  @Test
  void grantedStepOutputContentIsStampedLlmGenerated() {
    // A step's raw output is the step's LLM response text (AgentBehaviourHandler/SparBehaviourHandler
    // capture it as such) — a grant must never stamp it SYSTEM_GENERATED, which would launder
    // LLM-authored content into the framework-owned label the design reserves for deterministic,
    // non-LLM content.
    ContextSelector stepOutput = new ContextSelector(ContextSourceKind.STEP_OUTPUT, "s0",
        ContextVariant.FULL);
    ContextSelection selection = new ContextSelection(List.of(), List.of(stepOutput), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    state.putStepOutput("s0", "raw agent response");
    RequestContextCommand command = new RequestContextCommand(List.of(stepOutput));

    handler.apply(command, request(state, step, 0));

    assertThat(state.getContextValue(ReservedContextKeys.grantedKey(ContextSourceId.of(stepOutput))))
        .get()
        .extracting(com.agentforge4j.core.workflow.context.ContextValue::provenance)
        .isEqualTo(com.agentforge4j.core.workflow.context.ContextProvenance.LLM_GENERATED);
  }

  @Test
  void grantedLedgerSectionContentIsStampedSystemGenerated() {
    // Ledger content is produced only by LedgerMerger's deterministic, non-LLM merge — a grant of it
    // is framework-owned content, correctly SYSTEM_GENERATED.
    LedgerDefinition ledgerDef = ledger("requirements");
    ContextSelector ledgerSelector = ledgerSelector("requirements", ContextVariant.FULL);
    ContextSelection selection = new ContextSelection(List.of(), List.of(ledgerSelector), null);
    StepDefinition step = step(selection);
    WorkflowDefinition workflow = workflowWithLedger(step, ledgerDef);
    WorkflowState state = state();
    LedgerMerger.writeMerged(state, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-1")))));
    RequestContextCommand command = new RequestContextCommand(List.of(ledgerSelector));

    handler.apply(command, request(state, step, workflow, 0));

    assertThat(state.getContextValue(ReservedContextKeys.grantedKey(ContextSourceId.of(ledgerSelector))))
        .get()
        .extracting(com.agentforge4j.core.workflow.context.ContextValue::provenance)
        .isEqualTo(com.agentforge4j.core.workflow.context.ContextProvenance.SYSTEM_GENERATED);
  }

  @Test
  void selectorsBeyondMaxExpansionsInOneCommandAreDeniedNotGranted() {
    // maxExpansions counts requested SELECTORS, not commands: with the default limit of 1, packing
    // two in-scope selectors into a single command must grant only the first and deny the second
    // for MAX_EXPANSIONS_REACHED — batching cannot evade the limit.
    ContextSelection selection = new ContextSelection(List.of(),
        List.of(selector("design.md"), selector("other.md")), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    state.putContextValue("other.md",
        new com.agentforge4j.core.workflow.context.StringContextValue("more",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));
    RequestContextCommand command = new RequestContextCommand(
        List.of(selector("design.md"), selector("other.md")));

    handler.apply(command, request(state, step, 0));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(2);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(events.get(1).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
    assertThat(events.get(1).payload()).contains("MAX_EXPANSIONS_REACHED").contains("expansion=2");
    assertThat(state.getContextValue(
        ReservedContextKeys.grantedKey(ContextSourceId.of(selector("design.md"))))).isPresent();
    assertThat(state.getContextValue(
        ReservedContextKeys.grantedKey(ContextSourceId.of(selector("other.md"))))).isEmpty();
  }

  @Test
  void blankGrantedContentFailsWithASelectorNamingError() {
    // ContextPackVariant explicitly permits empty content; a grant must not bury that in a generic
    // value-invariant error — it fails with a message naming the selector so the author can find
    // the empty variant file.
    com.agentforge4j.core.spi.contextpack.ContextPack blankPack =
        new com.agentforge4j.core.spi.contextpack.ContextPack("empty-pack", "1.0.0", null, null,
            Map.of("full", new com.agentforge4j.core.spi.contextpack.ContextPackVariant("full", " ",
                ContextFingerprint.of(" "))));
    RequestContextCommandHandler packHandler = new RequestContextCommandHandler(
        new ContextSourceResolver(new ContextRenderer(mapper), mapper,
            ContextPackRegistry.of(List.of(blankPack))),
        new EventRecorder(eventLog, CLOCK));
    ContextSelector packSelector = new ContextSelector(ContextSourceKind.CONTEXT_PACK, "empty-pack",
        ContextVariant.FULL);
    ContextSelection selection = new ContextSelection(List.of(), List.of(packSelector), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(packSelector));

    assertThatThrownBy(() -> packHandler.apply(command, request(state, step, 0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CONTEXT_PACK:empty-pack")
        .hasMessageContaining("blank content");
    assertThat(state.getContextValue(
        ReservedContextKeys.grantedKey(ContextSourceId.of(packSelector)))).isEmpty();
  }

  @Test
  void grantDeniesAndDoesNotWriteAReservedNamespaceSelector() {
    ContextSelector reservedSelector = selector("__ledgerMergeState");
    ContextSelection selection = new ContextSelection(List.of(), List.of(reservedSelector), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(reservedSelector));

    handler.apply(command, request(state, step, 0));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
    assertThat(events.get(0).payload()).contains("RESERVED_NAMESPACE");
    assertThat(state.getContextValue("__ledgerMergeState")).isEmpty();
  }
}
