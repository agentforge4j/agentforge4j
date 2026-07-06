// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.assertion;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fluent, framework-agnostic assertions over a {@link WorkflowRunResult}. Every verb is a pure
 * projection over the captured event stream, captured files, and the final state; on a failed
 * expectation a plain {@link AssertionError} is thrown, so the engine carries no test-framework
 * dependency. All verbs return {@code this} for chaining.
 */
public final class WorkflowRunAssert {

  private final WorkflowRunResult result;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WorkflowRunAssert(WorkflowRunResult result) {
    this.result = Validate.notNull(result, "result must not be null");
  }

  /**
   * Begins an assertion chain over a run result.
   *
   * @param result the run result; must not be {@code null}
   *
   * @return a new assertion
   */
  public static WorkflowRunAssert assertThat(WorkflowRunResult result) {
    return new WorkflowRunAssert(result);
  }

  // --- status -------------------------------------------------------------------------------

  /**
   * Asserts the run's final status.
   *
   * @param expected the expected status; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert hasStatus(WorkflowStatus expected) {
    Validate.notNull(expected, "expected status must not be null");
    WorkflowStatus actual = result.finalState().getStatus();
    if (actual != expected) {
      throw error("Expected run status %s but was %s".formatted(expected, actual));
    }
    return this;
  }

  /**
   * Asserts the run completed.
   *
   * @return this
   */
  public WorkflowRunAssert isCompleted() {
    return hasStatus(WorkflowStatus.COMPLETED);
  }

  /**
   * Asserts the run failed.
   *
   * @return this
   */
  public WorkflowRunAssert isFailed() {
    return hasStatus(WorkflowStatus.FAILED);
  }

  /**
   * Asserts the run failed with the given failure kind.
   *
   * @param kind the expected failure kind; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert failedWith(FailureKind kind) {
    Validate.notNull(kind, "kind must not be null");
    RunFailure failure = result.finalState().getRunFailure();
    if (failure == null) {
      throw error("Expected the run to have failed with %s but no RunFailure was recorded"
          .formatted(kind));
    }
    FailureKind actual = classify(failure);
    if (actual != kind) {
      throw error("Expected failure kind %s but was %s".formatted(kind, actual));
    }
    return this;
  }

  /**
   * Asserts the run failed and its recorded failure reason contains the given fragment. Use this to
   * pin the specific rejection a negative scenario provokes (a parse error, a path-escape, a
   * fail-closed miss) rather than merely that the run ended {@code FAILED}.
   *
   * @param fragment expected substring of the failure reason; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert failedBecause(String fragment) {
    Validate.notBlank(fragment, "fragment must not be blank");
    hasStatus(WorkflowStatus.FAILED);
    RunFailure failure = result.finalState().getRunFailure();
    if (failure == null) {
      throw error("Expected the run to have failed because of '%s' but no RunFailure was recorded"
          .formatted(fragment));
    }
    if (!failure.failureReason().contains(fragment)) {
      throw error("Expected failure reason to contain '%s' but was '%s'"
          .formatted(fragment, failure.failureReason()));
    }
    return this;
  }

  // --- steps --------------------------------------------------------------------------------

  /**
   * Asserts the step was visited at least once.
   *
   * @param stepId the step id; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert visitedStep(String stepId) {
    requireStepId(stepId);
    if (stepStartCount(stepId) == 0) {
      throw error("Expected step '%s' to have been visited but it was not".formatted(stepId));
    }
    return this;
  }

  /**
   * Asserts the step was never visited.
   *
   * @param stepId the step id; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert didNotVisitStep(String stepId) {
    requireStepId(stepId);
    long count = stepStartCount(stepId);
    if (count > 0) {
      throw error("Expected step '%s' not to be visited but it was visited %d time(s)"
          .formatted(stepId, count));
    }
    return this;
  }

  /**
   * Asserts the step was visited exactly the expected number of times.
   *
   * @param stepId   the step id; must not be blank
   * @param expected the expected visit count
   *
   * @return this
   */
  public WorkflowRunAssert stepVisitCount(String stepId, int expected) {
    requireStepId(stepId);
    long count = stepStartCount(stepId);
    if (count != expected) {
      throw error("Expected step '%s' to be visited %d time(s) but was %d"
          .formatted(stepId, expected, count));
    }
    return this;
  }

