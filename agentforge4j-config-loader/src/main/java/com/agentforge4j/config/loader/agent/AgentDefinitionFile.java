package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Mutable DTO representing the JSON shape of an agent bundle file.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AgentDefinitionFile {
  private String id;
  private String name;
  private AgentLocality locality;
  private boolean enabled = true;
  private String systemPrompt;
  private List<ProviderPreference> providerPreferences;
  private List<String> supportedCommands;
  private String author;
  private String contact;
  private String version;

  /**
   * Converts this DTO into an {@link AgentDefinition} using the resolved system prompt content.
   *
   * @param systemPrompt resolved system prompt text
   * @return immutable agent definition
   */
  public AgentDefinition toDefinition(String systemPrompt) {
    return new AgentDefinition(
        id,
        name,
        locality,
        enabled,
        systemPrompt,
        providerPreferences,
        supportedCommands,
        author,
        contact,
        version);
  }
}
