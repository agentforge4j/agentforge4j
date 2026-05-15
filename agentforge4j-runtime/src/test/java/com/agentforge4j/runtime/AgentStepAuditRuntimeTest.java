package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.exception.UserPromptLimitExceededException;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runtime-level checks for LLM audit events, input vs approval taxonomy, and user-prompt caps.
 * LLM is mocked; this is a fast unit-style suite, not an HTTP integration test.
 */
class AgentStepAuditRuntimeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void llmOutput_and_awaitingInput_events_on_user_prompt_pause() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Hello?\",\"responseRequired\":true}]");

    Fixture f = fixture(client, agent("a1", List.of("USER_PROMPT", "SET_CONTEXT", "COMPLETE")), 8);

    String runId = f.runtime().start("wf1");
    WorkflowState state = f.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    List<WorkflowEvent> events = f.eventLog().getEvents(runId);
    assertThat(events.stream().anyMatch(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)).isTrue();
    assertThat(events.stream().anyMatch(e -> e.eventType() == WorkflowEventType.AWAITING_INPUT)).isTrue();
    assertThat(events.stream().noneMatch(e -> e.eventType() == WorkflowEventType.AWAITING_APPROVAL)).isTrue();

    WorkflowEvent llm = events.stream()
      .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
      .findFirst()
      .orElseThrow();
    assertThat(llm.payload()).contains("USER_PROMPT");
  }

  @Test
  void parse_retry_emits_two_llm_output_events() {
    String bad = "[{\"type\":\"CREATE_FILE\",\"path\":\"p\",\"content\":\"c\"}]";
    String good = "[{\"type\":\"COMPLETE\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(bad.strip(), good);

    Fixture f = fixture(client, agent("a1", List.of("COMPLETE")), 8);

    String runId = f.runtime().start("wf1");
    assertThat(f.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

    long llmOut = f.eventLog().getEvents(runId).stream()
      .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
      .count();
    assertThat(llmOut).isEqualTo(2);
  }

  @Test
  void userPromptLimit_emits_event_and_fails_without_step_output() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q1\",\"responseRequired\":true}]",
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q2\",\"responseRequired\":true}]");

    Fixture f = fixture(client, agent("a1", List.of("USER_PROMPT", "COMPLETE")), 2);

    String runId = f.runtime().start("wf1");
    assertThat(f.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    f.runtime().submitInput(runId, Map.of("response", "a1"));
    assertThat(f.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    f.runtime().submitInput(runId, Map.of("response", "a2"));

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getStepOutput("s1")).isEmpty();

    assertThat(f.eventLog().getEvents(runId).stream()
                 .anyMatch(e -> e.eventType() == WorkflowEventType.USER_PROMPT_LIMIT_REACHED))
      .isTrue();
  }

  @Test
  void runFailed_event_contains_supportId_and_no_stack_trace() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q1\",\"responseRequired\":true}]",
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q2\",\"responseRequired\":true}]");

    Fixture f = fixture(client, agent("a1", List.of("USER_PROMPT", "COMPLETE")), 2);

    String runId = f.runtime().start("wf1");
    f.runtime().submitInput(runId, Map.of("response", "a1"));
    f.runtime().submitInput(runId, Map.of("response", "a2"));

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    String supportId = state.getSupportId();
    assertThat(supportId).isNotBlank();

    WorkflowEvent runFailed = f.eventLog().getEvents(runId).stream()
        .filter(e -> e.eventType() == WorkflowEventType.RUN_FAILED)
        .findFirst()
        .orElseThrow();
    assertThat(runFailed.payload())
        .as("RUN_FAILED payload should carry supportId and a sanitised reason, not a stack trace")
        .contains("supportId=" + supportId)
        .contains("reason=")
        .doesNotContain("\n\tat ")
        .doesNotContain("Exception:")
        .doesNotContain(UserPromptLimitExceededException.class.getName());
  }

  @Test
  void promptAnswer_path_opens_runContextManager_scope() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q\",\"responseRequired\":true}]",
      """
        [{"type":"SET_CONTEXT","key":"out","value":{"type":"STRING","value":"x"}},
        {"type":"COMPLETE"}]
        """.strip());

    RecordingRunContextManager runContextManager = new RecordingRunContextManager();
    Fixture f = fixture(
        client,
        agent("a1", List.of("USER_PROMPT", "SET_CONTEXT", "COMPLETE")),
        8,
        runContextManager);

    String runId = f.runtime().start("wf1");
    int scopesAfterStart = runContextManager.opens.size();
    int closesAfterStart = runContextManager.closeCount.get();
    f.runtime().submitInput(runId, Map.of("response", "ok"));

    assertThat(runContextManager.opens.size())
        .as("submitInput on the prompt-answer path must open a RunContextManager scope")
        .isGreaterThan(scopesAfterStart);
    assertThat(runContextManager.closeCount.get())
        .as("the prompt-answer scope must be closed after submitInput returns")
        .isGreaterThan(closesAfterStart);
    RecordedScope last = runContextManager.opens.get(runContextManager.opens.size() - 1);
    assertThat(last.runId).isEqualTo(runId);
    assertThat(last.workflowId).isEqualTo("wf1");
    assertThat(last.stepId).isEqualTo("s1");
  }

  @Test
  void userPromptCounter_resets_after_step_completes() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q\",\"responseRequired\":true}]",
      """
        [{"type":"SET_CONTEXT","key":"out","value":{"type":"STRING","value":"x"}},
        {"type":"COMPLETE"}]
        """.strip());

    Fixture f = fixture(client, agent("a1", List.of("USER_PROMPT", "SET_CONTEXT", "COMPLETE")), 8);

    String runId = f.runtime().start("wf1");
    f.runtime().submitInput(runId, Map.of("response", "ok"));
    WorkflowState state = f.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getUserPromptPauseCountForStep("s1")).isZero();
  }

  @Test
  void event_log_is_append_only_across_turns() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(
      "[{\"type\":\"USER_PROMPT\",\"message\":\"Q\",\"responseRequired\":true}]",
      """
        [{"type":"SET_CONTEXT","key":"out","value":{"type":"STRING","value":"x"}},
        {"type":"COMPLETE"}]
        """.strip());

    Fixture f = fixture(client, agent("a1", List.of("USER_PROMPT", "SET_CONTEXT", "COMPLETE")), 8);

    String runId = f.runtime().start("wf1");
    int afterFirst = f.eventLog().getEvents(runId).size();
    f.runtime().submitInput(runId, Map.of("response", "ok"));
    int afterSecond = f.eventLog().getEvents(runId).size();
    assertThat(afterSecond).isGreaterThan(afterFirst);
  }

  private static AgentDefinition agent(String id, List<String> commands) {
    return new AgentDefinition(
      id,
      "A",
      AgentLocality.CLOUD,
      true,
      "sys",
      List.of(new ProviderPreference("openai", "gpt-4o-mini")),
      commands,
      null,
      null,
      "1.0.0");
  }

  private static Fixture fixture(LlmClient client, AgentDefinition agentDef, int maxPromptRounds) {
    return fixture(client, agentDef, maxPromptRounds, null);
  }

  private static Fixture fixture(LlmClient client,
      AgentDefinition agentDef,
      int maxPromptRounds,
      RunContextManager runContextManager) {
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);

    AgentRepository agentRepository = mock(AgentRepository.class);
    when(agentRepository.get(agentDef.id())).thenReturn(agentDef);

    StepDefinition step = new StepDefinition(
      "s1",
      "S",
      new AgentBehaviour(agentDef.id(), StepTransition.AUTO, null),
      new ContextMapping(List.of(), List.of("out")),
      null,
      maxPromptRounds);
    WorkflowDefinition wf = new WorkflowDefinition(
      "wf1",
      "W",
      null,
      null,
      null,
      null,
      null,
      WorkflowSource.CUSTOM,
      WorkflowLifecycle.ACTIVE,
      Map.of(),
      Map.of(),
      List.of(step));

    InMemoryWorkflowRepository workflowRepository = new InMemoryWorkflowRepository(Map.of("wf1", wf));
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder()
      .workflowRepository(workflowRepository)
      .agentRepository(agentRepository)
      .workflowStateRepository(stateRepository)
      .workflowEventLog(eventLog)
      .llmClientResolver(resolver)
      .objectMapper(MAPPER)
      .clock(clock)
      .integrationRegistry(NoOpIntegrationRegistry.INSTANCE)
      .fileSink(FileSink.NO_OP_FILE_SINK)
      .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER);
    if (runContextManager != null) {
      builder = builder.runContextManager(runContextManager);
    }
    WorkflowRuntime runtime = builder.build();

    return new Fixture(runtime, eventLog, stateRepository);
  }

  private record Fixture(
    WorkflowRuntime runtime,
    WorkflowEventLog eventLog,
    WorkflowStateRepository stateRepository) {
  }

  private record RecordedScope(String runId, String workflowId, String stepId, String agentId) {
  }

  private static final class RecordingRunContextManager implements RunContextManager {
    final List<RecordedScope> opens = new ArrayList<>();
    final AtomicInteger closeCount = new AtomicInteger();

    @Override
    public Scope open(String runId, String workflowId, String stepId, String agentId) {
      opens.add(new RecordedScope(runId, workflowId, stepId, agentId));
      return closeCount::incrementAndGet;
    }
  }
}
