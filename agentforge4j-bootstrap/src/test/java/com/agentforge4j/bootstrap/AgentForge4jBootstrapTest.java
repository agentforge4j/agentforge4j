package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.LocalFileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
  private WorkflowRepository workflowRepository;

  @Mock
  private WorkflowStateRepository workflowStateRepository;

  @Mock
  private WorkflowEventLog workflowEventLog;

  @Mock
  private LlmClientResolver llmClientResolver;

  @Mock
  private FileSink fileSink;

  @Mock
  private ContextRenderer contextRenderer;

  @Mock
  private LlmCommandParser llmCommandParser;

  @Mock
  private EventRecorder eventRecorder;

  @Mock
  private LlmProviderSelectionStrategy llmProviderSelectionStrategy;

  @Mock
  private AgentInvoker agentInvoker;

  @Mock
  private LlmCallObserver llmCallObserver;

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
  void customClockAppliesInstanceToComponents() {
    Clock fixedClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withClock(fixedClock)
        .build();
    assertThat(af.components().clock()).isSameAs(fixedClock);
  }

  @Test
  void withClockNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withClock(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withRunExecutionInterceptorBuildsSuccessfully() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withRunExecutionInterceptor(RunExecutionInterceptor.NO_OP)
        .build();
    assertThat(af.runtime()).isNotNull();
  }

  @Test
  void withRunExecutionInterceptorNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(
        () -> AgentForge4jBootstrap.defaults().withRunExecutionInterceptor(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customObjectMapperAppliesInstanceToComponents() {
    ObjectMapper customMapper = new ObjectMapper();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withObjectMapper(customMapper)
        .build();
    assertThat(af.components().objectMapper()).isSameAs(customMapper);
  }

  @Test
  void withObjectMapperNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withObjectMapper(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customAgentRepositoryAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentRepository(agentRepository)
        .build();
    assertThat(af.components().agentRepository()).isSameAs(agentRepository);
  }

  @Test
  void withAgentRepositoryNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withAgentRepository(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customWorkflowRepositoryAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withWorkflowRepository(workflowRepository)
        .build();
    assertThat(af.components().workflowRepository()).isSameAs(workflowRepository);
  }

  @Test
  void withWorkflowRepositoryNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withWorkflowRepository(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customWorkflowStateRepositoryAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withWorkflowStateRepository(workflowStateRepository)
        .build();
    assertThat(af.components().workflowStateRepository()).isSameAs(workflowStateRepository);
  }

  @Test
  void withWorkflowStateRepositoryNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withWorkflowStateRepository(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customWorkflowEventLogAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withWorkflowEventLog(workflowEventLog)
        .build();
    assertThat(af.components().workflowEventLog()).isSameAs(workflowEventLog);
  }

  @Test
  void withWorkflowEventLogNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withWorkflowEventLog(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customLlmClientResolverAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(llmClientResolver)
        .build();
    assertThat(af.components().llmClientResolver()).isSameAs(llmClientResolver);
  }

  @Test
  void withLlmClientResolverNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmClientResolver(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withLlmRetryPolicyWrapsResolverWhenMaxAttemptsAboveOne() {
    LlmRetryPolicy policy = new LlmRetryPolicy(3, 200, 10_000, 30_000);
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmRetryPolicy(policy)
        .build();
    assertThat(af.components().llmClientResolver()).isInstanceOf(RetryingLlmClientResolver.class);
  }

  @Test
  void withLlmRetryPolicyNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmRetryPolicy(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customContextRendererAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withContextRenderer(contextRenderer)
        .build();
    assertThat(af.components().contextRenderer()).isSameAs(contextRenderer);
  }

  @Test
  void withContextRendererNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withContextRenderer(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customLlmCommandParserAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmCommandParser(llmCommandParser)
        .build();
    assertThat(af.components().llmCommandParser()).isSameAs(llmCommandParser);
  }

  @Test
  void withLlmCommandParserNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmCommandParser(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customEventRecorderAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withEventRecorder(eventRecorder)
        .build();
    assertThat(af.components().eventRecorder()).isSameAs(eventRecorder);
  }

  @Test
  void withEventRecorderNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withEventRecorder(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customFileSinkAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withFileSink(fileSink)
        .build();
    assertThat(af.components().fileSink()).isSameAs(fileSink);
  }

  @Test
  void withFileSinkNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withFileSink(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withFileSinkPathAppliesLocalFileSinkToComponents(@TempDir Path sinkDir) {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withFileSinkPath(sinkDir)
        .build();
    assertThat(af.components().fileSink()).isInstanceOf(LocalFileSink.class);
  }

  @Test
  void withFileSinkPathNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withFileSinkPath(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customLlmProviderSelectionStrategyAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmProviderSelectionStrategy(llmProviderSelectionStrategy)
        .build();
    assertThat(af.components().llmProviderSelectionStrategy()).isSameAs(llmProviderSelectionStrategy);
  }

  @Test
  void withLlmProviderSelectionStrategyNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmProviderSelectionStrategy(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customAgentInvokerAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentInvoker(agentInvoker)
        .build();
    assertThat(af.components().agentInvoker()).isSameAs(agentInvoker);
  }

  @Test
  void withAgentInvokerNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withAgentInvoker(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customLlmCallObserverAppliesInstanceToComponents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmCallObserver(llmCallObserver)
        .build();
    assertThat(af.components().llmCallObserver()).isSameAs(llmCallObserver);
  }

  @Test
  void withLlmCallObserverNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmCallObserver(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withMaxNestingDepthPositiveValueBuildsRunnableRuntime() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withMaxNestingDepth(3)
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .build();
    assertThat(af.runtime()).isNotNull();
  }

  @Test
  void maxNestingDepthNegativeThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withMaxNestingDepth(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withAgentsDirLoadsFromFilesystemAndBuilds(@TempDir Path agentsDir) {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentsDir(agentsDir)
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .build();
    assertThat(af.components().loadedConfiguration()).isNotNull();
    assertThat(af.agents()).isEmpty();
  }

  @Test
  void withAgentsDirNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withAgentsDir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withWorkflowsDirLoadsFromFilesystemAndBuilds(@TempDir Path workflowsDir) {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(workflowsDir)
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .build();
    assertThat(af.components().loadedConfiguration()).isNotNull();
    assertThat(af.workflows()).isEmpty();
  }

  @Test
  void withWorkflowsDirNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withWorkflowsDir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withLoadShippedAgentsFalseYieldsNoLoadedAgents() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .build();
    assertThat(af.agents()).isEmpty();
  }

  @Test
  void withLoadShippedWorkflowsFalseYieldsNoLoadedWorkflows() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .build();
    assertThat(af.workflows()).isEmpty();
  }

  @Test
  void withCacheEnabledBuildsDefaultAgentInvokerWhenNoneExplicit() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withCacheEnabled(true)
        .build();
    assertThat(af.components().agentInvoker()).isNotNull();
  }

  @Test
  void withCacheEnabledAndExplicitAgentInvokerUsesExplicitInvoker() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withAgentInvoker(agentInvoker)
        .withCacheEnabled(true)
        .build();
    assertThat(af.components().agentInvoker()).isSameAs(agentInvoker);
  }

  @Test
  void withLlmProviderAcceptsMultipleProviderConfigsAndWiresResolver() {
    LlmProviderConfig openai = LlmProviderConfig.openai().defaults().apiKey("sk-openai").build();
    LlmProviderConfig claude = LlmProviderConfig.claude().defaults().apiKey("sk-claude").build();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmProvider(openai)
        .withLlmProvider(claude)
        .build();
    assertThat(af.components().llmClientResolver()).isNotNull();
    assertThat(af.runtime()).isNotNull();
  }

  @Test
  void withLlmProviderLastWriteWinsWithinSameProviderKeyStillBuilds() {
    LlmProviderConfig openaiFirst = LlmProviderConfig.openai().defaults().apiKey("sk-one").build();
    LlmProviderConfig openaiSecond = LlmProviderConfig.openai().defaults().apiKey("sk-two").build();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmProvider(openaiFirst)
        .withLlmProvider(openaiSecond)
        .build();
    assertThat(af.components().llmClientResolver()).isNotNull();
    assertThat(af.runtime()).isNotNull();
  }

  @Test
  void withLlmProviderNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmProvider(null))
        .isInstanceOf(IllegalArgumentException.class);
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
  void startBlankWorkflowIdThrowsHumanReadableMessage() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThatThrownBy(() -> af.start(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workflowId");
  }
}
