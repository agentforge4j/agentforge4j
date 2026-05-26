package com.agentforge4j.starter;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeAutoConfigurationPromptCacheTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(PromptCacheCollaboratorsConfiguration.class)
      .withConfiguration(AutoConfigurations.of(BootstrapAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.load-shipped-agents=false",
          "agentforge4j.load-shipped-workflows=false");

  @Test
  void contextStartsWithDefaultCacheProperties() {
    runner.run(ctx -> {
      assertThat(ctx.getStartupFailure()).isNull();
      assertThat(ctx).hasSingleBean(AgentInvoker.class);
      assertThat(ctx.getBean(AgentForge4j.class).runtime()).isNotNull();
      assertThat(ctx.getBean(LlmCacheSettings.class).enabled()).isFalse();
    });
  }

  @Test
  void agentInvokerOmitsBoundariesByDefault() {
    runner.run(ctx -> {
      LlmExecutionRequest request = invokeAndCaptureRequest(ctx);
      assertThat(request.promptLayerBoundaries()).isNull();
    });
  }

  @Test
  void agentInvokerPopulatesBoundariesWhenCacheEnabled() {
    runner.withPropertyValues("agentforge4j.llm.cache.enabled=true")
        .run(ctx -> {
          assertThat(ctx.getBean(LlmCacheSettings.class).enabled()).isTrue();
          LlmExecutionRequest request = invokeAndCaptureRequest(ctx);
          assertThat(request.promptLayerBoundaries()).isNotNull();
        });
  }

  @Test
  void agentInvokerOmitsBoundariesWhenCacheExplicitlyDisabled() {
    runner.withPropertyValues("agentforge4j.llm.cache.enabled=false")
        .run(ctx -> {
          LlmExecutionRequest request = invokeAndCaptureRequest(ctx);
          assertThat(request.promptLayerBoundaries()).isNull();
        });
  }

  private static LlmExecutionRequest invokeAndCaptureRequest(ConfigurableApplicationContext ctx) {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
        new LlmExecutionResponse("[{\"type\":\"COMPLETE\"}]", null, null));

    AgentRepository agentRepository = ctx.getBean(AgentRepository.class);
    when(agentRepository.get("agent-x")).thenReturn(testAgent());

    LlmClientResolver resolver = ctx.getBean(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    WorkflowState state = new WorkflowState(
        "run-cache-test",
        "wf-1",
        null,
        Instant.parse("2026-01-01T00:00:00Z"));

    ctx.getBean(AgentInvoker.class).invoke("agent-x", ContextMapping.none(), state, "STEP_PROMPT");

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    return captor.getValue();
  }

  private static AgentDefinition testAgent() {
    return new AgentDefinition(
        "a1",
        "A",
        AgentLocality.CLOUD,
        true,
        "agent-body",
        List.of(new ProviderPreference("openai", "gpt-4o-mini")),
        List.of("COMPLETE"),
        null,
        null,
        "1.0.0");
  }

  @Configuration
  static class PromptCacheCollaboratorsConfiguration {

    @Bean
    AgentRepository agentRepository() {
      return mock(AgentRepository.class);
    }

    @Bean
    AgentInvoker agentInvoker(
        AgentForge4j agentForge4j,
        AgentRepository agentRepository,
        LlmClientResolver llmClientResolver,
        ObjectMapper objectMapper,
        LlmCacheSettings cacheSettings) {
      EventRecorder eventRecorder = agentForge4j.components().eventRecorder();
      LlmCallObserver llmCallObserver = new LlmCallObserver(eventRecorder);
      return AgentInvoker.builder()
          .agentRepository(agentRepository)
          .llmClientResolver(llmClientResolver)
          .contextRenderer(new ContextRenderer(objectMapper))
          .llmCommandParser(new LlmCommandParser(objectMapper))
          .objectMapper(objectMapper)
          .eventRecorder(eventRecorder)
          .llmOutputEventCharCap(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP)
          .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
          .promptCacheEnabled(cacheSettings.enabled())
          .llmCallObserver(llmCallObserver)
          .build();
    }

    @Bean
    LlmClientResolver llmClientResolver() {
      LlmClientResolver resolver = mock(LlmClientResolver.class);
      LlmClient client = mock(LlmClient.class);
      when(client.getProviderName()).thenReturn("openai");
      when(client.execute(any())).thenReturn(
          new LlmExecutionResponse("[{\"type\":\"COMPLETE\"}]", null, null));
      when(resolver.resolve("openai")).thenReturn(client);
      when(resolver.isProviderAvailable("openai")).thenReturn(true);
      when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
      return resolver;
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
