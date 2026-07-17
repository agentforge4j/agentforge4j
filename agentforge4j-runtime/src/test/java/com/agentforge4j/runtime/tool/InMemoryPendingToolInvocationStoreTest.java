// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void findByRunReturnsOnlyThatRunsPendingInvocations() {
    PendingToolInvocation a = pending("run-1", "tool-a");
    PendingToolInvocation b = pending("run-1", "tool-b");
    store.save(a);
    store.save(b);
    store.save(pending("run-2", "tool-c"));

    assertThat(store.findByRun("run-1")).containsExactlyInAnyOrder(a, b);
    assertThat(store.findByRun("run-2")).extracting(PendingToolInvocation::toolInvocationId)
        .containsExactly("tool-c");
  }

  @Test
  void findByRunIsEmptyForARunWithNonePending() {
    store.save(pending("run-1", "tool-a"));

    assertThat(store.findByRun("run-2")).isEmpty();
  }

  @Test
  void findByRunRejectsBlankRunId() {
    assertThatThrownBy(() -> store.findByRun(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void claimReturnsTheRowAndRemovesItWhenTheExpectedRowMatches() {
    PendingToolInvocation pending = pending("run-1", "tool-x");
    store.save(pending);

    assertThat(store.claim("run-1", "tool-x", pending)).isSameAs(pending);
    assertThat(store.find("run-1", "tool-x")).isNull();
  }

  @Test
  void claimIsANoOpReturningNullWhenTheRowIsAlreadyGone() {
    PendingToolInvocation neverSaved = pending("run-1", "missing");
    assertThat(store.claim("run-1", "missing", neverSaved)).isNull();
  }

  @Test
  void claimFailsWithoutConsumingTheRowWhenTheStoredRowNoLongerMatchesExpected() {
    PendingToolInvocation original = pending("run-1", "tool-x");
    store.save(original);
    PendingToolInvocation staleExpectation = pending("run-1", "tool-x", "different reason");

    assertThat(store.claim("run-1", "tool-x", staleExpectation)).isNull();

    // The claim attempt against a stale expectation must not consume whatever row is actually
    // stored: the original row (never replaced in this test) is still there afterwards.
    assertThat(store.find("run-1", "tool-x")).isSameAs(original);
  }

  @Test
  void claimAgainstAReplacedRowFailsWithoutConsumingTheReplacement() {
    PendingToolInvocation original = pending("run-1", "tool-x");
    store.save(original);
    // Simulates a concurrent actor replacing the row (e.g. reSuspendForDecision) between this
    // caller's own find() and its claim attempt.
    PendingToolInvocation replacement = pending("run-1", "tool-x", "policy denied on replacement");
    store.remove("run-1", "tool-x");
    store.save(replacement);

    assertThat(store.claim("run-1", "tool-x", original)).isNull();

    // The replacement must remain resolvable through a later Continue/Reject — not silently
    // consumed by a claim attempt authorized under the stale, pre-replacement row.
    assertThat(store.find("run-1", "tool-x")).isSameAs(replacement);
  }

  @Test
  void claimIsRunScopedAndLeavesAnotherRunsEntryIntact() {
    PendingToolInvocation inRun1 = pending("run-1", "tool-x");
    store.save(inRun1);
    PendingToolInvocation inRun2 = pending("run-2", "tool-x");
    store.save(inRun2);

    store.claim("run-1", "tool-x", inRun1);

    assertThat(store.find("run-1", "tool-x")).isNull();
    assertThat(store.find("run-2", "tool-x")).isSameAs(inRun2);
  }

  @Test
  void onlyOneOfTwoConcurrentClaimsOnTheSameInvocationSucceeds() throws InterruptedException {
    PendingToolInvocation pending = pending("run-1", "tool-x");
    store.save(pending);
    int threadCount = 8;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    AtomicInteger successes = new AtomicInteger();
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      Thread thread = new Thread(() -> {
        try {
          barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
          throw new RuntimeException(e);
        }
        if (store.claim("run-1", "tool-x", pending) != null) {
          successes.incrementAndGet();
        }
      });
      threads.add(thread);
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(successes.get()).isEqualTo(1);
    assertThat(store.find("run-1", "tool-x")).isNull();
  }

  @Test
  void claimRejectsBlankIdentifiersOrNullExpectedPending() {
    PendingToolInvocation pending = pending("run-1", "tool-x");
    assertThatThrownBy(() -> store.claim(" ", "tool-x", pending))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.claim("run-1", " ", pending))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.claim("run-1", "tool-x", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static PendingToolInvocation pending(String runId, String toolInvocationId) {
    return new PendingToolInvocation(toolInvocationId, runId, "7", "agent-1", "wf-1",
        "github.create_pull_request", "{\"title\":\"x\"}", "because", "needs review", "OPERATOR",
        PendingToolInvocation.Origin.APPROVAL_REQUIRED, CREATED_AT);
  }

  private static PendingToolInvocation pending(String runId, String toolInvocationId,
      String reason) {
    return new PendingToolInvocation(toolInvocationId, runId, "7", "agent-1", "wf-1",
        "github.create_pull_request", "{\"title\":\"x\"}", "because", reason, null,
        PendingToolInvocation.Origin.POLICY_DENIED, CREATED_AT);
  }
}
