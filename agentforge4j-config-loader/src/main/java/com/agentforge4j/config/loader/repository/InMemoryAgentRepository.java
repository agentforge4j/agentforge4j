package com.agentforge4j.config.loader.repository;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.exception.AgentNotFoundException;
import com.agentforge4j.util.Validate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory {@link AgentRepository} whose global snapshot can be replaced atomically.
 */
public final class InMemoryAgentRepository implements AgentRepository {
  private final AtomicReference<Map<String, AgentDefinition>> globalAgentsRef;

  /**
   * Creates a repository from an initial global agent snapshot.
   *
   * @param globalAgents initial agents keyed by id
   */
  public InMemoryAgentRepository(Map<String, AgentDefinition> globalAgents) {
    this.globalAgentsRef = new AtomicReference<>(Map.copyOf(globalAgents));
  }

  /**
   * Returns an agent by id from the current global snapshot.
   *
   * @param id agent id
   * @return matching agent definition
   * @throws AgentNotFoundException when no agent exists for the id
   */
  @Override
  public AgentDefinition get(String id) {
    Validate.notBlank(id, "Agent id must not be blank");
    return Optional.ofNullable(globalAgentsRef.get().get(id))
        .orElseThrow(() -> new AgentNotFoundException(id));
  }

  /**
   * Returns all agents from the current global snapshot.
   *
   * @return immutable map of all agents keyed by id
   */
  @Override
  public Map<String, AgentDefinition> findAll() {
    return Map.copyOf(globalAgentsRef.get());
  }

  /**
   * Replaces the global agent snapshot used by this repository.
   *
   * @param globalAgents replacement agents keyed by id
   */
  public void replaceGlobalAgents(Map<String, AgentDefinition> globalAgents) {
    globalAgentsRef.set(Map.copyOf(globalAgents));
  }
}
