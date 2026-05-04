package com.agentforge4j.core.workflow.context;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
            ContextValueList {}
