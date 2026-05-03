package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps an LLM client resolver with retrying clients.
 * <p>
 * Resolved clients are cached and wrapped with {@link RetryingLlmClient} for automatic retries.
 */
public final class RetryingLlmClientResolver implements LlmClientResolver {

  private final LlmClientResolver delegate;
  private final int maxAttempts;
  private final long backoffMs;
  private final Map<String, LlmClient> cachedClients = new ConcurrentHashMap<>();

  /**
   * Creates a retrying resolver with the specified parameters.
   *
   * @param delegate    the underlying resolver to wrap
   * @param maxAttempts the maximum number of attempts for retries (must be at least 1)
   * @param backoffMs   the base backoff delay in milliseconds (must be >= 0)
   */
  public RetryingLlmClientResolver(LlmClientResolver delegate, int maxAttempts, long backoffMs) {
    this.delegate = Validate.notNull(delegate, "delegate must not be null");
    Validate.isTrue(maxAttempts >= 1, "Claude maxTokenSize must be positive");
    Validate.isTrue(backoffMs >= 0, "backoffMs must be >= 0");
    this.maxAttempts = maxAttempts;
    this.backoffMs = backoffMs;
  }

  /**
   * Resolves an LLM client for the given provider, wrapping it with retry logic.
   * <p>
   * Clients are cached to avoid repeated wrapping.
   *
   * @param provider the provider name
   * @return a retrying LLM client for the provider
   * @throws IllegalArgumentException if the provider is not found
   */
  @Override
  public LlmClient resolve(String provider) {
    return cachedClients.computeIfAbsent(provider, key ->
        new RetryingLlmClient(delegate.resolve(key), maxAttempts, backoffMs));
  }
}
