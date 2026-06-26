// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.ContextEqualityContract;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.runtime.GeneratedArtifact;
import com.agentforge4j.runtime.GeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministically validates the artifacts captured for the run, then fails the run closed on any violation. Applies
 * the generic rules itself — the {@code requiredArtifacts} allowlist (exactly those paths present, no unexpected
 * files), relative path safety, and the context-equality contracts — and delegates format-specific validation to the
 * {@link ArtifactValidator} named by the behaviour's {@code validatorId}. A failure records a {@code STEP_FAILED} audit
 * event and throws {@link StepExecutionException}; success returns {@link ExecutionOutcome#COMPLETED}.
 */
public final class ValidateBehaviourHandler implements BehaviourHandler<ValidateBehaviour> {

  private static final System.Logger LOG = System.getLogger(ValidateBehaviourHandler.class.getName());

  private final GeneratedArtifactStore generatedArtifactStore;
  private final Map<String, ArtifactValidator> validatorsById;
  private final ObjectMapper objectMapper;
  private final EventRecorder eventRecorder;

  /**
   * Creates a handler over the run-scoped store and the registered validators.
   *
   * @param generatedArtifactStore source of the run's captured artifacts
   * @param validators             validators registered by their {@code validatorId} (no duplicates)
   * @param eventRecorder          audit sink for the partial-validation failure event
   */
  public ValidateBehaviourHandler(GeneratedArtifactStore generatedArtifactStore,
      List<ArtifactValidator> validators, EventRecorder eventRecorder) {
    this.generatedArtifactStore = Validate.notNull(generatedArtifactStore, "generatedArtifactStore must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.objectMapper = new ObjectMapper();
    this.validatorsById = indexValidators(Validate.notNull(validators, "validators must not be null"));
  }

  @Override
  public Class<ValidateBehaviour> behaviourType() {
    return ValidateBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step, ValidateBehaviour behaviour,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    String runId = state.getRunId();
    LOG.log(System.Logger.Level.DEBUG, "Validate behaviour start stepId={0}, validatorId={1}",
        step.stepId(), behaviour.validatorId());

    Map<String, String> captured = capturedArtifacts(runId);
    checkAllowlist(step, state, behaviour, captured);
    checkPathSafety(step, state, behaviour);
    checkEqualityContracts(step, state, behaviour, captured);
    runValidator(step, state, behaviour, captured);

    return ExecutionOutcome.COMPLETED;
  }

  private Map<String, String> capturedArtifacts(String runId) {
    Map<String, String> byPath = new LinkedHashMap<>();
    for (GeneratedArtifact artifact : generatedArtifactStore.artifacts(runId)) {
      byPath.put(artifact.path(), artifact.content());
    }
    return byPath;
  }

  private void checkAllowlist(StepDefinition step, WorkflowState state, ValidateBehaviour behaviour,
      Map<String, String> captured) {
    Set<String> required = new LinkedHashSet<>(behaviour.requiredArtifacts());
    Set<String> present = captured.keySet();
    Set<String> missing = new LinkedHashSet<>(required);
    missing.removeAll(present);
    if (!missing.isEmpty()) {
      throw fail(step, state, "required artifacts not captured: %s".formatted(missing));
    }
    // Compare captured paths against the run-level union of every VALIDATE step's declared paths, not just
    // this step's requiredArtifacts, so multiple VALIDATE steps with disjoint allowlists do not cross-flag
    // each other's paths. Capture is scoped to that union, so a captured path is always a member — this
    // stays as a defensive assertion of that invariant.
    Set<String> unexpected = new LinkedHashSet<>(present);
    unexpected.removeAll(state.getCapturedArtifactPaths());
    if (!unexpected.isEmpty()) {
      throw fail(step, state,
          "unexpected artifacts captured (not in any VALIDATE allowlist): %s".formatted(unexpected));
    }
  }

  private void checkPathSafety(StepDefinition step, WorkflowState state, ValidateBehaviour behaviour) {
    for (String path : behaviour.requiredArtifacts()) {
      if (!isSafeRelativePath(path)) {
        throw fail(step, state, "artifact path is not a safe relative path: '%s'".formatted(path));
      }
    }
  }

  private void checkEqualityContracts(StepDefinition step, WorkflowState state,
      ValidateBehaviour behaviour, Map<String, String> captured) {
    Set<String> declaredInputKeys = declaredInputKeys(step);
    for (ContextEqualityContract contract : behaviour.contextEqualityContracts()) {
      // The expected value must be exposed to the step explicitly via inputKeys (U4); a contract may
      // not read an undeclared context key.
      if (!declaredInputKeys.contains(contract.contextKey())) {
        throw fail(step, state,
            "equality contract context key '%s' is not declared in the step's inputKeys"
                .formatted(contract.contextKey()));
      }
      String content = captured.get(contract.artifactPath());
      if (content == null) {
        throw fail(step, state, "equality contract references missing artifact '%s'"
            .formatted(contract.artifactPath()));
      }
      JsonNode target = resolvePointer(step, state, contract, content);
      if (target.isMissingNode()) {
        throw fail(step, state, "equality contract pointer '%s' not found in '%s'"
            .formatted(contract.jsonPointer(), contract.artifactPath()));
      }
      ContextValue contextValue = requiredContextValue(step, state, contract.contextKey());
      if (!valuesMatch(target, contextValue)) {
        throw fail(step, state,
            "equality contract violated at %s%s for context key '%s' (type or value mismatch)"
                .formatted(contract.artifactPath(), contract.jsonPointer(), contract.contextKey()));
      }
    }
  }

  private static Set<String> declaredInputKeys(StepDefinition step) {
    ContextMapping mapping = step.contextMapping();
    return mapping == null ? Set.of() : Set.copyOf(mapping.inputKeys());
  }

  /**
   * Type-aware equality: the artifact's JSON value node must match both the type and value of the context value
   * (string↔textual, number↔number, boolean↔boolean). A JSON/LIST context value, or any type mismatch, never matches —
   * so a stringified number can never equal a numeric context value.
   */
  private static boolean valuesMatch(JsonNode artifactValue, ContextValue contextValue) {
    if (contextValue instanceof StringContextValue string) {
      return artifactValue.isTextual() && string.value().equals(artifactValue.textValue());
    }
    if (contextValue instanceof NumberContextValue number) {
      return artifactValue.isNumber()
          && new BigDecimal(String.valueOf(number.value()))
          .compareTo(artifactValue.decimalValue()) == 0;
    }
    if (contextValue instanceof BooleanContextValue bool) {
      return artifactValue.isBoolean() && bool.value() == artifactValue.booleanValue();
    }
    return false;
  }

  private JsonNode resolvePointer(StepDefinition step, WorkflowState state,
      ContextEqualityContract contract, String content) {
    try {
      return objectMapper.readTree(content).at(JsonPointer.compile(contract.jsonPointer()));
    } catch (IllegalArgumentException | JacksonException e) {
      throw fail(step, state, "equality contract could not read artifact '%s' / pointer '%s': %s"
          .formatted(contract.artifactPath(), contract.jsonPointer(), e.getMessage()));
    }
  }

  private ContextValue requiredContextValue(StepDefinition step, WorkflowState state,
      String contextKey) {
    return state.getContextValue(contextKey)
        .orElseThrow(() -> fail(step, state,
            "equality contract context key '%s' is missing".formatted(contextKey)));
  }

  private void runValidator(StepDefinition step, WorkflowState state, ValidateBehaviour behaviour,
      Map<String, String> captured) {
    ArtifactValidator validator = validatorsById.get(behaviour.validatorId());
    if (validator == null) {
      throw fail(step, state,
          "no ArtifactValidator registered for validatorId '%s'".formatted(behaviour.validatorId()));
    }
    // Scope the validator's view to this step's declared requiredArtifacts, not the run-level capture
    // union: a VALIDATE step's validator sees exactly the bundle it was selected to check, so multiple
    // VALIDATE steps with disjoint allowlists never expose one step's artifacts to another's validator.
    // The allowlist check above has already proven every required path is captured, so each is present.
    Map<String, String> artifacts = scopedArtifacts(behaviour, captured);
    ValidationResult result = validator.validate(() -> artifacts);
    if (result == null || !result.valid()) {
      String detail = result == null ? "validator returned no result" : result.message();
      throw fail(step, state, "artifact validation failed (%s): %s".formatted(behaviour.validatorId(), detail));
    }
  }

  private static Map<String, String> scopedArtifacts(ValidateBehaviour behaviour,
      Map<String, String> captured) {
    Map<String, String> scoped = new LinkedHashMap<>();
    for (String path : behaviour.requiredArtifacts()) {
      String content = captured.get(path);
      if (content != null) {
        scoped.put(path, content);
      }
    }
    return Map.copyOf(scoped);
  }

  /**
   * Records the partial-validation audit event and returns the fail-closed exception for the caller to throw — so each
   * failure site reads {@code throw fail(...)}, making the fail-closed control flow explicit (no unreachable
   * fall-through).
   */
  private StepExecutionException fail(StepDefinition step, WorkflowState state, String reason) {
    String message = "Generated-artifact validation failed at step '%s': %s".formatted(step.stepId(), reason);
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_FAILED, message, "runtime");
    return new StepExecutionException(message);
  }

  private static boolean isSafeRelativePath(String path) {
    if (path == null || path.isBlank() || path.startsWith("/") || path.startsWith("\\")) {
      return false;
    }
    final Path parsed;
    try {
      parsed = Path.of(path);
    } catch (InvalidPathException e) {
      return false;
    }
    if (parsed.isAbsolute()) {
      return false;
    }
    Path normalized = parsed.normalize();
    return !normalized.startsWith("..") && !normalized.toString().isBlank();
  }

  private static Map<String, ArtifactValidator> indexValidators(List<ArtifactValidator> validators) {
    Map<String, ArtifactValidator> byId = new LinkedHashMap<>();
    for (ArtifactValidator validator : validators) {
      Validate.notNull(validator, "validators must not contain null entries");
      String id = Validate.notBlank(validator.validatorId(), "ArtifactValidator validatorId must not be blank");
      ArtifactValidator previous = byId.putIfAbsent(id, validator);
      Validate.isTrue(previous == null, "Duplicate ArtifactValidator for id '%s'".formatted(id));
    }
    return byId;
  }
}
