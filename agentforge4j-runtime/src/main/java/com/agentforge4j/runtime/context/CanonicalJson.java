// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a {@link JsonNode} to text with object fields sorted alphabetically at every level, so
 * semantically identical JSON (which Jackson may parse with field order depending on the source
 * document) always produces the same text. Array element order is preserved — order is semantically
 * meaningful in an array.
 *
 * <p>Used wherever content must fingerprint stably regardless of incidental field ordering: ledger
 * section content read for compaction, and structured output normalized for waste detection.
 */
public final class CanonicalJson {

  private CanonicalJson() {
  }

  /**
   * Renders {@code node} as canonical JSON text (object fields sorted, arrays order-preserved).
   *
   * @param node   the node to render; must not be {@code null}
   * @param mapper used to serialize the canonicalized tree; must not be {@code null}
   *
   * @return the canonical JSON text; never {@code null}
   */
  public static String render(JsonNode node, ObjectMapper mapper) {
    Validate.notNull(node, "node must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    return canonicalize(node, mapper).toString();
  }

  private static JsonNode canonicalize(JsonNode node, ObjectMapper mapper) {
    if (node.isObject()) {
      Map<String, JsonNode> sorted = new TreeMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        sorted.put(field.getKey(), canonicalize(field.getValue(), mapper));
      }
      ObjectNode result = mapper.createObjectNode();
      sorted.forEach(result::set);
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = mapper.createArrayNode();
      node.forEach(element -> result.add(canonicalize(element, mapper)));
      return result;
    }
    return node;
  }
}
