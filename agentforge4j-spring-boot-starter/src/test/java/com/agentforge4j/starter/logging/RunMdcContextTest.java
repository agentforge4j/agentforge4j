// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RunMdcContextTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void fourArgFactoryPutsAllIdentifiers() {
    try (RunMdcContext ignored = RunMdcContext.of("r", "w", "s", "a")) {
      assertThat(MDC.get("runId")).isEqualTo("r");
      assertThat(MDC.get("workflowId")).isEqualTo("w");
      assertThat(MDC.get("stepId")).isEqualTo("s");
      assertThat(MDC.get("agentId")).isEqualTo("a");
    }
  }

  @Test
  void closeRestoresPreviousMdcValues() {
    MDC.put("runId", "before");
    MDC.put("workflowId", "wf-before");
    MDC.put("stepId", "step-before");
    MDC.put("agentId", "agent-before");
    try (RunMdcContext ignored = RunMdcContext.of("during", "wf-during", "step-during", "agent-during")) {
      assertThat(MDC.get("runId")).isEqualTo("during");
      assertThat(MDC.get("workflowId")).isEqualTo("wf-during");
      assertThat(MDC.get("stepId")).isEqualTo("step-during");
      assertThat(MDC.get("agentId")).isEqualTo("agent-during");
    }
    assertThat(MDC.get("runId")).isEqualTo("before");
    assertThat(MDC.get("workflowId")).isEqualTo("wf-before");
    assertThat(MDC.get("stepId")).isEqualTo("step-before");
    assertThat(MDC.get("agentId")).isEqualTo("agent-before");
  }

  @Test
  void blankValueRemovesKey() {
    try (RunMdcContext ignored = RunMdcContext.of("r", "w", "  ", "a")) {
      assertThat(MDC.get("stepId")).isNull();
      assertThat(MDC.get("agentId")).isEqualTo("a");
    }
  }
}