  /**
   * Asserts the given step ids occur, in order, as a subsequence of the visited steps.
   *
   * @param stepIds the ordered step ids; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert stepsInOrderedSubsequence(String... stepIds) {
    Validate.notNull(stepIds, "stepIds must not be null");
    List<String> visited = result.captures().events().stream()
        .filter(event -> event.eventType() == WorkflowEventType.STEP_STARTED)
        .map(WorkflowEvent::stepId)
        .toList();
    if (!isOrderedSubsequence(visited, Arrays.asList(stepIds))) {
      throw error("Steps %s are not an ordered subsequence of visited steps %s"
          .formatted(Arrays.toString(stepIds), visited));
    }
    return this;
  }

  // --- context ------------------------------------------------------------------------------

  /**
   * Asserts a context key is present.
   *
   * @param key the context key; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert contextHas(String key) {
    requireKey(key);
    if (!result.finalState().getContext().containsKey(key)) {
      throw error("Expected context key '%s' to be present".formatted(key));
    }
    return this;
  }

  /**
   * Asserts a context key is absent.
   *
   * @param key the context key; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert contextMissing(String key) {
    requireKey(key);
    if (result.finalState().getContext().containsKey(key)) {
      throw error("Expected context key '%s' to be absent".formatted(key));
    }
    return this;
  }

  /**
   * Asserts a context key is present and its value is non-empty.
   *
   * @param key the context key; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert contextNonEmpty(String key) {
    ContextValue value = requirePresent(key);
    if (isBlank(asString(value))) {
      throw error("Expected context key '%s' to be non-empty".formatted(key));
    }
    return this;
  }

  /**
   * Asserts a context value equals the expected control value. Reserve this for deterministic
   * control values, never for LLM prose.
   *
   * @param key      the context key; must not be blank
   * @param expected the expected value; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert contextEquals(String key, String expected) {
    Validate.notNull(expected, "expected must not be null");
    ContextValue value = requirePresent(key);
    String actual = asString(value);
    if (!expected.equals(actual)) {
      throw error("Expected context key '%s' to equal '%s' but was '%s'"
          .formatted(key, expected, actual));
    }
    return this;
  }

  /**
   * Asserts a context value matches the given regular expression.
   *
   * @param key   the context key; must not be blank
   * @param regex the regular expression; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert contextMatchesRegex(String key, String regex) {
    Validate.notBlank(regex, "regex must not be blank");
    ContextValue value = requirePresent(key);
    String actual = asString(value);
    if (!Pattern.matches(regex, actual)) {
      throw error("Expected context key '%s' (value '%s') to match /%s/"
          .formatted(key, actual, regex));
    }
    return this;
  }

  /**
   * Asserts a JSON context value has the given top-level field.
   *
   * @param key   the context key; must not be blank
   * @param field the JSON field name; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert contextHasField(String key, String field) {
    Validate.notBlank(field, "field must not be blank");
    ContextValue value = requirePresent(key);
    if (!(value instanceof JsonContextValue json)) {
      throw error("Expected context key '%s' to be a JSON value but was %s"
          .formatted(key, value.getClass().getSimpleName()));
    }
    if (!readJson(json.json()).has(field)) {
      throw error("Expected JSON context key '%s' to have field '%s'".formatted(key, field));
    }
    return this;
  }

  // --- artifacts ----------------------------------------------------------------------------

  /**
   * Asserts a file was created at the given path.
   *
   * @param path the requested path; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert createdFile(String path) {
    Validate.notBlank(path, "path must not be blank");
    if (result.captures().files().stream().noneMatch(file -> file.path().equals(path))) {
      throw error("Expected a file to be created at path '%s'".formatted(path));
    }
    return this;
  }

  /**
   * Alias of {@link #createdFile(String)}.
   *
   * @param path the requested path; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert artifactPresent(String path) {
    return createdFile(path);
  }

  /**
   * Asserts no file was created at the given path.
   *
   * @param path the requested path; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert artifactAbsent(String path) {
    Validate.notBlank(path, "path must not be blank");
    if (result.captures().files().stream().anyMatch(file -> file.path().equals(path))) {
      throw error("Expected no file at path '%s'".formatted(path));
    }
    return this;
  }

  // --- events -------------------------------------------------------------------------------

  /**
   * Asserts an event of the given type was emitted.
   *
   * @param type the event type; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert emittedEvent(WorkflowEventType type) {
    requireType(type);
    if (countEvents(type) == 0) {
      throw error("Expected event %s to be emitted".formatted(type));
    }
    return this;
  }

  /**
   * Asserts no event of the given type was emitted.
   *
   * @param type the event type; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert didNotEmitEvent(WorkflowEventType type) {
    requireType(type);
    if (countEvents(type) > 0) {
      throw error("Expected event %s not to be emitted".formatted(type));
    }
    return this;
  }

  /**
   * Asserts the exact number of events of the given type.
   *
   * @param type     the event type; must not be {@code null}
   * @param expected the expected count
   *
   * @return this
   */
  public WorkflowRunAssert eventCount(WorkflowEventType type, int expected) {
    requireType(type);
    long count = countEvents(type);
    if (count != expected) {
      throw error("Expected %d %s event(s) but found %d".formatted(expected, type, count));
    }
    return this;
  }

