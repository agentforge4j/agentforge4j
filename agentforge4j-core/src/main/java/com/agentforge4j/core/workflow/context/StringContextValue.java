// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * String-typed context value.
 *
 * @param value      non-blank string content
 * @param provenance origin of the content; never {@code null}
 */
public record StringContextValue(String value, ContextProvenance provenance) implements ContextValue {

  public StringContextValue {
    Validate.notBlank(value, "StringContextValue value must not be blank");
    Validate.notNull(provenance, "StringContextValue provenance must not be null");
  }

  /**
   * Jackson deserialization seam: defaults absent {@code provenance} to
   * {@link ContextProvenance#USER_SUPPLIED} (fail-safe). Inbound LLM {@code SET_CONTEXT} JSON carries
   * no provenance and is re-stamped by the write path; persisted state always carries it. Direct Java
   * construction must use the canonical constructor and supply provenance explicitly.
   */
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public static StringContextValue fromJson(
      @JsonProperty(value = "value", required = true) String value,
      @JsonProperty("provenance") ContextProvenance provenance) {
    return new StringContextValue(value, ContextProvenance.orUserSupplied(provenance));
  }

  @Override
  public ContextValue withProvenance(ContextProvenance provenance) {
    return new StringContextValue(value, provenance);
  }
}
