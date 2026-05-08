package com.agentforge4j.workflows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentBundleLocatorTest {

  @Test
  void shippedAgentIds_loadsIdsFromTestClasspathIndex() {
    assertThat(AgentBundleLocator.shippedAgentIds()).containsExactly("locator-test-agent");
  }

  @Test
  void shippedAgentIds_returnsImmutableCopy() {
    var shippedAgentIds = AgentBundleLocator.shippedAgentIds();

    assertThatThrownBy(() -> shippedAgentIds.add("should-fail"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void locateAgentJson_returnsUrlForShippedTestAgent() {
    assertThat(AgentBundleLocator.locateAgentJson("locator-test-agent")).isNotNull();
  }

  @Test
  void locateSystemPrompt_opensReadableContentForShippedTestAgent() throws IOException {
    try (var stream = AgentBundleLocator.locateSystemPrompt("locator-test-agent").openStream()) {
      String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(text).contains("Locator test system prompt");
    }
  }

  @Test
  void locateBoundariesPrompt_returnsNullWhenOptionalFileAbsent() {
    assertThat(AgentBundleLocator.locateBoundariesPrompt("locator-test-agent")).isNull();
  }

  @Test
  void locateAgentJson_throwsForUnknownAgent() {
    assertThatThrownBy(() -> AgentBundleLocator.locateAgentJson("unknown-agent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing shipped agent resource");
  }

  @Test
  void locateSystemPrompt_throwsForUnknownAgent() {
    assertThatThrownBy(() -> AgentBundleLocator.locateSystemPrompt("unknown-agent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing shipped agent systemprompt");
  }

  @Test
  void locateBoundariesPrompt_returnsNullWhenOptionalFileMissing() {
    assertThat(AgentBundleLocator.locateBoundariesPrompt("unknown-agent")).isNull();
  }
}