  /**
   * Asserts the given event types occur, in order, as a subsequence of the emitted events.
   *
   * @param types the ordered event types; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert eventsInOrder(WorkflowEventType... types) {
    Validate.notNull(types, "types must not be null");
    List<WorkflowEventType> emitted = result.captures().events().stream()
        .map(WorkflowEvent::eventType)
        .toList();
    if (!isOrderedSubsequence(emitted, Arrays.asList(types))) {
      throw error("Event types %s are not an ordered subsequence of the emitted events %s"
          .formatted(Arrays.toString(types), emitted));
    }
    return this;
  }

  // --- pending / approvals / inputs ---------------------------------------------------------

  /**
   * Asserts the run is in the given pending state.
   *
   * @param status the pending status; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert reachedPendingState(WorkflowStatus status) {
    Validate.notNull(status, "status must not be null");
    WorkflowStatus actual = result.finalState().getStatus();
    if (actual != status) {
      throw error("Expected run to be in pending state %s but was %s".formatted(status, actual));
    }
    return this;
  }

  /**
   * Asserts an approval was requested at the given step.
   *
   * @param stepId the step id; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert approvalRequested(String stepId) {
    requireStepId(stepId);
    if (eventsForStep(WorkflowEventType.STEP_AWAITING_APPROVAL, stepId).isEmpty()) {
      throw error("Expected an approval to be requested at step '%s'".formatted(stepId));
    }
    return this;
  }

  /**
   * Asserts an approval decision with a non-empty reason/note was recorded at the given step.
   *
   * @param stepId  the step id; must not be blank
   * @param outcome the expected outcome; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert approvalDecision(String stepId, ApprovalOutcome outcome) {
    requireStepId(stepId);
    Validate.notNull(outcome, "outcome must not be null");
    WorkflowEventType type = outcome == ApprovalOutcome.APPROVED
        ? WorkflowEventType.STEP_APPROVED
        : WorkflowEventType.STEP_REJECTED;
    List<WorkflowEvent> matches = eventsForStep(type, stepId);
    if (matches.isEmpty()) {
      throw error("Expected an %s decision at step '%s'".formatted(outcome, stepId));
    }
    if (matches.stream().allMatch(event -> isBlank(event.payload()))) {
      throw error("Expected the %s decision at step '%s' to carry a non-empty reason/note"
          .formatted(outcome, stepId));
    }
    return this;
  }

  /**
   * Asserts input was requested at the given step.
   *
   * @param stepId the step id; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert inputRequested(String stepId) {
    requireStepId(stepId);
    if (eventsForStep(WorkflowEventType.AWAITING_INPUT, stepId).isEmpty()) {
      throw error("Expected input to be requested at step '%s'".formatted(stepId));
    }
    return this;
  }

  // --- iteration ----------------------------------------------------------------------------

  /**
   * Asserts the iteration count, counted from {@code LOOP_ITERATION_STARTED}. This asserts the
   * number of iterations, not the loop strategy — the event stream carries no strategy
   * discriminator, so loop and {@code forEach} cannot be told apart here.
   *
   * @param expected the expected iteration count
   *
   * @return this
   */
  public WorkflowRunAssert loopIterations(int expected) {
    long count = countEvents(WorkflowEventType.LOOP_ITERATION_STARTED);
    if (count != expected) {
      throw error("Expected %d loop iteration(s) but found %d".formatted(expected, count));
    }
    return this;
  }

