package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientResolverTest {

  private static LlmExecutionRequest dummyRequest() {
    return LlmExecutionRequest.withDefaultModel("openai", "prompt", "input");
  }

  static final class Fail503Client implements LlmClient {

    private final AtomicInteger executeCalls = new AtomicInteger();
    private final Optional<LlmRetryPolicy> retryPolicyOverride;

    Fail503Client(Optional<LlmRetryPolicy> retryPolicyOverride) {
      this.retryPolicyOverride = retryPolicyOverride;
    }

    @Override
    public String getProviderName() {
      return "openai";
    }

    @Override
    public Optional<LlmRetryPolicy> getRetryPolicy() {
      return retryPolicyOverride;
    }

    @Override
    public LlmExecutionResponse execute(LlmExecutionRequest request) {
      executeCalls.incrementAndGet();
      throw new LlmInvocationException("down", 503);
    }

    int getExecuteCalls() {
      return executeCalls.get();
    }
  }

  static final class CountingDelegatingResolver implements LlmClientResolver {

    final AtomicInteger resolveCalls = new AtomicInteger();
    private final LlmClient innerClient;

    CountingDelegatingResolver(LlmClient innerClient) {
      this.innerClient = innerClient;
    }

    @Override
    public LlmClient resolve(String provider) {
      resolveCalls.incrementAndGet();
      return innerClient;
    }

    @Override
    public boolean isProviderAvailable(String provider) {
      return true;
    }

    @Override
    public List<String> listAvailableClients() {
      return List.of(innerClient.getProviderName());
    }
  }

  static class TestLlmClientResolver implements LlmClientResolver {

    private LlmClient client;

    void setClient(LlmClient client) {
      this.client = client;
    }

    @Override
    public LlmClient resolve(String provider) {
      return client;
    }

    @Override
    public boolean isProviderAvailable(String provider) {
      return provider.equals(client.getProviderName());
    }

    @Override
    public List<String> listAvailableClients() {
      return List.of(client.getProviderName());
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateWithValidParameters() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      delegate.setClient(new TestLlmClient(null, "r"));
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(delegate, LlmRetryPolicy.defaults());

      assertThat(resolver.resolve("any")).isInstanceOf(RetryingLlmClient.class);
    }

    @Test
    void shouldThrowWhenDelegateNull() {
      assertThatThrownBy(() ->
          new RetryingLlmClientResolver(null, LlmRetryPolicy.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("delegate must not be null");
    }

    @Test
    void shouldThrowWhenDefaultPolicyNull() {
      LlmClientResolver delegate = new TestLlmClientResolver();
      assertThatThrownBy(() -> new RetryingLlmClientResolver(delegate, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("defaultPolicy must not be null");
    }
  }

  @Nested
  class ResolveTests {

    @Test
    void shouldReturnRetryingClient() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      LlmClient baseClient = new TestLlmClient(null, "response");
      delegate.setClient(baseClient);
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(delegate, LlmRetryPolicy.defaults());

      LlmClient client = resolver.resolve("test");

      assertThat(client).isInstanceOf(RetryingLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("test");
    }

    @Test
    void cachesByNormalizedProviderKey_openAiAnd_openai_shareInstance() {
      LlmClient inner = new Fail503Client(Optional.empty());
      CountingDelegatingResolver counting = new CountingDelegatingResolver(inner);
      LlmRetryPolicy defaultPolicy =
          new LlmRetryPolicy(2, 1L, 6L, 0L);
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(counting, defaultPolicy);

      LlmClient a = resolver.resolve("OpenAI ");
      LlmClient b = resolver.resolve("openai");

      assertThat(a).isSameAs(b);
      assertThat(counting.resolveCalls.get()).isEqualTo(1);
    }

    @Test
    void usesInnerClientRetryPolicy_whenPresent() {
      LlmRetryPolicy custom = new LlmRetryPolicy(5, 1L, 6L, 0L);
      Fail503Client inner = new Fail503Client(Optional.of(custom));
      CountingDelegatingResolver counting = new CountingDelegatingResolver(inner);
      LlmRetryPolicy defaultPolicy = new LlmRetryPolicy(2, 1L, 6L, 0L);
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(counting, defaultPolicy);

      RetryingLlmClient wrapped =
          (RetryingLlmClient) resolver.resolve("openai");

      assertThatThrownBy(() -> wrapped.execute(dummyRequest()))
          .isInstanceOf(LlmInvocationException.class);

      assertThat(inner.getExecuteCalls()).isEqualTo(5);
    }

    @Test
    void usesDefaultRetryPolicy_whenInnerReturnsEmpty_optional() {
      Fail503Client inner = new Fail503Client(Optional.empty());
      CountingDelegatingResolver counting = new CountingDelegatingResolver(inner);
      LlmRetryPolicy defaultPolicy = new LlmRetryPolicy(3, 1L, 6L, 0L);
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(counting, defaultPolicy);

      RetryingLlmClient wrapped =
          (RetryingLlmClient) resolver.resolve("openai");

      assertThatThrownBy(() -> wrapped.execute(dummyRequest()))
          .isInstanceOf(LlmInvocationException.class);

      assertThat(inner.getExecuteCalls()).isEqualTo(3);
    }

    @Test
    void shouldCacheClients() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      LlmClient baseClient = new TestLlmClient(null, "response");
      delegate.setClient(baseClient);
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(delegate, LlmRetryPolicy.defaults());

      LlmClient client1 = resolver.resolve("test");
      LlmClient client2 = resolver.resolve("test");

      assertThat(client1).isSameAs(client2);
    }

    @Test
    void shouldUseSeparateCacheEntryPerProviderKey() {
      TestLlmClientResolver delegate = new TestLlmClientResolver();
      delegate.setClient(new TestLlmClient(null, "same"));
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(delegate, new LlmRetryPolicy(2, 1L, 6L, 0L));

      LlmClient a = resolver.resolve("provider-a");
      LlmClient b = resolver.resolve("provider-b");

      assertThat(a).isNotSameAs(b);
      assertThat(a).isInstanceOf(RetryingLlmClient.class);
      assertThat(b).isInstanceOf(RetryingLlmClient.class);
    }

    @Test
    void shouldPropagateExceptionsFromDelegateResolve() {
      LlmClientResolver delegate = new LlmClientResolver() {
        @Override
        public LlmClient resolve(String provider) {
          throw new IllegalArgumentException("missing " + provider);
        }

        @Override
        public boolean isProviderAvailable(String provider) {
          return false;
        }

        @Override
        public List<String> listAvailableClients() {
          return List.of();
        }
      };
      RetryingLlmClientResolver resolver =
          new RetryingLlmClientResolver(delegate, new LlmRetryPolicy(2, 1L, 6L, 0L));

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
    public LlmExecutionResponse execute(LlmExecutionRequest request) {
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      return new LlmExecutionResponse(response, null);
    }
  }
}
