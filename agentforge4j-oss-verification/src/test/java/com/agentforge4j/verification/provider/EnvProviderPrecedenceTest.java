// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.verification.support.CapturingLlmClientFactory;
import com.agentforge4j.verification.support.Fixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

/**
 * Proves the LLM provider-config resolution precedence end-to-end through the public bootstrap
 * discovery path: {@code programmatic > system-property > env > (unset) > skip}. A test-only
 * {@code looptest} provider (non-hyphenated so it survives env normalisation, no API key required)
 * captures the {@code baseUrl} the bootstrap resolved for it, so each test asserts which layer won.
 * The env variable is set with junit-pioneer's {@code @SetEnvironmentVariable} (the dep added for
 * exactly this), and system properties with {@code @SetSystemProperty}; both are restored after the
 * test.
 */
class EnvProviderPrecedenceTest {

  private static final String BASE_URL_KEY = "agentforge4j.llm.looptest.base.url";

  @BeforeEach
  void clearCaptures() {
    CapturingLlmClientFactory.CAPTURED_BASE_URLS.clear();
  }

  private static AgentForge4j build(LlmProviderConfig... programmatic) {
    AgentForge4jBootstrap.Builder bootstrap = AgentForge4jBootstrap.defaults()
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withWorkflowsDir(Fixtures.dir("/fixtures/provider/workflows"))
        .withAgentsDir(Fixtures.dir("/fixtures/provider/agents"));
    for (LlmProviderConfig config : programmatic) {
      bootstrap = bootstrap.withLlmProvider(config);
    }
    return bootstrap.build();
  }

  private static LlmProviderConfig looptest(String baseUrl) {
    return new LlmProviderConfig("looptest", null, baseUrl, null, null, Map.of());
  }

  @Test
  @SetEnvironmentVariable(key = "AGENTFORGE4J_LLM_LOOPTEST_BASE_URL", value = "http://env.example")
  void envValueIsUsedWhenNoHigherLayerSetsIt() {
    build();

    assertThat(CapturingLlmClientFactory.CAPTURED_BASE_URLS)
        .containsExactly("http://env.example");
  }

  @Test
  @SetEnvironmentVariable(key = "AGENTFORGE4J_LLM_LOOPTEST_BASE_URL", value = "http://env.example")
  @SetSystemProperty(key = BASE_URL_KEY, value = "http://sysprop.example")
  void systemPropertyWinsOverEnv() {
    build();

    assertThat(CapturingLlmClientFactory.CAPTURED_BASE_URLS)
        .containsExactly("http://sysprop.example");
  }

  @Test
  @SetEnvironmentVariable(key = "AGENTFORGE4J_LLM_LOOPTEST_BASE_URL", value = "http://env.example")
  @SetSystemProperty(key = BASE_URL_KEY, value = "http://sysprop.example")
  void programmaticConfigWinsOverSystemPropertyAndEnv() {
    build(looptest("http://programmatic.example"));

    assertThat(CapturingLlmClientFactory.CAPTURED_BASE_URLS)
        .containsExactly("http://programmatic.example");
  }

  @Test
  void unconfiguredProviderIsSkippedNotFabricated() {
    AgentForge4j af = build();

    assertThat(CapturingLlmClientFactory.CAPTURED_BASE_URLS)
        .as("a provider with no configuration at any layer must be skipped, not constructed")
        .isEmpty();
    assertThat(af.components().llmClientResolver().listAvailableClients())
        .doesNotContain("looptest");
  }
}
