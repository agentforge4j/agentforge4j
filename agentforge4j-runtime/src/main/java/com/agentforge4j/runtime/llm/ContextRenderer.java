package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/**
 * Renders the subset of the shared context exposed to an agent as a JSON object — filtering by
 * {@link ContextMapping#inputKeys()} when a mapping is provided.
 *
 * <p>An empty {@code inputKeys} list means no filtering is applied and all
 * context values are rendered.
 */
public final class ContextRenderer {

  private final ObjectMapper objectMapper;
  private final Map<Class<? extends ContextValue>, ContextValueExtractor<?>> contextRenderValues;

  public ContextRenderer(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.contextRenderValues = indexRenderValues();
  }

  public String render(Map<String, ContextValue> context, ContextMapping mapping) {
    Validate.notNull(context, "context must not be null");
    Validate.notNull(mapping, "mapping must not be null");
    ObjectNode root = objectMapper.createObjectNode();
    List<String> inputKeys = mapping.inputKeys();
    Validate.notNull(inputKeys, "inputKeys must not be null");
    context.entrySet().stream()
        .filter(entry -> inputKeys.isEmpty() || inputKeys.contains(entry.getKey()))
        .forEach(entry -> root.set(entry.getKey(), renderValue(entry.getValue())));
    return root.toString();
  }

  private <T extends ContextValue> JsonNode renderValue(T value) {
    @SuppressWarnings("unchecked")
    ContextValueExtractor<T> extractor =
        (ContextValueExtractor<T>) contextRenderValues.get(value.getClass());

    return Validate.notNull(extractor,
            "No ContextValueExtractor registered for: " + value.getClass().getName())
        .extract(value);
  }

  private ArrayNode parseListValue(ContextValueList listValue) {
    ArrayNode array = objectMapper.createArrayNode();
    listValue.values().forEach(element -> array.add(renderValue(element)));
    return array;
  }

  private JsonNode parseJsonOrText(JsonContextValue json) {
    try {
      return objectMapper.readTree(json.json());
    } catch (Exception e) {
      return objectMapper.getNodeFactory().textNode(json.json());
    }
  }

  private Map<Class<? extends ContextValue>, ContextValueExtractor<?>> indexRenderValues() {
    return Map.ofEntries(entry(StringContextValue.class,
            node -> objectMapper.getNodeFactory().textNode(node.value())),
        entry(NumberContextValue.class,
            node -> objectMapper.valueToTree(node.value())),
        entry(BooleanContextValue.class,
            node -> objectMapper.getNodeFactory().booleanNode(node.value())),
        entry(JsonContextValue.class, this::parseJsonOrText),
        entry(ContextValueList.class, this::parseListValue)
    );
  }

  private static <T extends ContextValue> Map.Entry<Class<T>, ContextValueExtractor<T>> entry(
      Class<T> type,
      ContextValueExtractor<T> extractor) {
    return Map.entry(type, extractor);
  }
}