  /**
   * Asserts the iteration count; an alias of {@link #loopIterations(int)} over the single canonical
   * {@code LOOP_ITERATION_STARTED} source. This asserts the number of iterations, not the loop
   * strategy — the event stream does not distinguish a {@code forEach} loop from any other, so the
   * two names imply no distinction the events can back.
   *
   * @param expected the expected iteration count
   *
   * @return this
   */
  public WorkflowRunAssert forEachIterations(int expected) {
    return loopIterations(expected);
  }

  // --- tool calls ---------------------------------------------------------------------------

  /**
   * Asserts the given tool capability was invoked.
   *
   * @param capabilityId the capability id; must not be blank
   *
   * @return this
   */
  public WorkflowRunAssert invokedTool(String capabilityId) {
    Validate.notBlank(capabilityId, "capabilityId must not be blank");
    boolean found = eventsOfType(WorkflowEventType.TOOL_INVOCATION_REQUESTED).stream()
        .anyMatch(event -> capabilityId.equals(jsonField(event.payload(), "capability")));
    if (!found) {
      throw error("Expected tool '%s' to be invoked".formatted(capabilityId));
    }
    return this;
  }

  /**
   * Asserts the number of tool invocations (counted from {@code TOOL_INVOCATION_REQUESTED}).
   *
   * @param expected the expected count
   *
   * @return this
   */
  public WorkflowRunAssert toolCallCount(int expected) {
    long count = countEvents(WorkflowEventType.TOOL_INVOCATION_REQUESTED);
    if (count != expected) {
      throw error("Expected %d tool invocation(s) but found %d".formatted(expected, count));
    }
    return this;
  }

  // --- provider calls -----------------------------------------------------------------------

  /**
   * Asserts the number of provider (LLM) calls (counted from {@code LLM_CALL_COMPLETED}).
   *
   * @param expected the expected count
   *
   * @return this
   */
  public WorkflowRunAssert providerCallCount(int expected) {
    long count = countEvents(WorkflowEventType.LLM_CALL_COMPLETED);
    if (count != expected) {
      throw error("Expected %d provider call(s) but found %d".formatted(expected, count));
    }
    return this;
  }

  /**
   * Asserts the number of provider (LLM) calls recorded for a specific step, regardless of dispatch
   * or attempt. Useful for proving a looping or parse-retried step made the expected number of real,
   * billable provider calls — each carries its own {@code stepUid}/{@code callAttempt} discriminator
   * in its {@code LLM_CALL_COMPLETED} payload, but this assertion only needs the step id.
   *
   * @param stepId   the step id to filter on; must not be blank
   * @param expected the expected count
   *
   * @return this
   */
  public WorkflowRunAssert providerCallCountForStep(String stepId, int expected) {
    Validate.notBlank(stepId, "stepId must not be blank");
    long count = eventsOfType(WorkflowEventType.LLM_CALL_COMPLETED).stream()
        .filter(event -> stepId.equals(event.stepId()))
        .count();
    if (count != expected) {
      throw error("Expected %d provider call(s) for step '%s' but found %d"
          .formatted(expected, stepId, count));
    }
    return this;
  }

  /**
   * Asserts at least one provider call resolved its model from the given tier.
   *
   * @param tier the expected model tier; must not be {@code null}
   *
   * @return this
   */
  public WorkflowRunAssert providerCallTier(ModelTier tier) {
    Validate.notNull(tier, "tier must not be null");
    boolean found = eventsOfType(WorkflowEventType.LLM_CALL_COMPLETED).stream()
        .anyMatch(event -> tier.name().equals(jsonField(event.payload(), "requestedModelTier")));
    if (!found) {
      throw error("Expected at least one provider call at tier %s".formatted(tier));
    }
    return this;
  }

