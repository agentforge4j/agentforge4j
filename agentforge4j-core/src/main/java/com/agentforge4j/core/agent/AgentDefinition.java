package com.agentforge4j.core.agent;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents the definition of an agent, including its configuration and capabilities.
 * Instances are immutable and validated at construction time.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentDefinition(
    String id,
    String name,
    AgentLocality locality,
    boolean enabled,
    String systemPrompt,
    List<ProviderPreference> providerPreferences,
    List<String> supportedCommands,
    String author,
    String contact,
    String version
) {

  /**
   * Creates an AgentDefinition with validation.
   *
   * @param id unique identifier for the agent
   * @param name human-readable name of the agent
   * @param locality whether the agent runs locally or in the cloud
   * @param enabled whether the agent is enabled for use
   * @param systemPrompt the system prompt used by the agent
   * @param providerPreferences ordered list of provider preferences for LLM execution
   * @param supportedCommands list of command types this agent can execute; null defaults to empty list
   * @param author the author of the agent definition
   * @param contact contact information for the agent author
   * @param version version of the agent definition
   */
  public AgentDefinition {
    Validate.notBlank(id, "AgentDefinition id must not be blank");
    Validate.notBlank(name, "AgentDefinition name must not be blank for agent: %s".formatted(id));
    Validate.notNull(locality,
        "AgentDefinition locality must not be null for agent: %s".formatted(id));
    Validate.notBlank(systemPrompt,
        "AgentDefinition systemPrompt must not be blank for agent: %s".formatted(id));
    Validate.notBlank(version,
        "AgentDefinition version must not be blank for agent: %s".formatted(id));
    Validate.notEmpty(providerPreferences,
        "AgentDefinition must have at least one providerPreference for agent: %s".formatted(id));
    providerPreferences = List.copyOf(providerPreferences);
    supportedCommands = supportedCommands != null ? List.copyOf(supportedCommands) : List.of();
  }
}
