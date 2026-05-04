package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record CallEndpointCommand(
    @JsonProperty(required = true)
    String integrationId,
    @JsonProperty(required = true)
    String operation,
    Map<String, Object> payload,
    String contextKey
) implements LlmCommand {
    public CallEndpointCommand {
        Validate.notBlank(integrationId,
            "CallEndpointCommand integrationId must not be blank");
        Validate.notBlank(operation,
            "CallEndpointCommand operation must not be blank");
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }
}
