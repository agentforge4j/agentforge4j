package com.agentforge4j.llm;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientTest {

  static class TestLlmClient implements LlmClient {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final RuntimeException exceptionToThrow;
    private final String response;
    private final int failCount;

    TestLlmClient(RuntimeException exceptionToThrow, String response) {
      this(exceptionToThrow, response, 0);
    }

    TestLlmClient(RuntimeException exceptionToThrow, String response, int failCount) {
      this.exceptionToThrow = exceptionToThrow;
      this.response = response;
      this.failCount = failCount;
    }

    @Override
    public String getProviderName() {
      return "test";
    }

    @Override
    public String execute(LlmExecutionRequest request) {
      int count = callCount.incrementAndGet();
      if (exceptionToThrow != null && count <= failCount) {
        throw exceptionToThrow;
      }
      return response;
    }

    int getCallCount() {
      return callCount.get();
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateWithValidParameters() {
      LlmClient delegate = new TestLlmClient(null, "response");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 100);

      assertThat(client.getProviderName()).isEqualTo("test");
    }

    @Test
    void shouldRejectNullDelegate() {
      assertThatThrownBy(() -> new RetryingLlmClient(null, 2, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("delegate must not be null");
    }

    @Test
    void shouldRejectMaxAttemptsBelowOne() {
      LlmClient delegate = new TestLlmClient(null, "r");
      assertThatThrownBy(() -> new RetryingLlmClient(delegate, 0, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claude maxTokenSize must be positive");
    }

    @Test
    void shouldRejectNegativeBackoff() {
      LlmClient delegate = new TestLlmClient(null, "r");
      assertThatThrownBy(() -> new RetryingLlmClient(delegate, 2, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("backoffMs must be >= 0");
    }
  }

  @Nested
  class ExecuteTests {

    @Test
    void shouldReturnResponseOnFirstAttempt() {
      TestLlmClient delegate = new TestLlmClient(null, "success");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 100);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      String result = client.execute(request);

      assertThat(result).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryOnTransientExceptionAndSucceed() {
      TestLlmClient delegate = new TestLlmClient(new LlmInvocationException("HTTP error: 429"), "success", 1);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0); // no delay
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      String result = client.execute(request);

      assertThat(result).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnIOException() {
      TestLlmClient delegate = new TestLlmClient(new RuntimeException(new IOException("network")), "success", 1);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      String result = client.execute(request);

      assertThat(result).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnHttpTimeoutException() {
      TestLlmClient delegate = new TestLlmClient(new RuntimeException(new HttpTimeoutException("timeout")), "success", 1);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      String result = client.execute(request);

      assertThat(result).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnSocketTimeoutException() {
      TestLlmClient delegate = new TestLlmClient(new RuntimeException(new SocketTimeoutException("socket timeout")), "success", 1);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      String result = client.execute(request);

      assertThat(result).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnConnectException() {
      TestLlmClient delegate = new TestLlmClient(new RuntimeException(new ConnectException("connect failed")), "success", 1);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      String result = client.execute(request);

      assertThat(result).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldNotRetryOnNonTransientException() {
      TestLlmClient delegate = new TestLlmClient(new IllegalArgumentException("invalid"), "success", 10);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("invalid");

      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void shouldFailAfterMaxAttempts() {
      TestLlmClient delegate = new TestLlmClient(new LlmInvocationException("HTTP error: 500"), "success", 10);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 2, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessage("HTTP error: 500");

      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldNotRetryOnNonRetryableHttpStatus() {
      TestLlmClient delegate = new TestLlmClient(new LlmInvocationException("HTTP error: 400"), "success", 10);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessage("HTTP error: 400");

      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryOn502And503_when_message_matches_realistic_format() {
      String msg502 =
          "openai HTTP error: 502 - <html>bad gateway</html>";
      TestLlmClient delegate502 = new TestLlmClient(new LlmInvocationException(msg502), "ok", 1);
      RetryingLlmClient client502 = new RetryingLlmClient(delegate502, 2, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      assertThat(client502.execute(request)).isEqualTo("ok");
      assertThat(delegate502.getCallCount()).isEqualTo(2);

      String msg503 = "claude HTTP error: 503 - {\"error\":\"overload\"}";
      TestLlmClient delegate503 = new TestLlmClient(new LlmInvocationException(msg503), "ok2", 1);
      RetryingLlmClient client503 = new RetryingLlmClient(delegate503, 2, 0);

      assertThat(client503.execute(request)).isEqualTo("ok2");
      assertThat(delegate503.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOn504() {
      TestLlmClient delegate =
          new TestLlmClient(new LlmInvocationException("HTTP error: 504"), "done", 1);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 2, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      assertThat(client.execute(request)).isEqualTo("done");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldNotRetry_when_maxAttempts_is_one() {
      TestLlmClient delegate =
          new TestLlmClient(new LlmInvocationException("HTTP error: 503"), "unused", 5);
      RetryingLlmClient client = new RetryingLlmClient(delegate, 1, 0);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOf(LlmInvocationException.class);

      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void shouldSurfaceInterruptedExceptionDuringBackoffAsLlmInvocationException() throws Exception {
      CountDownLatch firstFailure = new CountDownLatch(1);
      LlmClient delegate = new LlmClient() {
        @Override
        public String getProviderName() {
          return "interrupt-test";
        }

        @Override
        public String execute(LlmExecutionRequest request) {
          firstFailure.countDown();
          throw new LlmInvocationException("HTTP error: 503");
        }
      };
      RetryingLlmClient client = new RetryingLlmClient(delegate, 2, 400);
      LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("test", "prompt", "input");

      ThreadFactory factory = runnable -> {
        Thread t = new Thread(runnable, "retrying-llm-backoff-test");
        t.setDaemon(true);
        return t;
      };
      ExecutorService pool = Executors.newSingleThreadExecutor(factory);
      try {
        Future<String> future = pool.submit(() -> client.execute(request));
        assertThat(firstFailure.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(80);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
          if ("retrying-llm-backoff-test".equals(t.getName())) {
            t.interrupt();
            break;
          }
        }
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(LlmInvocationException.class)
            .hasMessage("Retry interrupted");
      } finally {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      }
    }
  }
}

