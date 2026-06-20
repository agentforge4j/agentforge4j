// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.interceptor.ExecutionBlockedException;
import com.agentforge4j.runtime.interceptor.LlmCallContext;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentInvokerInterceptorTest {

  private static final String COMPLETE = "[{\"type\":\"COMPLETE\"}]";
  /**
   * Distinctive, known-length system prompt body so the assembled length can be pinned to it.
   */
  private static final String SYSTEM_PROMPT =
      "SYSTEM-PROMPT-BODY-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  @Test
  void beforeLlmCallFiresOncePerCallWithResolvedContext() {
    LlmClient client = client();
    List<LlmCallContext> captured = new ArrayList<>();
    RunExecutionInterceptor interceptor = new RunExecutionInterceptor() {
      @Override
      public void beforeLlmCall(LlmCallContext context) {
        captured.add(context);
      }
    };

    invoker(client, interceptor).invoke("agent-x", ContextMapping.none(), state("run-1"), null);

    assertThat(captured).hasSize(1);
    LlmCallContext context = captured.get(0);
    assertThat(context.runId()).isEqualTo("run-1");
    assertThat(context.stepId()).isEqualTo("step-1");
    assertThat(context.agentId()).isEqualTo("a1");
    assertThat(context.provider()).isEqualTo("openai");
    assertThat(context.maxOutputTokens()).isNull();            // OSS request sets none today
    // userInput renders empty here, so the length is the assembled system prompt — which wraps
    // SYSTEM_PROMPT and must therefore be at least its body length, proving it carries the real
    // composed prompt (not a placeholder/constant/0).
    assertThat(context.assembledPromptLength()).isGreaterThanOrEqualTo(SYSTEM_PROMPT.length());
    assertThat(context.cachedInputUnknown()).isTrue();
    verify(client).execute(any());                              // call proceeded
  }

  @Test
  void beforeLlmCallFiresOnceEvenWhenTheProviderCallIsRetried() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    // First response is unparseable, so AgentInvoker's internal parse-retry re-calls the provider;
    // the second parses. Two provider calls, but the control hook must fire exactly once.
    when(client.execute(any()))
        .thenReturn(new LlmExecutionResponse("not-a-valid-command", "openai-model", null))
        .thenReturn(new LlmExecutionResponse(COMPLETE, "openai-model", null));
    List<LlmCallContext> captured = new ArrayList<>();
    RunExecutionInterceptor interceptor = new RunExecutionInterceptor() {
      @Override
      public void beforeLlmCall(LlmCallContext context) {
        captured.add(context);
      }
    };

    invoker(client, interceptor).invoke("agent-x", ContextMapping.none(), state("run-retry"), null);

    assertThat(captured).hasSize(1);             // fired once, before the retry loop
    verify(client, times(2)).execute(any());     // provider actually called twice
  }

  @Test
  void blockingBeforeLlmCallHaltsAndSkipsTheProviderCall() {
    LlmClient client = client();
    RunExecutionInterceptor blocker = new RunExecutionInterceptor() {
      @Override
      public void beforeLlmCall(LlmCallContext context) {
        throw new ExecutionBlockedException("budget exhausted");
      }
    };

    assertThatThrownBy(
        () -> invoker(client, blocker).invoke("agent-x", ContextMapping.none(), state("run-2"), null))
        .isInstanceOf(ExecutionBlockedException.class)
        .hasMessageContaining("budget exhausted");
    verify(client, never()).execute(any());                    // blocked before dispatch
  }

  private static LlmClient client() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(COMPLETE, "openai-model", null));
    return client;
  }

  private static AgentInvoker invoker(LlmClient client, RunExecutionInterceptor interceptor) {
    ObjectMapper mapper = new ObjectMapper();
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agent());
    LlmClientResolver clientResolver = mock(LlmClientResolver.class);
    when(clientResolver.resolve("openai")).thenReturn(client);
    when(clientResolver.isProviderAvailable("openai")).thenReturn(true);
    when(clientResolver.listAvailableClients()).thenReturn(List.of("openai"));
    EventRecorder recorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC));
    return AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(clientResolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(false)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver(mock(com.agentforge4j.llm.api.ModelTierResolver.class))
        .runExecutionInterceptor(interceptor)
        .build();
  }

  private static AgentDefinition agent() {
    return AgentDefinition.builder()
        .withId("a1")
        .withName("A")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt(SYSTEM_PROMPT)
        .withProviderPreferences(List.of(new ProviderPreference("openai", "pinned-model")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withAuthor(null)
        .withContact(null)
        .withVersion("1.0.0")
        .withModelTier(null)
        .build();
  }

  private static WorkflowState state(String runId) {
    WorkflowState state = new WorkflowState(runId, "wf-1", null,
        Instant.parse("2026-01-01T00:00:00Z"));
    state.setCurrentStepId("step-1");
    return state;
  }
}
