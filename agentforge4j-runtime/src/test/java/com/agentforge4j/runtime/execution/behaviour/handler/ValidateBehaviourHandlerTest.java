// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.ContextEqualityContract;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.runtime.GeneratedArtifactStore;
import com.agentforge4j.runtime.InMemoryGeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateBehaviourHandlerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
  private static final String VALIDATOR_ID = "agent-bundle";

  private final InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();

  private static final ArtifactValidator OK_VALIDATOR = validator(VALIDATOR_ID, ctx -> ValidationResult.ok());

  private static ArtifactValidator validator(String id, java.util.function.Function<
      com.agentforge4j.core.spi.validation.ArtifactValidationContext, ValidationResult> fn) {
    return new ArtifactValidator() {
      @Override
      public String validatorId() {
        return id;
      }

      @Override
      public ValidationResult validate(
          com.agentforge4j.core.spi.validation.ArtifactValidationContext context) {
        return fn.apply(context);
      }
    };
  }

  private ValidateBehaviourHandler handler(GeneratedArtifactStore store,
      List<ArtifactValidator> validators) {
    return new ValidateBehaviourHandler(store, validators,
        new EventRecorder(eventLog, CLOCK));
  }

  private static WorkflowState stateWithStep(String stepId) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, CLOCK.instant());
    state.setCurrentStepId(stepId);
    return state;
  }

  private static ExecutionContext context(WorkflowState state, StepDefinition step) {
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf-1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step), List.of());
    return new ExecutionContext(state, wf, 32);
  }

  private static StepDefinition validateStep(ValidateBehaviour behaviour) {
    // Declare each equality-contract context key in inputKeys (enforced by the handler).
    List<String> inputKeys = behaviour.contextEqualityContracts().stream()
        .map(ContextEqualityContract::contextKey)
        .toList();
    return StepDefinition.builder()
        .withStepId("v1")
        .withName("V")
        .withBehaviour(behaviour)
        .withContextMapping(new ContextMapping(inputKeys, List.of()))
        .build();
  }

  private ExecutionOutcome run(GeneratedArtifactStore store, List<ArtifactValidator> validators,
      WorkflowState state, ValidateBehaviour behaviour) {
    // Mirror the runtime's scoped capture: the run's capture union is the VALIDATE step's declared paths.
    state.mergeCapturedArtifactPaths(behaviour.requiredArtifacts());
    StepDefinition step = validateStep(behaviour);
    return handler(store, validators).handle(step, behaviour, context(state, step));
  }

  private String stepFailedAudit() {
    return eventLog.getEvents("run-1").stream()
        .filter(e -> e.eventType() == WorkflowEventType.STEP_FAILED)
        .map(com.agentforge4j.core.workflow.event.WorkflowEvent::payload)
        .reduce("", (a, b) -> a + b);
  }

  @Test
  void passes_when_allowlist_matches_and_validator_ok() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{}");
    store.register("run-1", "gen", "systemprompt.md", "sys");
    ValidateBehaviour behaviour = new ValidateBehaviour(
        VALIDATOR_ID, List.of("agent.json", "systemprompt.md"), List.of());

    ExecutionOutcome outcome = run(store, List.of(OK_VALIDATOR), stateWithStep("v1"), behaviour);

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
  }

  @Test
  void fails_when_required_artifact_missing() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{}");
    ValidateBehaviour behaviour = new ValidateBehaviour(
        VALIDATOR_ID, List.of("agent.json", "systemprompt.md"), List.of());

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), stateWithStep("v1"), behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("required artifacts not captured");
    assertThat(stepFailedAudit()).contains("required artifacts not captured");
  }

  @Test
  void fails_when_unexpected_artifact_present() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{}");
    store.register("run-1", "gen", "rogue.txt", "x");
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"), List.of());

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), stateWithStep("v1"), behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("unexpected artifacts captured");
  }

  @Test
  void fails_when_required_path_is_unsafe() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "../escape.json", "{}");
    ValidateBehaviour behaviour = new ValidateBehaviour(
        VALIDATOR_ID, List.of("../escape.json"), List.of());

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), stateWithStep("v1"), behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("not a safe relative path");
  }

  @Test
  void fails_on_unknown_validator_id() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{}");
    ValidateBehaviour behaviour = new ValidateBehaviour("missing", List.of("agent.json"), List.of());

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), stateWithStep("v1"), behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("no ArtifactValidator registered for validatorId 'missing'");
  }

  @Test
  void fails_when_validator_returns_invalid() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{}");
    ArtifactValidator failing = validator(VALIDATOR_ID, ctx -> ValidationResult.invalid("bad bundle"));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"), List.of());

    assertThatThrownBy(() -> run(store, List.of(failing), stateWithStep("v1"), behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("artifact validation failed")
        .hasMessageContaining("bad bundle");
  }

  @Test
  void equality_contract_passes_on_match() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"modelTier\":\"POWERFUL\"}");
    WorkflowState state = stateWithStep("v1");
    state.putContextValue("recommendedTier",
        new StringContextValue("POWERFUL", ContextProvenance.SYSTEM_GENERATED));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier")));

    assertThat(run(store, List.of(OK_VALIDATOR), state, behaviour))
        .isEqualTo(ExecutionOutcome.COMPLETED);
  }

  @Test
  void equality_contract_fails_on_value_mismatch() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"modelTier\":\"LITE\"}");
    WorkflowState state = stateWithStep("v1");
    state.putContextValue("recommendedTier",
        new StringContextValue("POWERFUL", ContextProvenance.SYSTEM_GENERATED));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier")));

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), state, behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("equality contract violated");
  }

  @Test
  void equality_contract_fails_when_context_value_missing() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"modelTier\":\"POWERFUL\"}");
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier")));

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), stateWithStep("v1"), behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("context key 'recommendedTier' is missing");
  }

  @Test
  void equality_contract_fails_when_pointer_missing() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"name\":\"x\"}");
    WorkflowState state = stateWithStep("v1");
    state.putContextValue("recommendedTier",
        new StringContextValue("POWERFUL", ContextProvenance.SYSTEM_GENERATED));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier")));

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), state, behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void equality_contract_typed_mismatch_string_vs_number_fails() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"count\":5}");
    WorkflowState state = stateWithStep("v1");
    // Context value is a STRING "5"; the artifact value is a NUMBER 5 — typed equality must reject.
    state.putContextValue("count", new StringContextValue("5", ContextProvenance.SYSTEM_GENERATED));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/count", "count")));

    assertThatThrownBy(() -> run(store, List.of(OK_VALIDATOR), state, behaviour))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("type or value mismatch");
  }

  @Test
  void equality_contract_typed_number_match_passes() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"count\":5}");
    WorkflowState state = stateWithStep("v1");
    state.putContextValue("count", new NumberContextValue(5, ContextProvenance.SYSTEM_GENERATED));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/count", "count")));

    assertThat(run(store, List.of(OK_VALIDATOR), state, behaviour))
        .isEqualTo(ExecutionOutcome.COMPLETED);
  }

  @Test
  void equality_contract_context_key_not_declared_in_inputKeys_fails() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "gen", "agent.json", "{\"modelTier\":\"POWERFUL\"}");
    WorkflowState state = stateWithStep("v1");
    state.mergeCapturedArtifactPaths(List.of("agent.json"));
    state.putContextValue("recommendedTier",
        new StringContextValue("POWERFUL", ContextProvenance.SYSTEM_GENERATED));
    ValidateBehaviour behaviour = new ValidateBehaviour(VALIDATOR_ID, List.of("agent.json"),
        List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier")));
    // Step declares NO inputKeys, so the contract's context key is undeclared.
    StepDefinition step = StepDefinition.builder()
        .withStepId("v1").withName("V").withBehaviour(behaviour)
        .withContextMapping(ContextMapping.none())
        .build();

    assertThatThrownBy(() -> handler(store, List.of(OK_VALIDATOR))
        .handle(step, behaviour, context(state, step)))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("not declared in the step's inputKeys");
  }

  @Test
  void validator_sees_only_its_own_step_required_artifacts_not_the_run_union() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    // Two VALIDATE steps with disjoint allowlists both capture into the run.
    store.register("run-1", "gen", "alpha.json", "{\"a\":1}");
    store.register("run-1", "gen", "beta.json", "{\"b\":2}");
    WorkflowState state = stateWithStep("v1");
    // Run-level capture union spans both steps' declared paths.
    state.mergeCapturedArtifactPaths(List.of("alpha.json", "beta.json"));

    List<String> seenByBeta = new java.util.ArrayList<>();
    ArtifactValidator betaValidator = validator("validator-beta", ctx -> {
      seenByBeta.addAll(ctx.artifacts().keySet());
      return ValidationResult.ok();
    });
    // The 'beta' step declares only beta.json, even though alpha.json is also captured in the run.
    ValidateBehaviour betaBehaviour =
        new ValidateBehaviour("validator-beta", List.of("beta.json"), List.of());
    StepDefinition step = validateStep(betaBehaviour);

    ExecutionOutcome outcome =
        handler(store, List.of(betaValidator)).handle(step, betaBehaviour, context(state, step));

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    // The validator sees ONLY its own declared artifact — never the other step's alpha.json.
    assertThat(seenByBeta).containsExactly("beta.json");
  }

  @Test
  void constructor_rejects_duplicate_validator_ids() {
    assertThatThrownBy(() -> handler(new InMemoryGeneratedArtifactStore(),
        List.of(OK_VALIDATOR, validator(VALIDATOR_ID, ctx -> ValidationResult.ok()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate ArtifactValidator");
  }
}