  // --- run metadata -------------------------------------------------------------------------

  /**
   * Asserts the deterministic total token usage recorded on the run.
   *
   * @param expectedTotal the expected total
   *
   * @return this
   */
  public WorkflowRunAssert tokenTotals(int expectedTotal) {
    int actual = tokenTotal();
    if (actual != expectedTotal) {
      throw error("Expected total token usage %d but was %d".formatted(expectedTotal, actual));
    }
    return this;
  }

  // --- internals ----------------------------------------------------------------------------

  private int tokenTotal() {
    return result.finalState().getContextValue(ReservedContextKeys.LLM_TOKENS_TOTAL)
        .filter(NumberContextValue.class::isInstance)
        .map(value -> ((NumberContextValue) value).value().intValue())
        .orElse(0);
  }

  private ContextValue requirePresent(String key) {
    requireKey(key);
    return result.finalState().getContextValue(key)
        .orElseThrow(() -> error("Expected context key '%s' to be present".formatted(key)));
  }

  private long stepStartCount(String stepId) {
    return eventsForStep(WorkflowEventType.STEP_STARTED, stepId).size();
  }

  private long countEvents(WorkflowEventType type) {
    return result.captures().events().stream()
        .filter(event -> event.eventType() == type)
        .count();
  }

  private List<WorkflowEvent> eventsOfType(WorkflowEventType type) {
    return result.captures().events().stream()
        .filter(event -> event.eventType() == type)
        .toList();
  }

  private List<WorkflowEvent> eventsForStep(WorkflowEventType type, String stepId) {
    return result.captures().events().stream()
        .filter(event -> event.eventType() == type && stepId.equals(event.stepId()))
        .toList();
  }

  private String jsonField(String payload, String field) {
    if (isBlank(payload)) {
      return null;
    }
    try {
      JsonNode value = objectMapper.readTree(payload).get(field);
      return value == null || value.isNull() ? null : value.asText();
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw error("Context value is not valid JSON: " + e.getOriginalMessage());
    }
  }

  private static String asString(ContextValue value) {
    if (value instanceof StringContextValue string) {
      return string.value();
    }
    if (value instanceof NumberContextValue number) {
      return String.valueOf(number.value());
    }
    if (value instanceof BooleanContextValue bool) {
      return Boolean.toString(bool.value());
    }
    if (value instanceof JsonContextValue json) {
      return json.json();
    }
    if (value instanceof ContextValueList list) {
      return list.values().toString();
    }
    throw error("Unsupported context value type: " + value.getClass().getName());
  }

  private static <T> boolean isOrderedSubsequence(List<T> sequence, List<T> wanted) {
    int cursor = 0;
    for (T target : wanted) {
      boolean found = false;
      while (cursor < sequence.size()) {
        T current = sequence.get(cursor);
        cursor++;
        if (current != null && current.equals(target)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private static void requireKey(String key) {
    Validate.notBlank(key, "context key must not be blank");
  }

  private static void requireStepId(String stepId) {
    Validate.notBlank(stepId, "stepId must not be blank");
  }

  private static void requireType(WorkflowEventType type) {
    Validate.notNull(type, "event type must not be null");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Maps a {@link RunFailure} to its {@link FailureKind}, covering every permitted subtype of the
   * sealed hierarchy explicitly. The trailing {@code throw} guards against a newly added
   * {@code RunFailure} subtype silently classifying as {@code EXCEPTION}: it fails loudly until the
   * mapping is updated. (A type-pattern {@code switch}, which would make this a compile-time check,
   * is a Java 21 feature; this module targets Java 17, so the exhaustiveness is enforced at runtime.)
   */
  private static FailureKind classify(RunFailure failure) {
    if (failure instanceof RunFailure.StepRejectionFailure) {
      return FailureKind.STEP_REJECTION;
    }
    if (failure instanceof RunFailure.ExceptionFailure) {
      return FailureKind.EXCEPTION;
    }
    throw new IllegalStateException(
        "Unmapped RunFailure subtype %s; update WorkflowRunAssert.classify and FailureKind"
            .formatted(failure.getClass().getName()));
  }

  private static AssertionError error(String message) {
    return new AssertionError(message);
  }
}
