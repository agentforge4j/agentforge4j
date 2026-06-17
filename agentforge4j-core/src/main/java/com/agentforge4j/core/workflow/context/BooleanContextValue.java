// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Boolean payload stored in workflow context.
 *
 * @param value      boolean content
 * @param provenance origin of the content; never {@code null}
 */
public record BooleanContextValue(boolean value, ContextProvenance provenance) implements ContextValue {

  public BooleanContextValue {
    Validate.notNull(provenance, "BooleanContextValue provenance must not be null");
  }

  /**
   * Jackson deserialization seam: defaults absent {@code provenance} to
   * {@link ContextProvenance#USER_SUPPLIED} (fail-safe). See {@link StringContextValue#fromJson}.
   */
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public static BooleanContextValue fromJson(
      @JsonProperty("value") boolean value,
      @JsonProperty("provenance") ContextProvenance provenance) {
    return new BooleanContextValue(value, ContextProvenance.orUserSupplied(provenance));
  }

  @Override
  public ContextValue withProvenance(ContextProvenance provenance) {
    return new BooleanContextValue(value, provenance);
  }
}
