package com.agentforge4j.core.command.schema;

import com.agentforge4j.core.command.LlmCommand;
import com.fasterxml.jackson.annotation.JsonSubTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for LLM command type names: mirrors {@link LlmCommand}'s
 * {@link JsonSubTypes} declaration order.
 */
public final class LlmCommandSubtypeRegistry {

  private static final LinkedHashMap<String, Class<? extends LlmCommand>> ORDERED;

  static {
    JsonSubTypes subTypes = LlmCommand.class.getAnnotation(JsonSubTypes.class);
    if (subTypes == null) {
      throw new IllegalStateException("LlmCommand must declare @JsonSubTypes");
    }
    LinkedHashMap<String, Class<? extends LlmCommand>> map = new LinkedHashMap<>();
    for (JsonSubTypes.Type t : subTypes.value()) {
      @SuppressWarnings("unchecked")
      Class<? extends LlmCommand> clazz = (Class<? extends LlmCommand>) t.value();
      map.put(t.name(), clazz);
    }
    ORDERED = map;
  }

  private LlmCommandSubtypeRegistry() {
  }

  /**
   * Returns an unmodifiable map from command type name to implementing class.
   *
   * @return map of type names to classes
   */
  public static Map<String, Class<? extends LlmCommand>> allSubtypes() {
    return Collections.unmodifiableMap(ORDERED);
  }

  /**
   * Returns the command type names in declaration order.
   *
   * @return ordered list of type names
   */
  public static List<String> allTypeNamesOrdered() {
    return List.copyOf(ORDERED.keySet());
  }
}
