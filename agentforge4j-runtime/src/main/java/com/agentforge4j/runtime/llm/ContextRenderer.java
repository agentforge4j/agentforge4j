// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
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
 *
 * <p>Entries are partitioned by provenance: trusted entries
 * ({@link com.agentforge4j.core.workflow.context.ContextProvenance#isTrusted()}) render at the JSON root with their
 * keys unchanged, while untrusted entries (user- or external-tool-supplied) are isolated under a reserved
 * {@value #UNTRUSTED_USER_INPUT_KEY} object so embedded instructions are structurally separated from trusted content.
 * The envelope is always emitted (stable shape) even when empty.
 */
public final class ContextRenderer {

  /**
   * Reserved root key under which untrusted (user- or external-tool-supplied) context entries are isolated. A trusted
   * context key that collides with this name is rejected.
   */
  public static final String UNTRUSTED_USER_INPUT_KEY = UntrustedInputEnvelope.KEY;

  private final ObjectMapper objectMapper;
  private final Map<Class<? extends ContextValue>, ContextValueExtractor<?>> contextRenderValues;

  public ContextRenderer(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.contextRenderValues = indexRenderValues();
  }

  public String render(Map<String, ContextValue> context, ContextMapping mapping) {
    Validate.notNull(context, "context must not be null");
    Validate.notNull(mapping, "mapping must not be null");
    List<String> inputKeys = mapping.inputKeys();
    Validate.notNull(inputKeys, "inputKeys must not be null");
    ObjectNode root = objectMapper.createObjectNode();
    ObjectNode untrusted = objectMapper.createObjectNode();
    context.entrySet().stream()
        .filter(entry -> inputKeys.isEmpty() || inputKeys.contains(entry.getKey()))
        .forEach(entry -> partition(root, untrusted, entry.getKey(), entry.getValue()));
    // Always emit the untrusted envelope so the rendered shape is stable, even when empty.
    root.set(UNTRUSTED_USER_INPUT_KEY, untrusted);
    return root.toString();
  }

  private void partition(ObjectNode root, ObjectNode untrusted, String key, ContextValue value) {
    if (value.provenance().isTrusted()) {
      Validate.isTrue(!UNTRUSTED_USER_INPUT_KEY.equals(key),
          "Trusted context key '%s' collides with the reserved untrusted-input envelope key"
              .formatted(UNTRUSTED_USER_INPUT_KEY));
      root.set(key, renderValue(value));
    } else {
      untrusted.set(key, renderValue(value));
    }
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
