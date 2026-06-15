package com.agentforge4j.llm.fake;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeRunLifecycleTest {

  private static final FakeScriptKey KEY = new FakeScriptKey("wf", "s1", "a1", 0);
  private static final FakeScriptKey KEY_1 = new FakeScriptKey("wf", "s1", "a1", 1);

  private static FakeInvocation invocation(String runId) {
    return new FakeInvocation("wf", runId, "s1", "a1");
  }

  private static FakeScript script(FakeResponse zero) {
    return new FakeScript(1, Map.of(KEY, zero));
  }

  @Test
  void deregister_clearsScriptAndCounters_noLeak() {
    FakeRunLifecycle lifecycle = new FakeRunLifecycle();
    lifecycle.register("run-1", script(new FakeResponse("zero", null)));
    lifecycle.nextResponse(invocation("run-1")); // create a counter

    lifecycle.deregister("run-1");

    assertThat(lifecycle.containsRun("run-1")).isFalse();
    assertThat(lifecycle.isRegistered("run-1")).isFalse();
    // A fresh registration starts the sequence again at ordinal 0.
    lifecycle.register("run-1", script(new FakeResponse("zero-again", null)));
    assertThat(lifecycle.nextResponse(invocation("run-1")))
        .isInstanceOf(FakeResolution.Found.class);
  }

  @Test
  void register_replacingScript_resetsCounters() {
    FakeRunLifecycle lifecycle = new FakeRunLifecycle();
    lifecycle.register("run-1", new FakeScript(1, Map.of(
        KEY, new FakeResponse("zero", null), KEY_1, new FakeResponse("one", null))));
    lifecycle.nextResponse(invocation("run-1")); // consumes ordinal 0
    lifecycle.nextResponse(invocation("run-1")); // consumes ordinal 1

    lifecycle.register("run-1", new FakeScript(1, Map.of(KEY, new FakeResponse("fresh", null))));

    FakeResolution resolution = lifecycle.nextResponse(invocation("run-1"));
    assertThat(resolution).isInstanceOf(FakeResolution.Found.class);
    assertThat(((FakeResolution.Found) resolution).response().responseText()).isEqualTo("fresh");
  }

  @Test
  void ttl_evictsStaleRuns_butKeepsActiveRunsFresh() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    FakeRunLifecycle lifecycle = new FakeRunLifecycle(clock, Duration.ofMinutes(10), 0);
    lifecycle.register("stale", script(new FakeResponse("s", null)));
    lifecycle.register("active", script(new FakeResponse("a", null)));

    clock.advance(Duration.ofMinutes(5));
    lifecycle.nextResponse(invocation("active")); // keeps 'active' fresh
    clock.advance(Duration.ofMinutes(6)); // 'stale' now 11m idle, 'active' 6m idle

    lifecycle.sweep();

    assertThat(lifecycle.containsRun("stale")).isFalse();
    assertThat(lifecycle.containsRun("active")).isTrue();
  }

  @Test
  void maxRunCap_evictsLeastRecentlyUsed_keepsJustRegistered() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    FakeRunLifecycle lifecycle = new FakeRunLifecycle(clock, null, 2);
    lifecycle.register("run-a", script(new FakeResponse("a", null)));
    clock.advance(Duration.ofSeconds(1));
    lifecycle.register("run-b", script(new FakeResponse("b", null)));
    clock.advance(Duration.ofSeconds(1));
    lifecycle.register("run-c", script(new FakeResponse("c", null))); // exceeds cap of 2

    assertThat(lifecycle.containsRun("run-a")).isFalse(); // least-recently-used evicted
    assertThat(lifecycle.containsRun("run-b")).isTrue();
    assertThat(lifecycle.containsRun("run-c")).isTrue();
  }

  @Test
  void concurrentRunsWithOverlappingKeys_stayIsolated() throws InterruptedException {
    int runs = 8;
    int callsPerRun = 50;
    FakeRunLifecycle lifecycle = new FakeRunLifecycle();
    for (int i = 0; i < runs; i++) {
      final int runIndex = i;
      FakeScript script = new FakeScript(1, java.util.stream.IntStream.range(0, callsPerRun)
          .boxed()
          .collect(java.util.stream.Collectors.toMap(
              ordinal -> new FakeScriptKey("wf", "s1", "a1", ordinal),
              ordinal -> new FakeResponse("run-" + runIndex + "-ord-" + ordinal, null))));
      lifecycle.register("run-" + runIndex, script);
    }

    ExecutorService pool = Executors.newFixedThreadPool(runs);
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    for (int i = 0; i < runs; i++) {
      String runId = "run-" + i;
      pool.submit(() -> {
        try {
          start.await();
          for (int call = 0; call < callsPerRun; call++) {
            FakeResolution resolution = lifecycle.nextResponse(invocation(runId));
            if (!(resolution instanceof FakeResolution.Found found)) {
              failures.add(runId + " call " + call + " -> " + resolution.getClass().getSimpleName());
              continue;
            }
            String expected = runId + "-ord-" + call;
            if (!found.response().responseText().equals(expected)) {
              failures.add(runId + " call " + call + " -> '" + found.response().responseText()
                  + "' expected '" + expected + "'");
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    // Every run consumed exactly its own ordinals 0..callsPerRun-1 in order and saw its own
    // run-scoped response text — no interleaving, no shared counter, no cross-run response bleed.
    assertThat(failures).isEmpty();
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
