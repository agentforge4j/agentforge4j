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

}
