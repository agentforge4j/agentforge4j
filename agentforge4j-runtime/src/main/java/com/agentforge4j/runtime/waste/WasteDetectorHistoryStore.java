// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.waste;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * Reads and writes {@link WasteDetector}'s persisted history at the reserved
 * {@code __wasteDetectorHistory.*} context keys (see {@link ReservedContextKeys}). Mirrors
 * {@code CompactSiblingStore}'s read/write-as-JSON-under-a-reserved-key pattern.
 */
public final class WasteDetectorHistoryStore {

  private WasteDetectorHistoryStore() {
  }

  /**
   * Reads the persisted invocation history for {@code stepId}, if any.
   *
   * @param state  run state to read from; must not be {@code null}
   * @param stepId the step id; must not be blank
   * @param mapper used to parse the stored JSON; must not be {@code null}
   *
   * @return the stored history, or empty when this step has no prior recorded invocation
   */
  public static Optional<WasteDetectorInvocationHistory> readInvocation(WorkflowState state,
      String stepId, ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notNull(mapper, "mapper must not be null");
    return readJson(state, ReservedContextKeys.wasteDetectorInvocationHistoryKey(stepId), mapper,
        WasteDetectorInvocationHistory.class);
  }

  /**
   * Writes the invocation history for {@code history.stepId()}, replacing any prior entry.
   *
   * @param state   run state to write to; must not be {@code null}
   * @param history the history to persist; must not be {@code null}
   * @param mapper  used to serialize the history; must not be {@code null}
   */
  public static void writeInvocation(WorkflowState state, WasteDetectorInvocationHistory history,
      ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notNull(history, "history must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    writeJson(state, ReservedContextKeys.wasteDetectorInvocationHistoryKey(history.stepId()),
        history, mapper);
  }

  /**
   * Reads the persisted loop history for {@code blueprintId}, if any.
   *
   * @param state       run state to read from; must not be {@code null}
   * @param blueprintId the blueprint id; must not be blank
   * @param mapper      used to parse the stored JSON; must not be {@code null}
   *
   * @return the stored history, or empty before the loop's first iteration has completed
   */
  public static Optional<WasteDetectorLoopHistory> readLoop(WorkflowState state,
      String blueprintId, ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(blueprintId, "blueprintId must not be blank");
    Validate.notNull(mapper, "mapper must not be null");
    return readJson(state, ReservedContextKeys.wasteDetectorLoopHistoryKey(blueprintId), mapper,
        WasteDetectorLoopHistory.class);
  }

  /**
   * Writes the loop history for {@code history.blueprintId()}, replacing any prior entry.
   *
   * @param state   run state to write to; must not be {@code null}
   * @param history the history to persist; must not be {@code null}
   * @param mapper  used to serialize the history; must not be {@code null}
   */
  public static void writeLoop(WorkflowState state, WasteDetectorLoopHistory history,
      ObjectMapper mapper) {
    Validate.notNull(state, "state must not be null");
    Validate.notNull(history, "history must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    writeJson(state, ReservedContextKeys.wasteDetectorLoopHistoryKey(history.blueprintId()),
        history, mapper);
  }

  private static <T> Optional<T> readJson(WorkflowState state, String key, ObjectMapper mapper,
      Class<T> type) {
    Optional<ContextValue> stored = state.getContextValue(key);
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    Validate.isTrue(stored.get() instanceof JsonContextValue,
        "Waste-detector history context key '%s' does not hold JSON content".formatted(key));
    try {
      return Optional.of(mapper.readValue(((JsonContextValue) stored.get()).json(), type));
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse stored waste-detector history for '%s': %s"
              .formatted(key, e.getMessage()), e);
    }
  }

  private static void writeJson(WorkflowState state, String key, Object value,
      ObjectMapper mapper) {
    try {
      state.putContextValue(key,
          new JsonContextValue(mapper.writeValueAsString(value), ContextProvenance.SYSTEM_GENERATED));
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to serialize waste-detector history for '%s': %s".formatted(key, e.getMessage()),
          e);
    }
  }
}
