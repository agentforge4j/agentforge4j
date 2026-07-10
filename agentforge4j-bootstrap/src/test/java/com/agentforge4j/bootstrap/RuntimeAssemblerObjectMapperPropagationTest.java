// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Proves the object mapper {@link RuntimeAssembler#runtime} is given is the one actually used for
 * compact-sibling read/write, not {@link com.agentforge4j.runtime.WorkflowRuntimeBuilder}'s own
 * default {@code new ObjectMapper()} (the gap {@code RuntimeAssembler.runtime} previously left open
 * by never calling {@code .objectMapper(...)} on the builder it assembles).
 */
class RuntimeAssemblerObjectMapperPropagationTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"),
      ZoneOffset.UTC);
  private static final ContextSelector LEDGER_SELECTOR =
      new ContextSelector(ContextSourceKind.LEDGER_SECTION, "requirements", ContextVariant.FULL);

  @Test
  void bootstrapResolvedObjectMapperIsUsedForCompactSiblingReadAndWrite() {
    ObjectMapper spyMapper = spy(new ObjectMapper());
    StepDefinition compactStep = StepDefinition.builder()
        .withStepId("compact")
        .withName("Compact")
        .withBehaviour(new CompactBehaviour(LEDGER_SELECTOR, new DeterministicExtract(),
            new CompactionPolicy(0, 0)))
        .build();
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "ledger/requirement-ledger.schema.json", LedgerMergeStrategy.APPEND, null);
    WorkflowDefinition workflow = new WorkflowDefinition("wf1", "W", null, null, null, "1.0.0",
        null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(compactStep), List.of(), List.of(ledger));
    InMemoryWorkflowRepository workflowRepository = new InMemoryWorkflowRepository(
        Map.of("wf1", workflow));
    InMemoryWorkflowStateRepository workflowStateRepository = new InMemoryWorkflowStateRepository();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    WorkflowRuntime runtime = RuntimeAssembler.runtime(
        workflowRepository,
        workflowStateRepository,
        eventLog,
        CLOCK,
        mock(FileSink.class),
        mock(AgentInvoker.class),
        eventRecorder,
        null,
        null,
        null,
        null,
        RunExecutionInterceptor.NO_OP,
        spyMapper,
        List.of());

    String runId = runtime.start("wf1");

    // The write path (CompactSiblingStore.write, a runtime-internal type not reachable from this
    // module) calls createObjectNode()/valueToTree() on the mapper it is given; verifying those
    // calls on spyMapper (not just object equality) proves this exact instance drove
    // serialization, not a builder-internal default.
    verify(spyMapper, atLeastOnce()).createObjectNode();
    WorkflowState state = runtime.getState(runId);
    assertThat(state.getContext().keySet())
        .anyMatch(key -> key.startsWith(ReservedContextKeys.COMPACT_KEY_PREFIX));
  }
}
