// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.util.Validate;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared production path that turns a parsed {@link AgentDefinitionFile} into a loadable {@link AgentDefinition}:
 * core-field validation, system-prompt resolution (inline, else a {@code systemprompt.md} sibling optionally appended
 * with {@code boundaries.md}), and conversion via {@link AgentDefinitionFile#toDefinition(String)} (which applies the
 * record's own invariants).
 *
 * <p>Used by both {@link BaseAgentLoader} (resolving siblings from the bundle source) and the
 * generated-artifact validator (resolving siblings from the run's captured artifacts), so "valid" means literally
 * "loads as a valid {@link AgentDefinition}".
 */
public final class AgentDefinitionAssembler {

  /**
   * Sibling file name carrying the agent system prompt when not inlined.
   */
  public static final String SYSTEM_PROMPT_FILE_NAME = "systemprompt.md";
  /**
   * Optional sibling file name appended to the system prompt.
   */
  public static final String BOUNDARIES_FILE_NAME = "boundaries.md";

  /**
   * Resolves a sibling file's content by name, returning {@code null} when absent.
   */
  @FunctionalInterface
  public interface SiblingResolver {

    /**
     * @param fileName sibling file name (for example {@code systemprompt.md})
     *
     * @return the content, or {@code null} when the sibling is not present
     */
    String read(String fileName);
  }

  /**
   * Validates core fields and builds the definition, resolving the system prompt via the resolver.
   *
   * @param file        parsed agent file; must not be {@code null}
   * @param describedAs label used in validation messages (bundle entry or artifact path)
   * @param siblings    resolver for {@code systemprompt.md} / {@code boundaries.md}
   *
   * @return the loadable agent definition
   *
   * @throws IllegalArgumentException if a core field is invalid or the system prompt cannot be resolved
   */
  public AgentDefinition assemble(AgentDefinitionFile file, String describedAs, SiblingResolver siblings) {
    validate(file, describedAs);
    Validate.notNull(siblings, "siblings resolver must not be null");
    return file.toDefinition(resolveSystemPrompt(file, siblings));
  }

  /**
   * Validates the core required fields shared by every loader.
   *
   * @param file        parsed agent file
   * @param describedAs label used in validation messages
   */
  public void validate(AgentDefinitionFile file, String describedAs) {
    Validate.notNull(file, "Agent file produced null definition: %s".formatted(describedAs));
    Validate.notBlank(file.getId(), "Agent id is required: %s".formatted(describedAs));
    Validate.notBlank(file.getName(), "Agent name is required: %s".formatted(describedAs));
    Validate.notNull(file.getLocality(), "Agent locality is required: %s".formatted(describedAs));
    Validate.notEmpty(file.getProviderPreferences(),
        "Agent must define at least one providerPreference: %s".formatted(describedAs));
  }

  private String resolveSystemPrompt(AgentDefinitionFile file, SiblingResolver siblings) {
    if (StringUtils.isNotBlank(file.getSystemPrompt())) {
      return file.getSystemPrompt();
    }
    String basePrompt = Validate.notBlank(siblings.read(SYSTEM_PROMPT_FILE_NAME),
        "Agent must define non-blank inline systemPrompt or provide systemprompt.md");
    String boundariesPrompt = siblings.read(BOUNDARIES_FILE_NAME);
    if (StringUtils.isNotBlank(boundariesPrompt)) {
      return basePrompt + System.lineSeparator() + System.lineSeparator() + boundariesPrompt;
    }
    return basePrompt;
  }
}
