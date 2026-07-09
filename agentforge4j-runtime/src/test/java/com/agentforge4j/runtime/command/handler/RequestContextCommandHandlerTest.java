// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.context.CompactSibling;
import com.agentforge4j.runtime.context.CompactSiblingStore;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.context.ContextPackRegistry;
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
      int round) {
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1, step,
        workflow(step), round);
  }

  private CommandApplicationRequest request(WorkflowState state, StepDefinition step,
      WorkflowDefinition enclosingWorkflow, int round) {
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1, step,
        enclosingWorkflow, round);
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
  void grantsRequestInExpandableScopeAndWritesContext() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    state.removeContextValue("design.md");
    state.putContextValue("design.md",
        new com.agentforge4j.core.workflow.context.StringContextValue("hello",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 1));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue("design.md")).isPresent();
  }

  @Test
  void grantDoesNotOverwriteAnExistingKeyWithReEncodedContent() {
    // "design.md" already carries a StringContextValue in state() before the grant. A granted
    // context-read request must never mutate an existing key's type/encoding by re-writing it as a
    // JsonContextValue.
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 1));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue("design.md"))
        .get()
        .isInstanceOf(com.agentforge4j.core.workflow.context.StringContextValue.class)
        .isEqualTo(new com.agentforge4j.core.workflow.context.StringContextValue("hello",
            com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED));
  }

  @Test
  void deniesRequestNotInExpandableScope() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 1));

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

    handler.apply(command, request(state, step, 1));

    assertThat(eventLog.getEvents("run-1")).hasSize(1);
    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
  }

  @Test
  void deniesWithMaxExpansionsReachedBeforeCheckingScope() {
    // Selector IS in scope, but the round number already exceeds maxExpansions (default 1): the
    // round limit must be checked before the scope check, so this denies for MAX_EXPANSIONS_REACHED,
    // not NOT_IN_EXPANDABLE_SCOPE.
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(selector("design.md")));

    handler.apply(command, request(state, step, 2));

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

    handler.apply(command, request(state, step, 2));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
  }

  @Test
  void handlesMultipleSelectorsInOneCommandIndependently() {
    ContextSelection selection = new ContextSelection(List.of(), List.of(selector("design.md")),
        null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(
        List.of(selector("design.md"), selector("other-key")));

    handler.apply(command, request(state, step, 1));

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
    CompactSiblingStore.write(state, sourceId, new CompactSibling("compact form", metadata), mapper);
    RequestContextCommand command = new RequestContextCommand(List.of(compactSelector));

    handler.apply(command, request(state, step, workflow, 1));

    assertThat(eventLog.getEvents("run-1").get(0).eventType())
        .isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_GRANTED);
    assertThat(state.getContextValue("requirements"))
        .get()
        .isInstanceOf(com.agentforge4j.core.workflow.context.JsonContextValue.class)
        .extracting(value -> ((com.agentforge4j.core.workflow.context.JsonContextValue) value).json())
        .isEqualTo("compact form");

    // A fresh round against a state whose ledger has since changed (no matching sibling written) must
    // fall back to full content rather than serving the now-stale "compact form" sibling.
    WorkflowState staleState = state();
    LedgerMerger.writeMerged(staleState, ledgerDef,
        mapper.valueToTree(Map.of("entries", List.of(Map.of("id", "REQ-9")))));
    RequestContextCommand staleCommand = new RequestContextCommand(List.of(compactSelector));

    handler.apply(staleCommand, request(staleState, step, workflow, 1));

    assertThat(staleState.getContextValue("requirements"))
        .get()
        .isInstanceOf(com.agentforge4j.core.workflow.context.JsonContextValue.class)
        .extracting(value -> ((com.agentforge4j.core.workflow.context.JsonContextValue) value).json())
        .isNotEqualTo("compact form");
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

    assertThatThrownBy(() -> handler.apply(command, request(state, step, workflow, 1)))
        .isInstanceOf(CompactSiblingUnavailableException.class);
  }

  @Test
  void grantDeniesAndDoesNotWriteAReservedNamespaceSelector() {
    ContextSelector reservedSelector = selector("__ledgerMergeState");
    ContextSelection selection = new ContextSelection(List.of(), List.of(reservedSelector), null);
    StepDefinition step = step(selection);
    WorkflowState state = state();
    RequestContextCommand command = new RequestContextCommand(List.of(reservedSelector));

    handler.apply(command, request(state, step, 1));

    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_EXPANSION_DENIED);
    assertThat(events.get(0).payload()).contains("RESERVED_NAMESPACE");
    assertThat(state.getContextValue("__ledgerMergeState")).isEmpty();
  }
}
