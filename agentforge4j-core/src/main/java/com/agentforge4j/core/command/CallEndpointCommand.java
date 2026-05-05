package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Instruct the runtime to call an external endpoint via a registered integration.
 * The response is stored in the workflow context under the specified key if provided.
 */
public record CallEndpointCommand(
    @JsonProperty(required = true)
    String integrationId,
    @JsonProperty(required = true)
    String operation,
    Map<String, Object> payload,
    String contextKey
) implements LlmCommand {

  /**
   * @param integrationId the identifier of the integration to use
   * @param operation the operation to perform on the integration
   * @param payload optional data to send with the request
   * @param contextKey optional key to store the response under in workflow context
   */
  public CallEndpointCommand {
    Validate.notBlank(integrationId,
        "CallEndpointCommand integrationId must not be blank");
    Validate.notBlank(operation,
        "CallEndpointCommand operation must not be blank");
    payload = payload != null ? Map.copyOf(payload) : Map.of();
  }
}
