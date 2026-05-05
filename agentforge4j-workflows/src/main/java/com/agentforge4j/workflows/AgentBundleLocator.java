package com.agentforge4j.workflows;

import com.agentforge4j.util.Validate;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AgentBundleLocator {

  private static final String SHIPPED_AGENTS_PATH = "/shipped-agents/";
  private static final List<String> SHIPPED_AGENT_IDS = new ArrayList<>();

  static {
    loadShippedAgentIds();
  }

  private AgentBundleLocator() {
  }

  public static List<String> shippedAgentIds() {
    return SHIPPED_AGENT_IDS;
  }

  public static URL locateAgentJson(String agentId) {
    String path = SHIPPED_AGENTS_PATH + agentId + ".agent/agent.json";
    return Validate.notNull(AgentBundleLocator.class.getResource(path),
        () -> new IllegalStateException(
            "Missing shipped agent resource: %s".formatted(path)));
  }

  public static URL locateSystemPrompt(String agentId) {
    String path = SHIPPED_AGENTS_PATH + agentId + ".agent/systemprompt.md";
    return Validate.notNull(AgentBundleLocator.class.getResource(path),
        () -> new IllegalStateException(
            "Missing shipped agent systemprompt: %s".formatted(path)));
  }

  public static URL locateBoundariesPrompt(String agentId) {
    String path = SHIPPED_AGENTS_PATH + agentId + ".agent/boundaries.md";
    return AgentBundleLocator.class.getResource(path);
  }

  private static void loadShippedAgentIds() {
    URL resource = AgentBundleLocator.class.getResource(SHIPPED_AGENTS_PATH + "index");
    if (resource != null) {
      try (InputStream stream = resource.openStream()) {
        String[] agentIds = new String(stream.readAllBytes()).split("\\R");
        SHIPPED_AGENT_IDS.addAll(Arrays.stream(agentIds).toList().stream()
            .filter(id -> !id.isBlank()).toList());
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to read shipped agent index resource: %s".formatted(resource), e);
      }
    }
  }
}
