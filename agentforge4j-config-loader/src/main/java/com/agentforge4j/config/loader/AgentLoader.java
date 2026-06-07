package com.agentforge4j.config.loader;

import com.agentforge4j.core.agent.AgentDefinition;

import java.util.List;
import java.util.Map;

/**
 * Loads agent definitions from a backing source.
 */
public interface AgentLoader {

  /**
   * Loads all agents available under a directory.
   *
   * @return agents keyed by id
   * @throws RuntimeException when the source cannot be read or contains invalid definitions
   */
  Map<String, AgentDefinition> loadAgents();

  Map<String, AgentDefinition> loadAgents(List<String> bundleFiles);
}
