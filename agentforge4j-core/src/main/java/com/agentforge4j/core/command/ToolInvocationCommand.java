package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/**
 * Request from the LLM to invoke a logical tool capability. The eleventh {@link LlmCommand}; it
 * always flows through the runtime's {@code ToolExecutionService} chokepoint, never directly to an
 * external system.
 *
 * <p>{@code toolInvocationId} correlates audit events and approval resume. The LLM omits it when
 * emitting the command, so the compact constructor generates a UUID when it is {@code null} or
 * blank — leaving an explicitly supplied id untouched.
 *
 * <p>{@code arguments} is a JSON object (mirroring {@link CallEndpointCommand#payload()}): the LLM
 * emits it as a structured object, not an escaped JSON string. The runtime serializes it to JSON
 * text at the {@code ToolProvider} boundary, preserving the opaque-string contract there.
 *
 * @param toolInvocationId stable invocation id; generated if absent
 * @param capability       non-blank logical capability id, for example
 *                         {@code "github.create_pull_request"}
 * @param arguments        tool arguments as a JSON object; never {@code null} (defaults to empty)
 * @param llmRationale     model rationale for audit, or {@code null}
 */
public record ToolInvocationCommand(
    String toolInvocationId,
    @JsonProperty(required = true)
    String capability,
    Map<String, Object> arguments,
    String llmRationale) implements LlmCommand {

  /**
   * Validates {@code capability}, generates {@code toolInvocationId} when it is null or blank, and
   * defensively copies {@code arguments} (null becomes an empty map).
   */
  public ToolInvocationCommand {
    Validate.notBlank(capability, "ToolInvocationCommand capability must not be blank");
    toolInvocationId = StringUtils.defaultIfBlank(toolInvocationId, UUID.randomUUID().toString());
    arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
  }
}
