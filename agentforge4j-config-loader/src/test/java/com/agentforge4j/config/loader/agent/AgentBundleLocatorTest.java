// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the shipped agent locator reads the shipped agent index from the classpath after moving
 * into {@code config-loader} and switching to {@link ClassLoader}-based lookup. Backed by the
 * synthetic {@code shipped-agents/index} fixture on the test classpath.
 */
class AgentBundleLocatorTest {

  @Test
  void shippedAgentIds_listsTheClasspathFixture() {
    assertThat(AgentBundleLocator.shippedAgentIds()).contains("shipped-test");
  }
}
