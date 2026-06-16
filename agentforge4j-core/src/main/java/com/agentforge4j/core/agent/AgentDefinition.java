// SPDX-License-Identifier: Apache-2.0
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
    String version,
    String modelTier
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
   * @param modelTier optional capability tier name ({@code LITE}/{@code STANDARD}/{@code POWERFUL})
   *                  resolved to a concrete model at invocation time; {@code null} when the agent
   *                  relies on raw provider model pins or provider defaults. Stored as the tier name
   *                  (a String) so {@code core} stays free of the {@code llm-api} enum; the name is
   *                  validated where it is converted, at the runtime invocation boundary
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

  /**
   * Returns a new {@link Builder} for assembling an {@link AgentDefinition} without positional
   * arguments. Optional fields ({@code supportedCommands}, {@code author}, {@code contact},
   * {@code modelTier}) may be left unset; the required fields are validated when
   * {@link Builder#build()} is called.
   *
   * @return new builder; never {@code null}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for {@link AgentDefinition}. Lets callers omit the optional {@code modelTier}
   * (and other optional fields) without passing a trailing {@code null} to the canonical
   * constructor. Validation is deferred to {@link #build()}, which delegates to the canonical
   * constructor.
   */
  public static final class Builder {

    private String id;
    private String name;
    private AgentLocality locality;
    private boolean enabled;
    private String systemPrompt;
    private List<ProviderPreference> providerPreferences;
    private List<String> supportedCommands;
    private String author;
    private String contact;
    private String version;
    private String modelTier;

    private Builder() {
      // obtain via AgentDefinition.builder()
    }

    /**
     * Sets the unique agent identifier.
     *
     * @param id unique identifier for the agent
     *
     * @return this builder
     */
    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets the human-readable agent name.
     *
     * @param name human-readable name of the agent
     *
     * @return this builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets whether the agent runs locally or in the cloud.
     *
     * @param locality agent locality
     *
     * @return this builder
     */
    public Builder withLocality(AgentLocality locality) {
      this.locality = locality;
      return this;
    }

    /**
     * Sets whether the agent is enabled for use.
     *
     * @param enabled {@code true} when the agent is enabled
     *
     * @return this builder
     */
    public Builder withEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * Sets the system prompt used by the agent.
     *
     * @param systemPrompt the system prompt
     *
     * @return this builder
     */
    public Builder withSystemPrompt(String systemPrompt) {
      this.systemPrompt = systemPrompt;
      return this;
    }

    /**
     * Sets the ordered provider preferences for LLM execution.
     *
     * @param providerPreferences ordered list of provider preferences
     *
     * @return this builder
     */
    public Builder withProviderPreferences(List<ProviderPreference> providerPreferences) {
      this.providerPreferences = providerPreferences;
      return this;
    }

    /**
     * Sets the command types this agent can execute.
     *
     * @param supportedCommands list of supported commands; {@code null} defaults to an empty list
     *
     * @return this builder
     */
    public Builder withSupportedCommands(List<String> supportedCommands) {
      this.supportedCommands = supportedCommands;
      return this;
    }

    /**
     * Sets the author of the agent definition.
     *
     * @param author the author
     *
     * @return this builder
     */
    public Builder withAuthor(String author) {
      this.author = author;
      return this;
    }

    /**
     * Sets contact information for the agent author.
     *
     * @param contact contact information
     *
     * @return this builder
     */
    public Builder withContact(String contact) {
      this.contact = contact;
      return this;
    }

    /**
     * Sets the version of the agent definition.
     *
     * @param version version string
     *
     * @return this builder
     */
    public Builder withVersion(String version) {
      this.version = version;
      return this;
    }

    /**
     * Sets the optional capability tier name ({@code LITE}/{@code STANDARD}/{@code POWERFUL}).
     *
     * @param modelTier capability tier name, or {@code null} for none
     *
     * @return this builder
     */
    public Builder withModelTier(String modelTier) {
      this.modelTier = modelTier;
      return this;
    }

    /**
     * Builds the validated {@link AgentDefinition}.
     *
     * @return immutable agent definition; never {@code null}
     */
    public AgentDefinition build() {
      return new AgentDefinition(id, name, locality, enabled, systemPrompt, providerPreferences,
          supportedCommands, author, contact, version, modelTier);
    }
  }
}
