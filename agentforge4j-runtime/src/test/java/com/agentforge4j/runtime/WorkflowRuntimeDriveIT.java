package com.agentforge4j.runtime;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration-style drive over the real {@link WorkflowRuntimeBuilder} graph: no Mockito, no HTTP,
 * only in-memory repositories and a trivial in-process LLM stub.
 */
class WorkflowRuntimeDriveIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void wiredRuntime_completesSingleAgentStep() {
    AgentDefinition agent = new AgentDefinition(
        "agent1",
        "Agent",
        AgentLocality.CLOUD,
        true,
        "stub system",
        List.of(new ProviderPreference("openai", "gpt-test")),
        List.of("COMPLETE"),
        null,
        null,
        "1.0.0");

    StepDefinition step = new StepDefinition(
        "s1",
        "Step1",
        new AgentBehaviour(agent.id(), StepTransition.AUTO, null),
        new ContextMapping(List.of(), List.of()),
        null,
        8);

    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-it",
        "IT",
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

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .agentRepository(new MapAgentRepository(Map.of(agent.id(), agent)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .llmClientResolver(new SingleClientResolver(new ConstantJsonLlmClient("[{\"type\":\"COMPLETE\"}]")))
        .objectMapper(MAPPER)
        .clock(Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC))
        .integrationRegistry(NoOpIntegrationRegistry.INSTANCE)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  private static final class MapAgentRepository implements AgentRepository {
    private final Map<String, AgentDefinition> agents;

    MapAgentRepository(Map<String, AgentDefinition> agents) {
      this.agents = Map.copyOf(agents);
    }

    @Override
    public AgentDefinition get(String id) {
      AgentDefinition def = agents.get(id);
      if (def == null) {
        throw new IllegalArgumentException("No agent: " + id);
      }
      return def;
    }

    @Override
    public Map<String, AgentDefinition> findAll() {
      return agents;
    }
  }

  private static final class SingleClientResolver implements LlmClientResolver {
    private final LlmClient client;

    SingleClientResolver(LlmClient client) {
      this.client = client;
    }

    @Override
    public LlmClient resolve(String provider) {
      return client;
    }

    @Override
    public boolean isProviderAvailable(String provider) {
      return true;
    }
  }

  private static final class ConstantJsonLlmClient implements LlmClient {
    private final String jsonBody;

    ConstantJsonLlmClient(String jsonBody) {
      this.jsonBody = jsonBody;
    }

    @Override
    public String getProviderName() {
      return "openai";
    }

    @Override
    public String execute(LlmExecutionRequest request) {
      return jsonBody;
    }
  }
}
