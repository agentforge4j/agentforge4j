// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.workflow.state.WorkflowState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for {@link RetryPolicyAttemptCounter#reserve} being a single atomic
 * check-and-increment rather than a separate {@code read} followed by a separate {@code increment}.
 * {@code DefaultWorkflowRuntime.enforceRetryPolicy} (backing {@code WorkflowRuntime.retry()}) and
 * {@code RetryPreviousBehaviourHandler.reserveSharedRetryPolicyCeiling} both delegate to this exact
 * method for the exact same shared counter on the same {@link WorkflowState} — exercising it under
 * real concurrency here covers both the same-mechanism race (two concurrent {@code retry()} calls)
 * and the cross-mechanism race ({@code retry()} racing a {@code RETRY_PREVIOUS} step) at once, since
 * neither caller has any additional state of its own that could diverge the outcome.
 */
class RetryPolicyAttemptCounterConcurrencyTest {

  private static final String STEP_ID = "s1";

  @Test
  void twoConcurrentReservationsAtACeilingOfOneGrantExactlyOne() throws Exception {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null,
        Instant.parse("2026-07-01T12:00:00Z"));

    CountDownLatch bothReady = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    Callable<Boolean> attempt = () -> {
      bothReady.countDown();
      release.await();
      return RetryPolicyAttemptCounter.reserve(state, STEP_ID, 1);
    };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first = executor.submit(attempt);
      Future<Boolean> second = executor.submit(attempt);
      bothReady.await();
      release.countDown();

      boolean firstGranted = first.get();
      boolean secondGranted = second.get();

      assertThat(firstGranted ^ secondGranted)
          .as("exactly one of two racing reservations at ceiling=1 must be granted")
          .isTrue();
      assertThat(RetryPolicyAttemptCounter.read(state, STEP_ID)).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void manyConcurrentReservationsNeverExceedTheCeiling() throws Exception {
    WorkflowState state = new WorkflowState("run-2", "wf-1", null,
        Instant.parse("2026-07-01T12:00:00Z"));
    int ceiling = 10;
    int contendingThreads = 50;

    CountDownLatch allReady = new CountDownLatch(contendingThreads);
    CountDownLatch release = new CountDownLatch(1);
    Callable<Boolean> attempt = () -> {
      allReady.countDown();
      release.await();
      return RetryPolicyAttemptCounter.reserve(state, STEP_ID, ceiling);
    };

    ExecutorService executor = Executors.newFixedThreadPool(contendingThreads);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < contendingThreads; i++) {
        futures.add(executor.submit(attempt));
      }
      allReady.await();
      release.countDown();

      AtomicInteger grantedCount = new AtomicInteger();
      for (Future<Boolean> future : futures) {
        if (future.get()) {
          grantedCount.incrementAndGet();
        }
      }

      assertThat(grantedCount.get()).isEqualTo(ceiling);
      assertThat(RetryPolicyAttemptCounter.read(state, STEP_ID)).isEqualTo(ceiling);
    } finally {
      executor.shutdownNow();
    }
  }
}
