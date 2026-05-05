package com.agentforge4j.workflows;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentBundleLocatorTest {

  @Test
  void shippedAgentIds_returnsEmptyWhenShippedAgentIndexIsEmpty() {
    assertThat(AgentBundleLocator.shippedAgentIds()).isEmpty();
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
