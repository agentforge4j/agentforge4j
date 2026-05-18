package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ensures in-memory repository semantics stay live for internal use while {@link WorkflowRuntime}
 * does not hand out that live instance to API consumers.
 */
class WorkflowStateExposureRuntimeTest {

  @Test
  void inMemoryRepository_findByIdReturnsLiveInstance() {
    InMemoryWorkflowStateRepository repo = new InMemoryWorkflowStateRepository();
    Instant t = Instant.parse("2026-05-01T00:00:00Z");
    WorkflowState saved = new WorkflowState("run-1", "wf-1", null, t);
    repo.save(saved);

    WorkflowState found = repo.findById("run-1").orElseThrow();
    assertThat(found).isSameAs(saved);

    found.setStatus(WorkflowStatus.COMPLETED);
    assertThat(repo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void runtime_getStateReturnsIndependentSnapshot() {
    WorkflowStateRepository stateRepo = new InMemoryWorkflowStateRepository();
    Instant t = Instant.parse("2026-05-01T00:00:00Z");
    WorkflowState stored = new WorkflowState("run-1", "wf-1", null, t);
    stored.setStatus(WorkflowStatus.PAUSED);
    stored.setCurrentStepId("step-a");
    stored.setLastUpdatedAt(t);
    stored.putContextValue("k", new StringContextValue("v"));
    stateRepo.save(stored);

    WorkflowRuntime runtime = minimalRuntime(stateRepo);
    WorkflowState view = runtime.getState("run-1");

    assertThat(view).isNotSameAs(stored);
    view.setStatus(WorkflowStatus.COMPLETED);
    view.putContextValue("leak", new StringContextValue("x"));

    assertThat(stored.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(stored.getContext()).doesNotContainKey("leak");
  }

  @Test
  void runtime_getState_contextViewCannotMutateUnderlyingStoredState() {
    WorkflowStateRepository stateRepo = new InMemoryWorkflowStateRepository();
    Instant t = Instant.parse("2026-05-01T00:00:00Z");
    WorkflowState stored = new WorkflowState("run-1", "wf-1", null, t);
    stored.putContextValue("k", new StringContextValue("v"));
    stateRepo.save(stored);

    WorkflowState view = minimalRuntime(stateRepo).getState("run-1");
    assertThatThrownBy(() -> view.getContext().put("bad", new StringContextValue("y")))
        .isInstanceOf(UnsupportedOperationException.class);

    WorkflowState live = stateRepo.findById("run-1").orElseThrow();
    assertThat(live.getContext()).doesNotContainKey("bad");
  }

  private static WorkflowRuntime minimalRuntime(WorkflowStateRepository stateRepository) {
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(resolver.resolve(any())).thenReturn(client);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentRepository agentRepository = mock(AgentRepository.class);

    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Collections.emptyMap()))
        .agentRepository(agentRepository)
        .workflowStateRepository(stateRepository)
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .llmClientResolver(resolver)
        .clock(Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC))
        .integrationRegistry(NoOpIntegrationRegistry.INSTANCE)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();
  }
}
