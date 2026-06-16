// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryPendingToolInvocationStoreTest {

  private static final Instant CREATED_AT = Instant.parse("2026-05-01T00:00:00Z");

  private final InMemoryPendingToolInvocationStore store = new InMemoryPendingToolInvocationStore();

  @Test
  void saveThenFindReturnsTheSameInvocation() {
    PendingToolInvocation pending = pending("run-1", "tool-x");

    store.save(pending);

    assertThat(store.find("run-1", "tool-x")).isSameAs(pending);
  }

  @Test
  void findIsRunScopedSoTheSameInvocationIdInAnotherRunIsNotReturned() {
    store.save(pending("run-1", "tool-x"));

    assertThat(store.find("run-2", "tool-x")).isNull();
  }

  @Test
  void invocationsWithTheSameIdInDifferentRunsStayIsolated() {
    PendingToolInvocation inRun1 = pending("run-1", "tool-x");
    PendingToolInvocation inRun2 = pending("run-2", "tool-x");
    store.save(inRun1);
    store.save(inRun2);

    assertThat(store.find("run-1", "tool-x")).isSameAs(inRun1);
    assertThat(store.find("run-2", "tool-x")).isSameAs(inRun2);
  }

  @Test
  void removeIsRunScopedAndLeavesAnotherRunsEntryIntact() {
    store.save(pending("run-1", "tool-x"));
    PendingToolInvocation inRun2 = pending("run-2", "tool-x");
    store.save(inRun2);

    store.remove("run-1", "tool-x");

    assertThat(store.find("run-1", "tool-x")).isNull();
    assertThat(store.find("run-2", "tool-x")).isSameAs(inRun2);
  }

  @Test
  void findForAnUnknownInvocationReturnsNull() {
    assertThat(store.find("run-1", "missing")).isNull();
  }

  @Test
  void removeOfAnUnknownInvocationIsANoOp() {
    store.save(pending("run-1", "tool-x"));

    store.remove("run-1", "missing");

    assertThat(store.find("run-1", "tool-x")).isNotNull();
  }

  @Test
  void saveRejectsNull() {
    assertThatThrownBy(() -> store.save(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findRejectsBlankIdentifiers() {
    assertThatThrownBy(() -> store.find(" ", "tool-x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.find("run-1", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void removeRejectsBlankIdentifiers() {
    assertThatThrownBy(() -> store.remove(" ", "tool-x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.remove("run-1", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static PendingToolInvocation pending(String runId, String toolInvocationId) {
    return new PendingToolInvocation(toolInvocationId, runId, "7", "agent-1", "wf-1",
        "github.create_pull_request", "{\"title\":\"x\"}", "because", "needs review", "OPERATOR",
        CREATED_AT);
  }
}
