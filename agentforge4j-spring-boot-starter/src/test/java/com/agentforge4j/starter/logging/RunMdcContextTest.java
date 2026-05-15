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
  void twoArgFactorySetsRunAndWorkflowClearsStepAndAgentKeys() {
    try (RunMdcContext ignored = RunMdcContext.of("run-1", "wf-1")) {
      assertThat(MDC.get("runId")).isEqualTo("run-1");
      assertThat(MDC.get("workflowId")).isEqualTo("wf-1");
      assertThat(MDC.get("stepId")).isNull();
      assertThat(MDC.get("agentId")).isNull();
    }
    assertThat(MDC.get("runId")).isNull();
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
    try (RunMdcContext ignored = RunMdcContext.of("during", "wf-during")) {
      assertThat(MDC.get("runId")).isEqualTo("during");
    }
    assertThat(MDC.get("runId")).isEqualTo("before");
    assertThat(MDC.get("workflowId")).isEqualTo("wf-before");
  }

  @Test
  void blankValueRemovesKey() {
    try (RunMdcContext ignored = RunMdcContext.of("r", "w", "  ", "a")) {
      assertThat(MDC.get("stepId")).isNull();
      assertThat(MDC.get("agentId")).isEqualTo("a");
    }
  }

  @Test
  void withStepAndWithAgentAreFluent() {
    try (RunMdcContext ctx = RunMdcContext.of("r", "w").withStep("s1").withAgent("bot")) {
      assertThat(MDC.get("stepId")).isEqualTo("s1");
      assertThat(MDC.get("agentId")).isEqualTo("bot");
    }
  }
}
