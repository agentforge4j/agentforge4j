package com.agentforge4j.llm;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientResolverTest {

  static class TestLlmClientResolver implements LlmClientResolver {
    private LlmClient client;

    void setClient(LlmClient client) {
      this.client = client;
    }

    @Override
    public LlmClient resolve(String provider) {
      return client;
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateWithValidParameters() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      delegate.setClient(new TestLlmClient(null, "r"));
      RetryingLlmClientResolver resolver = new RetryingLlmClientResolver(delegate, 3, 100);

      assertThat(resolver.resolve("any")).isInstanceOf(RetryingLlmClient.class);
    }

    @Test
    void shouldThrowWhenDelegateNull() {
      assertThatThrownBy(() -> new RetryingLlmClientResolver(null, 3, 100))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("delegate must not be null");
    }

    @Test
    void shouldThrowWhenMaxAttemptsLessThanOne() {
      LlmClientResolver delegate = new TestLlmClientResolver();
      assertThatThrownBy(() -> new RetryingLlmClientResolver(delegate, 0, 100))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claude maxTokenSize must be positive");
    }

    @Test
    void shouldThrowWhenBackoffNegative() {
      LlmClientResolver delegate = new TestLlmClientResolver();
      assertThatThrownBy(() -> new RetryingLlmClientResolver(delegate, 3, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("backoffMs must be >= 0");
    }
  }

  @Nested
  class ResolveTests {

    @Test
    void shouldReturnRetryingClient() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      LlmClient baseClient = new TestLlmClient(null, "response");
      delegate.setClient(baseClient);
      RetryingLlmClientResolver resolver = new RetryingLlmClientResolver(delegate, 3, 100);

      LlmClient client = resolver.resolve("test");

      assertThat(client).isInstanceOf(RetryingLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("test");
    }

    @Test
    void shouldCacheClients() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      LlmClient baseClient = new TestLlmClient(null, "response");
      delegate.setClient(baseClient);
      RetryingLlmClientResolver resolver = new RetryingLlmClientResolver(delegate, 3, 100);

      LlmClient client1 = resolver.resolve("test");
      LlmClient client2 = resolver.resolve("test");

      assertThat(client1).isSameAs(client2);
    }

    @Test
    void shouldUseSeparateCacheEntryPerProviderKey() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      delegate.setClient(new TestLlmClient(null, "same"));
      RetryingLlmClientResolver resolver = new RetryingLlmClientResolver(delegate, 2, 0);

      LlmClient a = resolver.resolve("provider-a");
      LlmClient b = resolver.resolve("provider-b");

      assertThat(a).isNotSameAs(b);
      assertThat(a).isInstanceOf(RetryingLlmClient.class);
      assertThat(b).isInstanceOf(RetryingLlmClient.class);
    }

    @Test
    void shouldPropagateExceptionsFromDelegateResolve() {
      LlmClientResolver delegate = provider -> {
        throw new IllegalArgumentException("missing " + provider);
      };
      RetryingLlmClientResolver resolver = new RetryingLlmClientResolver(delegate, 2, 0);

      assertThatThrownBy(() -> resolver.resolve("openai"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("missing openai");
    }
  }

  static class TestLlmClient implements LlmClient {
    private final RuntimeException exceptionToThrow;
    private final String response;

    TestLlmClient(RuntimeException exceptionToThrow, String response) {
      this.exceptionToThrow = exceptionToThrow;
      this.response = response;
    }

    @Override
    public String getProviderName() {
      return "test";
    }

    @Override
    public String execute(LlmExecutionRequest request) {
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      return response;
    }
  }
}
