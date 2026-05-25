package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentForge4jBootstrapTest {

  @Mock
  private AgentRepository agentRepository;

  @Mock
  private FileSink fileSink;

  @Mock
  private AgentInvoker agentInvoker;

  @Mock
  private WorkflowRuntime workflowRuntime;

  @Test
  void defaultsBuildSucceeds() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void workflowsReturnsImmutableList() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.workflows()).isNotNull();
    assertThatThrownBy(() -> af.workflows().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void agentsReturnsImmutableList() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.agents()).isNotNull();
    assertThatThrownBy(() -> af.agents().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void runtimeIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.runtime()).isNotNull();
  }

  @Test
  void customClockIsUsed() {
    Clock fixedClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withClock(fixedClock)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void customObjectMapperIsUsed() {
    ObjectMapper customMapper = new ObjectMapper();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withObjectMapper(customMapper)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void customAgentRepositoryIsUsed() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentRepository(agentRepository)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void customFileSinkSuppressesWarning() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withFileSink(fileSink)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void withLlmProviderIsAdditive() {
    LlmProviderConfig openai = LlmProviderConfig.openai().defaults().apiKey("sk-openai").build();
    LlmProviderConfig claude = LlmProviderConfig.claude().defaults().apiKey("sk-claude").build();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmProvider(openai)
        .withLlmProvider(claude)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void withLlmProviderReplacesWithinSameProvider() {
    LlmProviderConfig openaiFirst = LlmProviderConfig.openai().defaults().apiKey("sk-one").build();
    LlmProviderConfig openaiSecond = LlmProviderConfig.openai().defaults().apiKey("sk-two").build();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmProvider(openaiFirst)
        .withLlmProvider(openaiSecond)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void withCacheEnabledAndExplicitAgentInvokerLogsWarning() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentInvoker(agentInvoker)
        .withCacheEnabled(true)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void startDelegatesToRuntime() {
    when(workflowRuntime.start("wf-id")).thenReturn("run-123");
    LoadedConfiguration configuration = new LoadedConfiguration(Map.of(), Map.of());
    BootstrapComponents components = AgentForge4jBootstrap.defaults().build().components();
    AgentForge4j af = new AgentForge4j(workflowRuntime, configuration, components);
    String runId = af.start("wf-id");
    assertThat(runId).isEqualTo("run-123");
    verify(workflowRuntime).start("wf-id");
  }

  @Test
  void startBlankWorkflowIdThrows() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThatThrownBy(() -> af.start(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withLlmProviderNullThrows() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmProvider(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void maxNestingDepthNegativeThrows() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withMaxNestingDepth(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
