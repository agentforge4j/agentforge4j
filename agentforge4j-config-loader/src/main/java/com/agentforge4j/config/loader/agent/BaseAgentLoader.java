// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.config.loader.AgentLoader;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared loading flow for agent bundle sources.
 */
@RequiredArgsConstructor
public abstract class BaseAgentLoader implements AgentLoader {

  protected final ObjectMapper objectMapper;
  private final String loaderName;

  protected static final String AGENT_DIR_SUFFIX = ".agent";
  protected static final String AGENT_FILE_NAME = "agent.json";
  protected static final String SYSTEM_PROMPT_FILE_NAME = "systemprompt.md";
  protected static final String BOUNDARIES_FILE_NAME = "boundaries.md";

  protected abstract void log(System.Logger.Level level, String message, Object... args);

  protected abstract List<String> listAgentDirectories();

  protected abstract AgentDefinitionFile readAgentFile(String entry);

  protected abstract String readSystemPromptFile(String entry);

  protected abstract String readBoundariesFile(String entry);

  @Override
  public Map<String, AgentDefinition> loadAgents() {
    log(System.Logger.Level.INFO, "Loading agents for {0}", loaderName);
    return loadAgents(listAgentDirectories());
  }

  @Override
  public Map<String, AgentDefinition> loadAgents(List<String> bundleFiles) {
    Map<String, AgentDefinition> loaded = new LinkedHashMap<>();
    List<String> filteredList = bundleFiles
        .stream()
        .filter(StringUtils::isNotBlank)
        .filter(entry -> entry.endsWith(AGENT_DIR_SUFFIX))
        .map(this::normalizeAgentBundleEntry)
        .toList();
    for (String entry : filteredList) {
      AgentDefinitionFile file = readAndValidateAgentFile(entry);
      log(System.Logger.Level.INFO, "Loading agent ID {0}", file.getId());
      AgentDefinition definition = file.toDefinition(resolveSystemPrompt(file, entry));
      validateDuplicateAgent(loaded, definition);
    }
    return Map.copyOf(loaded);
  }

  private String resolveSystemPrompt(AgentDefinitionFile file, String agentId) {
    if (StringUtils.isNotBlank(file.getSystemPrompt())) {
      return file.getSystemPrompt();
    }
    String basePrompt = Validate.notBlank(readSystemPromptFile(agentId),
        "Agent must define non-blank inline systemPrompt or provide systemprompt.md in shipped resources");
    String boundariesPrompt = readBoundariesFile(agentId);
    if (StringUtils.isNotBlank(boundariesPrompt)) {
      return basePrompt + System.lineSeparator() + System.lineSeparator() + boundariesPrompt;
    }
    return basePrompt;
  }

  /**
   * Normalizes a raw index or directory entry to the form expected by {@link #readAgentFile}.
   */
  private String normalizeAgentBundleEntry(String raw) {
    return raw.substring(Math.max(raw.lastIndexOf('/') + 1, 0));
  }

  private AgentDefinitionFile readAndValidateAgentFile(String loadEntry) {
    AgentDefinitionFile file = readAgentFile(loadEntry);
    validateAgentDefinitionFile(file, loadEntry);
    Validate.isTrue(file.getId().equals(deriveAgentId(loadEntry)),
        "Agent id '%s' must match bundle id '%s'".formatted(file.getId(), loadEntry));
    return file;
  }

  private String deriveAgentId(String loadEntry) {
    if (loadEntry.endsWith(AGENT_DIR_SUFFIX)) {
      return loadEntry.substring(0, loadEntry.length() - AGENT_DIR_SUFFIX.length());
    }
    return loadEntry;
  }

  private void validateDuplicateAgent(Map<String, AgentDefinition> loaded,
      AgentDefinition definition) {
    AgentDefinition previous = loaded.put(definition.id(), definition);
    Validate.isTrue(previous == null,
        "Duplicate agent id '%s' in %s (%s)"
            .formatted(definition.id(), loaderName, loaded.keySet()));
  }

  private void validateAgentDefinitionFile(AgentDefinitionFile definition, String entry) {
    Validate.notNull(definition, "Agent file produced null definition: %s".formatted(entry));
    Validate.notBlank(definition.getId(), "Agent id is required: %s".formatted(entry));
    Validate.notBlank(definition.getName(), "Agent name is required: %s".formatted(entry));
    Validate.notNull(definition.getLocality(), "Agent locality is required: %s".formatted(entry));
    Validate.notEmpty(definition.getProviderPreferences(),
        "Agent must define at least one providerPreference: %s".formatted(entry));
  }
}
