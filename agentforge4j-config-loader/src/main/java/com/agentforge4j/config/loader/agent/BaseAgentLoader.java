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
  protected static final String SYSTEM_PROMPT_FILE_NAME =
      AgentDefinitionAssembler.SYSTEM_PROMPT_FILE_NAME;
  protected static final String BOUNDARIES_FILE_NAME =
      AgentDefinitionAssembler.BOUNDARIES_FILE_NAME;

  private final AgentDefinitionAssembler assembler = new AgentDefinitionAssembler();

  protected abstract void log(System.Logger.Level level, String message, Object... args);

  protected abstract List<String> listAgentDirectories();

  protected abstract AgentDefinitionFile readAgentFile(String entry);

  protected abstract String readSystemPromptFile(String entry);

  protected abstract String readBoundariesFile(String entry);

  @Override
  public Map<String, AgentDefinition> loadAgents() {
    log(System.Logger.Level.INFO, "Loading agents for {0}", loaderName);
    // listAgentDirectories() for a top-level shipped catalog (AgentBundleLocator.shippedAgentIds())
    // returns bare ids with no .agent suffix (e.g. "requirement-structurer"), unlike
    // workflow-bundle-local index entries (e.g. "agents/agent-author.agent"), which already carry
    // both a path prefix and the suffix. loadAgents(List) below relies on the suffix to recognize an
    // agent entry, so bare ids must be suffixed here, at the one call site that knows they are bare.
    List<String> suffixed = listAgentDirectories().stream()
        .map(id -> id.endsWith(AGENT_DIR_SUFFIX) ? id : id + AGENT_DIR_SUFFIX)
        .toList();
    return loadAgents(suffixed);
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
      AgentDefinitionFile file = readAgentFile(entry);
      Validate.notNull(file, "Agent file produced null definition: %s".formatted(entry));
      log(System.Logger.Level.INFO, "Loading agent ID {0}", file.getId());
      AgentDefinition definition = assembler.assemble(file, entry, name -> readSibling(entry, name));
      // Loader-specific invariant: the declared id must match the bundle directory id.
      Validate.isTrue(file.getId().equals(deriveAgentId(entry)),
          "Agent id '%s' must match bundle id '%s'".formatted(file.getId(), entry));
      validateDuplicateAgent(loaded, definition);
    }
    return Map.copyOf(loaded);
  }

  private String readSibling(String agentId, String fileName) {
    if (SYSTEM_PROMPT_FILE_NAME.equals(fileName)) {
      return readSystemPromptFile(agentId);
    }
    if (BOUNDARIES_FILE_NAME.equals(fileName)) {
      return readBoundariesFile(agentId);
    }
    return null;
  }

  /**
   * Normalizes a raw index or directory entry to the form expected by {@link #readAgentFile}.
   */
  private String normalizeAgentBundleEntry(String raw) {
    return raw.substring(Math.max(raw.lastIndexOf('/') + 1, 0));
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
}
