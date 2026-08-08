// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.runtime.context.CompactSibling;
import com.agentforge4j.runtime.context.CompactSiblingStore;
import com.agentforge4j.runtime.context.ContextSourceId;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.ledger.LedgerMerger;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the prompt-cache stability guarantee end-to-end for token-governance content
 * specifically: {@link AgentInvoker#assembleSystemPrompt} takes no {@link WorkflowState} or rendered
 * context as input, so no content that compaction, ledger merge, or context expansion writes into
 * {@link WorkflowState} — regardless of how much it varies between invocations — can ever reach the
 * cacheable system-prompt prefix. These tests exercise the real {@link ContextRenderer} (not a mock)
 * so the guarantee is demonstrated on the actual data flow, not just inferred from the method
 * signature. No provider-side cache mapping (Claude/Bedrock) is touched; this only confirms the
 * existing runtime wiring that provider cache mappers depend on.
 */
class TokenGovernancePromptCacheStabilityTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"),
      ZoneOffset.UTC);
  private static final ContextSelector LEDGER_SELECTOR =
      new ContextSelector(ContextSourceKind.LEDGER_SECTION, "requirements", ContextVariant.FULL);

  @Test
  void compactSiblingContentNeverPerturbsSystemPromptOrBoundaries() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(
        "[{\"type\":\"COMPLETE\"}]", null, null));
    AgentInvoker invoker = invoker(mapper, client);
    String sourceId = ContextSourceId.of(LEDGER_SELECTOR);
    ContextMapping mapping = new ContextMapping(
        List.of(ReservedContextKeys.compactKey(sourceId)), List.of());
    WorkflowState state = state("run-compact-cache-stability");

    invoker.invoke("a1", mapping, state, "step prompt");
    CompactSiblingStore.write(state, sourceId,
        new CompactSibling("REQ-1 (compacted)",
            new CompactSiblingMetadata(sourceId, "fp-1", new DeterministicExtract(),
                500, 20, "compact-step", new CompactionPolicy(0, 0))),
        mapper, ContextProvenance.SYSTEM_GENERATED);
    invoker.invoke("a1", mapping, state, "step prompt");

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    LlmExecutionRequest first = captor.getAllValues().get(0);
    LlmExecutionRequest second = captor.getAllValues().get(1);

    assertThat(second.systemPrompt()).isEqualTo(first.systemPrompt());
    assertThat(second.promptLayerBoundaries()).isEqualTo(first.promptLayerBoundaries());
    assertThat(second.userInput()).isNotEqualTo(first.userInput());
    assertThat(second.userInput()).contains("REQ-1 (compacted)");
  }

  @Test
  void ledgerMergeContentNeverPerturbsSystemPromptOrBoundaries() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(
        "[{\"type\":\"COMPLETE\"}]", null, null));
    AgentInvoker invoker = invoker(mapper, client);
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "ledger/requirement-ledger.schema.json", LedgerMergeStrategy.APPEND, null);
    ContextMapping mapping = new ContextMapping(
        List.of(ReservedContextKeys.ledgerKey("requirements")), List.of());
    WorkflowState state = state("run-ledger-cache-stability");

    invoker.invoke("a1", mapping, state, "step prompt");
    JsonNode delta = mapper.readTree(
        "{\"entries\":[{\"id\":\"REQ-1\",\"rationale\":\"because\"}]}"
            .getBytes(StandardCharsets.UTF_8));
    LedgerMerger.writeMerged(state, ledger, LedgerMerger.merge(ledger, null, delta, mapper));
    invoker.invoke("a1", mapping, state, "step prompt");

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    LlmExecutionRequest first = captor.getAllValues().get(0);
    LlmExecutionRequest second = captor.getAllValues().get(1);

    assertThat(second.systemPrompt()).isEqualTo(first.systemPrompt());
    assertThat(second.promptLayerBoundaries()).isEqualTo(first.promptLayerBoundaries());
    assertThat(second.userInput()).isNotEqualTo(first.userInput());
    assertThat(second.userInput()).contains("REQ-1");
  }

  private static AgentInvoker invoker(ObjectMapper mapper, LlmClient client) {
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("a1")).thenReturn(AgentDefinition.builder()
        .withId("a1")
        .withName("A")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("AGENT_STABLE_BODY")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    return AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  private static WorkflowState state(String runId) {
    WorkflowState state = new WorkflowState(runId, "wf-1", null, Instant.parse("2026-01-01T00:00:00Z"));
    state.setCurrentStepId("step-1");
    return state;
  }
}
