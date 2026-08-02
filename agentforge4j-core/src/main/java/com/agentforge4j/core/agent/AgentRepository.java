// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

import com.agentforge4j.core.exception.AgentNotFoundException;
import java.util.Map;

/**
 * Repository for accessing agent definitions.
 */
public interface AgentRepository {

  /**
   * Retrieves an agent definition by its identifier.
   *
   * @param id the unique identifier of the agent
   * @return the agent definition
   * @throws AgentNotFoundException if no agent with the given id exists
   */
  AgentDefinition get(String id);

  /**
   * Returns all available agent definitions.
   *
   * @return a map of agent ids to their definitions
   */
  Map<String, AgentDefinition> findAll();
}
