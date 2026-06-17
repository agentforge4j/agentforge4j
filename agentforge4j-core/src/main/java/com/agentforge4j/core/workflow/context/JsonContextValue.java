// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON text stored as a context value; treated as opaque JSON until parsed by a consumer.
 *
 * @param json       non-blank JSON document or fragment
 * @param provenance origin of the content; never {@code null}
 */
public record JsonContextValue(String json, ContextProvenance provenance) implements ContextValue {

  public JsonContextValue {
    Validate.notBlank(json, "JsonContextValue json must not be blank");
    Validate.notNull(provenance, "JsonContextValue provenance must not be null");
  }

  /**
   * Jackson deserialization seam: defaults absent {@code provenance} to
   * {@link ContextProvenance#USER_SUPPLIED} (fail-safe). See {@link StringContextValue#fromJson}.
   */
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public static JsonContextValue fromJson(
      @JsonProperty(value = "json", required = true) String json,
      @JsonProperty("provenance") ContextProvenance provenance) {
    return new JsonContextValue(json, ContextProvenance.orUserSupplied(provenance));
  }

  @Override
  public ContextValue withProvenance(ContextProvenance provenance) {
    return new JsonContextValue(json, provenance);
  }
}
