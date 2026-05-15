package com.agentforge4j.llm;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientTest {

  /**
   * Fake client: each {@link #execute} pops the next script entry; suppliers may return a value or
   * throw from {@link Supplier#get}.
   */
  static final class ScriptedLlmClient implements LlmClient {
    private final String providerName;
    private final ArrayDeque<Supplier<String>> script = new ArrayDeque<>();
    private final AtomicInteger callCount = new AtomicInteger();

    ScriptedLlmClient(String providerName) {
      this.providerName = providerName;
    }

    ScriptedLlmClient(String providerName, List<Supplier<String>> steps) {
      this.providerName = providerName;
      this.script.addAll(steps);
    }

    @SafeVarargs
    ScriptedLlmClient(String providerName, Supplier<String>... steps) {
      this(providerName, List.of(steps));
    }

    @Override
    public String getProviderName() {
      return providerName;
    }

    @Override
    public String execute(LlmExecutionRequest request) {
      callCount.incrementAndGet();
      return script.removeFirst().get();
    }

    int getCallCount() {
      return callCount.get();
    }
  }

  private static LlmExecutionRequest dummyRequest() {
    return LlmExecutionRequest.withDefaultModel("test", "prompt", "input");
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateWithValidParameters() {
      LlmClient delegate = new ScriptedLlmClient("test");
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
      LlmClient delegate = new ScriptedLlmClient("r", () -> "x");
      assertThatThrownBy(() -> new RetryingLlmClient(delegate, 0, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("maxAttempts must be at least 1");
    }

    @Test
    void shouldRejectNegativeBackoff() {
      LlmClient delegate = new ScriptedLlmClient("r", () -> "x");
      assertThatThrownBy(() -> new RetryingLlmClient(delegate, 2, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("backoffMs must be >= 0");
    }
  }

  @Nested
  class ExecuteTests {

    @Test
    void successFirstAttempt_noRetry() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov", () -> "payload");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("payload");
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void transientThenSuccess_oneRetry() {
      IOException io = new IOException("net");
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException("wrapped", io);
          },
          () -> "ok");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("ok");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void allAttemptsFail_exceptionPropagated() {
      LlmInvocationException failure =
          new LlmInvocationException("HTTP error: 503 Service Unavailable");
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw failure;
          },
          () -> {
            throw failure;
          },
          () -> {
            throw failure;
          });
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThatThrownBy(() -> client.execute(dummyRequest()))
          .isSameAs(failure);
      assertThat(delegate.getCallCount()).isEqualTo(3);
    }

    @Test
    void nonTransient_noRetry() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException("Invalid model");
          });
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThatThrownBy(() -> client.execute(dummyRequest()))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessage("Invalid model");
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503, 504})
    void retryableHttpStatusFromMessage_retried(int status) {
      String message = "HTTP error: " + status + " Service";
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException(message);
          },
          () -> "recovered");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("recovered");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void nonRetryableHttpStatus_notRetried() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException("HTTP error: 400 Bad Request");
          });
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThatThrownBy(() -> client.execute(dummyRequest()))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("400");
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void nullMessage_doesNotThrow_notRetried() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException(null);
          });
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThatThrownBy(() -> client.execute(dummyRequest()))
          .isInstanceOf(LlmInvocationException.class);
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void malformedHttpStatusMessage_doesNotThrow() {
      for (String badMessage : List.of("HTTP error: ABC", "HTTP error: 99999")) {
        ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
            () -> {
              throw new LlmInvocationException(badMessage);
            });
        RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

        assertThatThrownBy(() -> client.execute(dummyRequest()))
            .isInstanceOf(LlmInvocationException.class)
            .hasMessage(badMessage);
        assertThat(delegate.getCallCount()).isEqualTo(1);
      }
    }

    @Test
    void ioExceptionInCauseChain_retried() {
      IOException root = new IOException("deep");
      RuntimeException mid = new RuntimeException("mid", root);
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new RuntimeException("outer", mid);
          },
          () -> "fine");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("fine");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void interruptedDuringBackoff_throwsAndRestoresFlag() throws Exception {
      CountDownLatch firstFailure = new CountDownLatch(1);
      AtomicReference<LlmInvocationException> terminal = new AtomicReference<>();
      AtomicBoolean interruptedAfterExecute = new AtomicBoolean();
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
      LlmExecutionRequest request = dummyRequest();

      Thread worker = new Thread(() -> {
        try {
          client.execute(request);
        } catch (LlmInvocationException e) {
          interruptedAfterExecute.set(Thread.currentThread().isInterrupted());
          terminal.set(e);
        }
      }, "retrying-llm-backoff-test");
      worker.setDaemon(true);
      worker.start();
      assertThat(firstFailure.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(80);
      worker.interrupt();
      worker.join(10_000);
      assertThat(worker.isAlive()).isFalse();
      assertThat(terminal.get())
          .isNotNull()
          .hasMessage("Retry interrupted")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(interruptedAfterExecute.get()).isTrue();
    }

    @Test
    void shouldRetryOnHttpTimeoutException() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new RuntimeException(new HttpTimeoutException("timeout"));
          },
          () -> "success");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnSocketTimeoutException() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new RuntimeException(new SocketTimeoutException("socket timeout"));
          },
          () -> "success");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnConnectException() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new RuntimeException(new ConnectException("connect failed"));
          },
          () -> "success");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("success");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldNotRetryOnNonTransientException() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new IllegalArgumentException("invalid");
          });
      RetryingLlmClient client = new RetryingLlmClient(delegate, 3, 0);

      assertThatThrownBy(() -> client.execute(dummyRequest()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("invalid");
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryOn502And503_when_message_matches_realistic_format() {
      String msg502 = "openai HTTP error: 502 - <html>bad gateway</html>";
      ScriptedLlmClient delegate502 = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException(msg502);
          },
          () -> "ok");
      RetryingLlmClient client502 = new RetryingLlmClient(delegate502, 2, 0);
      assertThat(client502.execute(dummyRequest())).isEqualTo("ok");
      assertThat(delegate502.getCallCount()).isEqualTo(2);

      String msg503 = "claude HTTP error: 503 - {\"error\":\"overload\"}";
      ScriptedLlmClient delegate503 = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException(msg503);
          },
          () -> "ok2");
      RetryingLlmClient client503 = new RetryingLlmClient(delegate503, 2, 0);
      assertThat(client503.execute(dummyRequest())).isEqualTo("ok2");
      assertThat(delegate503.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOn504() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException("HTTP error: 504");
          },
          () -> "done");
      RetryingLlmClient client = new RetryingLlmClient(delegate, 2, 0);

      assertThat(client.execute(dummyRequest())).isEqualTo("done");
      assertThat(delegate.getCallCount()).isEqualTo(2);
    }

    @Test
    void shouldNotRetry_when_maxAttempts_is_one() {
      ScriptedLlmClient delegate = new ScriptedLlmClient("prov",
          () -> {
            throw new LlmInvocationException("HTTP error: 503");
          });
      RetryingLlmClient client = new RetryingLlmClient(delegate, 1, 0);

      assertThatThrownBy(() -> client.execute(dummyRequest()))
          .isInstanceOf(LlmInvocationException.class);
      assertThat(delegate.getCallCount()).isEqualTo(1);
    }
  }
}
