// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.workflows;

import com.agentforge4j.util.Validate;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves shipped agent bundle resources from the classpath.
 */
public final class AgentBundleLocator {

  private static final String SHIPPED_AGENTS_PATH = "/shipped-agents/";
  private static final List<String> SHIPPED_AGENT_IDS = new ArrayList<>();

  static {
    loadShippedAgentIds();
  }

  private AgentBundleLocator() {
  }

  /**
   * Returns agent ids listed in the shipped agent index.
   *
   * @return immutable list of shipped agent ids
   */
  public static List<String> shippedAgentIds() {
    return List.copyOf(SHIPPED_AGENT_IDS);
  }

  /**
   * Resolves the {@code agent.json} resource for a shipped agent id.
   *
   * @param agentId agent id listed in the shipped agent index
   * @return classpath URL of the agent definition
   * @throws IllegalStateException when the resource is not present
   */
  public static URL locateAgentJson(String agentId) {
    String path = SHIPPED_AGENTS_PATH + agentId + ".agent/agent.json";
    return Validate.notNull(AgentBundleLocator.class.getResource(path),
        () -> new IllegalStateException(
            "Missing shipped agent resource: %s".formatted(path)));
  }

  /**
   * Resolves the {@code systemprompt.md} resource for a shipped agent id.
   *
   * @param agentId agent id listed in the shipped agent index
   * @return classpath URL of the system prompt
   * @throws IllegalStateException when the resource is not present
   */
  public static URL locateSystemPrompt(String agentId) {
    String path = SHIPPED_AGENTS_PATH + agentId + ".agent/systemprompt.md";
    return Validate.notNull(AgentBundleLocator.class.getResource(path),
        () -> new IllegalStateException(
            "Missing shipped agent systemprompt: %s".formatted(path)));
  }

  /**
   * Resolves the optional {@code boundaries.md} resource for a shipped agent id.
   *
   * @param agentId agent id listed in the shipped agent index
   * @return classpath URL of boundaries content, or {@code null} when absent
   */
  public static URL locateBoundariesPrompt(String agentId) {
    String path = SHIPPED_AGENTS_PATH + agentId + ".agent/boundaries.md";
    return AgentBundleLocator.class.getResource(path);
  }

  private static void loadShippedAgentIds() {
    URL resource = AgentBundleLocator.class.getResource(SHIPPED_AGENTS_PATH + "index");
    if (resource != null) {
      try (InputStream stream = resource.openStream()) {
        String[] agentIds = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
        SHIPPED_AGENT_IDS.addAll(Arrays.stream(agentIds).toList().stream()
            .filter(id -> !id.isBlank()).toList());
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to read shipped agent index resource: %s".formatted(resource), e);
      }
    }
  }
}
