// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.verification.support.Fixtures;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Verifies the zero-provider bootstrap contract: when no provider is configured (no
 * {@code withLlmProvider}, no resolver, no env/system-property credential), the bootstrap leaves an
 * <em>empty</em> resolver and logs a WARNING — it never fabricates a default or fake provider. The
 * OSS WARNING is emitted via {@code System.Logger}, which routes to {@code java.util.logging} on the
 * framework classpath, so it is captured with a JUL handler.
 */
class BootstrapZeroProviderTest {

  @Test
  void zeroProviderBootstrapYieldsAnEmptyResolverAndWarnsRatherThanFabricatingADefault() {
    List<LogRecord> records = new CopyOnWriteArrayList<>();
    Logger logger = Logger.getLogger("com.agentforge4j.bootstrap.RuntimeAssembler");
    Handler handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {
        // no-op
      }

      @Override
      public void close() {
        // no-op
      }
    };
    Level priorLevel = logger.getLevel();
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
    try {
      AgentForge4j af = AgentForge4jBootstrap.defaults()
          .withLoadShippedWorkflows(false)
          .withLoadShippedAgents(false)
          .withWorkflowsDir(Fixtures.dir("/fixtures/provider/workflows"))
          .withAgentsDir(Fixtures.dir("/fixtures/provider/agents"))
          .build();

      assertThat(af.components().llmClientResolver().listAvailableClients())
          .as("no providers configured must leave an empty resolver, not a fabricated default")
          .isEmpty();
      assertThat(records)
          .as("the bootstrap must warn that no LLM providers are configured")
          .anyMatch(record -> record.getLevel() == Level.WARNING
              && String.valueOf(record.getMessage()).contains("No LLM providers configured"));
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(priorLevel);
    }
  }
}
