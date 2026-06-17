// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Numeric-typed context value.
 *
 * @param value      non-null numeric content
 * @param provenance origin of the content; never {@code null}
 */
public record NumberContextValue(Number value, ContextProvenance provenance) implements ContextValue {

  public NumberContextValue {
    Validate.notNull(value, "NumberContextValue value must not be null");
    Validate.notNull(provenance, "NumberContextValue provenance must not be null");
  }

  /**
   * Jackson deserialization seam: defaults absent {@code provenance} to
   * {@link ContextProvenance#USER_SUPPLIED} (fail-safe). See {@link StringContextValue#fromJson}.
   */
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public static NumberContextValue fromJson(
      @JsonProperty(value = "value", required = true) Number value,
      @JsonProperty("provenance") ContextProvenance provenance) {
    return new NumberContextValue(value, ContextProvenance.orUserSupplied(provenance));
  }

  @Override
  public ContextValue withProvenance(ContextProvenance provenance) {
    return new NumberContextValue(value, provenance);
  }
}
