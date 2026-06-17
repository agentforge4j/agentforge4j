// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed marker interface for typed context values stored in the shared workflow context.
 * Dispatched by the runtime based on the {@code type} discriminator.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StringContextValue.class, name = "STRING"),
    @JsonSubTypes.Type(value = NumberContextValue.class, name = "NUMBER"),
    @JsonSubTypes.Type(value = BooleanContextValue.class, name = "BOOLEAN"),
    @JsonSubTypes.Type(value = JsonContextValue.class, name = "JSON"),
    @JsonSubTypes.Type(value = ContextValueList.class, name = "LIST")
})
public sealed interface ContextValue
    permits StringContextValue,
    NumberContextValue,
    BooleanContextValue,
    JsonContextValue,
    ContextValueList {

  /**
   * Origin of this value, stamped at the context write. Never {@code null}.
   *
   * @return the provenance of this value
   */
  ContextProvenance provenance();

  /**
   * Returns a copy of this value with its provenance replaced. Used by the write path to re-stamp a
   * value whose provenance must be set authoritatively server-side (for example an LLM-emitted
   * {@code SET_CONTEXT} value, re-stamped {@link ContextProvenance#LLM_GENERATED}).
   *
   * @param provenance the provenance to apply; never {@code null}
   *
   * @return a copy carrying {@code provenance}; never {@code null}
   */
  ContextValue withProvenance(ContextProvenance provenance);
}
