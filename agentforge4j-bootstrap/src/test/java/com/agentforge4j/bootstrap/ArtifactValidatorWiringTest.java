// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bootstrap wiring guard for {@link AgentForge4jBootstrap.Builder#withArtifactValidators(List)}: a supplied validator
 * is resolvable by a {@code VALIDATE} step that selects it by id (the built-in {@code agent-bundle} validator stays
 * present alongside it). Without registering it, the same step fails closed with the unresolved-validator message.
 */
class ArtifactValidatorWiringTest {

  private static final String CUSTOM_ID = "custom-format";

  @Test
  void supplied_validator_is_resolvable_by_a_validate_step() {
    RecordingValidator validator = new RecordingValidator();
    Fixture f = fixture(List.of(validator));

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(validator.invoked()).as("supplied validator must be resolved and run").isTrue();
  }

  @Test
  void validate_step_fails_closed_when_its_validator_is_not_registered() {
    Fixture f = fixture(List.of());

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getFailureReason()).contains("no ArtifactValidator registered", CUSTOM_ID);
  }

  private static Fixture fixture(List<ArtifactValidator> validators) {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(
        "[{\"type\":\"CREATE_FILE\",\"path\":\"x.json\",\"content\":\"{}\"},{\"type\":\"COMPLETE\"}]",
        null, null));

    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentDefinition agentDef = AgentDefinition.builder()
        .withId("a1")
        .withName("A")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("CREATE_FILE", "COMPLETE"))
        .withVersion("1.0.0")
        .build();
    AgentRepository agentRepository = mock(AgentRepository.class);
    when(agentRepository.get("a1")).thenReturn(agentDef);

    StepDefinition generate = StepDefinition.builder()
        .withStepId("generate")
        .withName("Generate")
        .withBehaviour(new AgentBehaviour("a1", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
    StepDefinition validate = StepDefinition.builder()
        .withStepId("validate")
        .withName("Validate")
        .withBehaviour(new ValidateBehaviour(CUSTOM_ID, List.of("x.json"), List.of()))
        .withContextMapping(ContextMapping.none())
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(generate, validate));

    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withLlmClientResolver(resolver)
        .withAgentRepository(agentRepository)
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", wf)))
        .withWorkflowStateRepository(stateRepository)
        .withFileSink(FileSink.NO_OP_FILE_SINK)
        .withArtifactValidators(validators)
        .build();

    return new Fixture(af, stateRepository);
  }

  /** Records that it was invoked and always passes. */
  private static final class RecordingValidator implements ArtifactValidator {

    private boolean invoked;

    @Override
    public String validatorId() {
      return CUSTOM_ID;
    }

    @Override
    public ValidationResult validate(ArtifactValidationContext context) {
      invoked = true;
      return ValidationResult.ok();
    }

    boolean invoked() {
      return invoked;
    }
  }

  private record Fixture(AgentForge4j af, WorkflowStateRepository stateRepository) {

    com.agentforge4j.core.runtime.WorkflowRuntime runtime() {
      return af.runtime();
    }
  }
}
