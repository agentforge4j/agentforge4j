// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
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
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runtime-level proof of scoped, last-write-wins generated-artifact capture: capture is gated on the run's
 * {@code VALIDATE}-declared path set, so a workflow with no {@code VALIDATE} step captures nothing (no regression for
 * file-overwriting workflows such as application-delivery), and re-emitting the same captured path under one runId is an
 * upsert rather than a fail-closed duplicate. LLM is mocked.
 */
class GeneratedArtifactCaptureRuntimeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
  private static final String VALIDATOR_ID = "rec";

  @Test
  void no_validate_step_captures_nothing_even_when_a_path_is_emitted_twice() {
    // application-delivery shape in miniature: same path written twice under one runId, no VALIDATE step.
    Fixture f = fixture(new InMemoryGeneratedArtifactStore(),
        "[{\"type\":\"CREATE_FILE\",\"path\":\"src/Foo.java\",\"content\":\"v1\"},"
            + "{\"type\":\"CREATE_FILE\",\"path\":\"src/Foo.java\",\"content\":\"v2\"},"
            + "{\"type\":\"COMPLETE\"}]",
        null, List.of());

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getGeneratedArtifactDescriptors()).isEmpty();
  }

  @Test
  void same_path_emitted_twice_with_downstream_validate_completes_last_write_wins() {
    RecordingValidator validator = new RecordingValidator();
    Fixture f = fixture(new InMemoryGeneratedArtifactStore(),
        "[{\"type\":\"CREATE_FILE\",\"path\":\"agent.json\",\"content\":\"a\"},"
            + "{\"type\":\"CREATE_FILE\",\"path\":\"agent.json\",\"content\":\"b\"},"
            + "{\"type\":\"COMPLETE\"}]",
        List.of("agent.json"), List.of(validator));

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    // The validator saw exactly one entry for the path, with the last-written content.
    assertThat(validator.seen()).containsExactly(Map.entry("agent.json", "b"));
    // One descriptor for the path (last write wins), not a duplicate-path failure.
    assertThat(state.getGeneratedArtifactDescriptors()).hasSize(1);
    assertThat(state.getGeneratedArtifactDescriptors().get(0).path()).isEqualTo("agent.json");
  }

  @Test
  void retry_re_drives_emitting_step_and_completes_via_upsert() {
    // First drive fails validation; retry re-drives the generate step, which re-emits the same captured path. The
    // re-emit upserts (no stale duplicate failure) and the second validation passes.
    FailOnceValidator validator = new FailOnceValidator();
    Fixture f = fixture(new InMemoryGeneratedArtifactStore(),
        "[{\"type\":\"CREATE_FILE\",\"path\":\"agent.json\",\"content\":\"a\"},"
            + "{\"type\":\"COMPLETE\"}]",
        List.of("agent.json"), List.of(validator));

    String runId = f.runtime().start("wf1");
    assertThat(f.stateRepository().findById(runId).orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.FAILED);

    f.runtime().retry(runId, "generate", "tester");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getGeneratedArtifactDescriptors()).hasSize(1);
    assertThat(state.getGeneratedArtifactDescriptors().get(0).path()).isEqualTo("agent.json");
  }

  private static Fixture fixture(GeneratedArtifactStore store, String llmCommandsJson,
      List<String> validateRequiredPaths, List<ArtifactValidator> validators) {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(llmCommandsJson, null, null));

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
    List<StepDefinition> steps;
    if (validateRequiredPaths == null) {
      steps = List.of(generate);
    } else {
      StepDefinition validate = StepDefinition.builder()
          .withStepId("validate")
          .withName("Validate")
          .withBehaviour(new ValidateBehaviour(VALIDATOR_ID, validateRequiredPaths, List.of()))
          .withContextMapping(ContextMapping.none())
          .build();
      steps = List.of(generate, validate);
    }
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.copyOf(steps));

    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);
    AgentInvoker agentInvoker = AgentInvoker.builder()
        .agentRepository(agentRepository)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(MAPPER))
        .llmCommandParser(new LlmCommandParser(MAPPER))
        .objectMapper(MAPPER)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, MAPPER))
        .modelTierResolver((provider, tier) -> null)
        .build();

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", wf)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .generatedArtifactStore(store)
        .artifactValidators(validators)
        .build();

    return new Fixture(runtime, eventLog, stateRepository);
  }

  /** Records the artifacts it last saw and always passes. */
  private static final class RecordingValidator implements ArtifactValidator {

    private final AtomicReference<Map<String, String>> seen = new AtomicReference<>(Map.of());

    @Override
    public String validatorId() {
      return VALIDATOR_ID;
    }

    @Override
    public ValidationResult validate(ArtifactValidationContext context) {
      seen.set(Map.copyOf(context.artifacts()));
      return ValidationResult.ok();
    }

    Map<String, String> seen() {
      return seen.get();
    }
  }

  /** Fails the first validation, then passes — to drive a retry. */
  private static final class FailOnceValidator implements ArtifactValidator {

    private boolean failed;

    @Override
    public String validatorId() {
      return VALIDATOR_ID;
    }

    @Override
    public ValidationResult validate(ArtifactValidationContext context) {
      if (!failed) {
        failed = true;
        return ValidationResult.invalid("first attempt rejected");
      }
      return ValidationResult.ok();
    }
  }

  private record Fixture(
      WorkflowRuntime runtime,
      WorkflowEventLog eventLog,
      WorkflowStateRepository stateRepository) {
  }
}
