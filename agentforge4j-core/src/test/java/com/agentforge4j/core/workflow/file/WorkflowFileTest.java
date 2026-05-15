package com.agentforge4j.core.workflow.file;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFileTest {

  private static WorkflowFile file(long sizeBytes) {
    return new WorkflowFile(
        "file-1",
        "run-1",
        "step-1",
        "report.txt",
        "/runs/run-1/report.txt",
        "text/plain",
        sizeBytes,
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void accepts_zero_size_bytes() {
    WorkflowFile file = file(0);
    assertThat(file.sizeBytes()).isZero();
  }

  @Test
  void rejects_negative_size_bytes() {
    assertThatThrownBy(() -> file(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sizeBytes");
  }
}
