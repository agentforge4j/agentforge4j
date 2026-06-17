// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ordered list of {@link ContextValue} entries treated as a single context value for
 * serialization.
 *
 * @param values     non-null, immutable copy of the list supplied at construction
 * @param provenance origin of the list value; never {@code null}. Each element carries its own
 *                   provenance independently.
 */
public record ContextValueList(List<ContextValue> values, ContextProvenance provenance) implements ContextValue {

  public ContextValueList {
    Validate.notNull(values, "ContextValueList values must not be null");
    Validate.notNull(provenance, "ContextValueList provenance must not be null");
    values = List.copyOf(values);
  }

  /**
   * Jackson deserialization seam: defaults absent {@code provenance} to
   * {@link ContextProvenance#USER_SUPPLIED} (fail-safe). See {@link StringContextValue#fromJson}.
   */
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public static ContextValueList fromJson(
      @JsonProperty(value = "values", required = true) List<ContextValue> values,
      @JsonProperty("provenance") ContextProvenance provenance) {
    return new ContextValueList(values, ContextProvenance.orUserSupplied(provenance));
  }

  /**
   * Re-stamps the <strong>container</strong> provenance only; the nested element provenances are
   * unchanged. Render-time partitioning decides root vs untrusted placement by the container's
   * provenance, so this is sufficient for isolation; elements keep their own provenance independently.
   */
  @Override
  public ContextValue withProvenance(ContextProvenance provenance) {
    return new ContextValueList(values, provenance);
  }
}
