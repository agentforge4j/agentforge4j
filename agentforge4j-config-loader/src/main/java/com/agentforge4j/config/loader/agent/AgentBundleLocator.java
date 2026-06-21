// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves shipped agent bundle resources from the classpath.
 *
 * <p>Like {@link com.agentforge4j.config.loader.workflow.WorkflowBundleLocator}, resources are
 * resolved through {@link ClassLoader#getResource(String)} on this class's own loader so a catalog
 * shipped as a separate module/jar is discoverable. The {@code shipped-agents} root contains a
 * hyphen and is therefore not JPMS-encapsulated. When no catalog is present the shipped agent index
 * is absent and {@link #shippedAgentIds()} is empty.
 */
public final class AgentBundleLocator {

  private static final String SHIPPED_AGENTS_PATH = "shipped-agents/";
  private static final ClassLoader LOADER = AgentBundleLocator.class.getClassLoader();
  private static final List<String> SHIPPED_AGENT_IDS = loadShippedAgentIds();

  private AgentBundleLocator() {
  }

  /**
   * Returns agent ids listed in the shipped agent index.
   *
   * @return immutable list of shipped agent ids, empty when no catalog is present
   */
  public static List<String> shippedAgentIds() {
    return List.copyOf(SHIPPED_AGENT_IDS);
  }

  private static List<String> loadShippedAgentIds() {
    URL resource = LOADER.getResource(SHIPPED_AGENTS_PATH + "index");
    if (resource == null) {
      return List.of();
    }
    try (InputStream stream = resource.openStream()) {
      String[] agentIds = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
      List<String> result = new ArrayList<>();
      for (String agentId : agentIds) {
        String trimmed = agentId.trim();
        if (!trimmed.isBlank()) {
          result.add(trimmed);
        }
      }
      return result;
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read shipped agent index resource: %s".formatted(resource), e);
    }
  }
}
