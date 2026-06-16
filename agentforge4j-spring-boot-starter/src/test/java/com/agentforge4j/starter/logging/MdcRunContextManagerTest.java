// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.runtime.RunContextManager;
import com.agentforge4j.runtime.RunContextManager.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcRunContextManagerTest {

  private final MdcRunContextManager manager = new MdcRunContextManager();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void openPutsIdentifiersOnMdcAndCloseRestoresPriorState() {
    MDC.put("runId", "prior-run");

    try (Scope scope = manager.open("run-a", "wf-a", "step-1", "agent-x")) {
      assertThat(MDC.get("runId")).isEqualTo("run-a");
      assertThat(MDC.get("workflowId")).isEqualTo("wf-a");
      assertThat(MDC.get("stepId")).isEqualTo("step-1");
      assertThat(MDC.get("agentId")).isEqualTo("agent-x");
    }

    assertThat(MDC.get("runId")).isEqualTo("prior-run");
    assertThat(MDC.get("workflowId")).isNull();
    assertThat(MDC.get("stepId")).isNull();
    assertThat(MDC.get("agentId")).isNull();
  }

  @Test
  void returnsAutoCloseableScopeCompatibleWithTryWithResources() {
    try (Scope ignored = manager.open("r", "w", null, null)) {
      assertThat(ignored).isInstanceOf(AutoCloseable.class);
    }
  }
}
