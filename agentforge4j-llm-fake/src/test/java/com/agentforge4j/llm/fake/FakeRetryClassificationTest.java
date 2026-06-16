// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.RetryingLlmClient;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationIdentity;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeRetryClassificationTest {

  /**
   * A fake miss is fail-closed and terminal: wrapped in {@link RetryingLlmClient}, it must not be retried (a retry
   * would mask the script gap and advance the ordinal counter again). This holds because
   * {@link FakeResponseNotFoundException} is an {@code LlmInvocationException} with no HTTP status, which the retry
   * classifier treats as non-transient.
   */
  @Test
  void fakeResponseNotFound_isNonTransient_soRetryingClientDoesNotRetry() {
    AtomicInteger executions = new AtomicInteger();
    // Source that always misses (run not scripted), counting each invocation.
    FakeResponseSource missingSource = invocation -> {
      executions.incrementAndGet();
      return new FakeResolution.RunNotScripted();
    };
    LlmClient retrying = new RetryingLlmClient(
        new FakeLlmClient(missingSource), LlmRetryPolicy.defaults());

    LlmExecutionRequest request = new LlmExecutionRequest("fake", null, "system", "user", null, null,
        new LlmInvocationIdentity("wf", "run-1", "s1", "a1"));

    assertThatThrownBy(() -> retrying.execute(request))
        .isInstanceOf(FakeResponseNotFoundException.class);
    assertThat(executions).hasValue(1); // exactly one attempt — no retry
  }
}
