// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;

/**
 * Wraps an LLM client resolver with retrying clients (cached per normalized provider id).
 *
 * <p>This is where retry-policy selection happens: a resolved client that declares its own policy
 * keeps it, and one that declares none ({@code null}) is retried with the default supplied to this
 * resolver at construction. The resulting {@link RetryingLlmClient} then reports that selected
 * policy as its own.
 */
public final class RetryingLlmClientResolver implements LlmClientResolver {

  private final LlmClientResolver delegate;
  private final LlmRetryPolicy defaultPolicy;
  private final Map<String, LlmClient> cachedClients = new ConcurrentHashMap<>();

  /**
   * Creates a resolver that wraps the clients of {@code delegate} with retry behaviour.
   *
   * @param delegate      resolver producing the clients to wrap; must not be {@code null}
   * @param defaultPolicy policy used for resolved clients that declare none of their own; must not
   *                      be {@code null}
   */
  public RetryingLlmClientResolver(LlmClientResolver delegate, LlmRetryPolicy defaultPolicy) {
    this.delegate = Validate.notNull(delegate, "delegate must not be null");
    this.defaultPolicy = Validate.notNull(defaultPolicy, "defaultPolicy must not be null");
  }

  /**
   * Resolves the client for {@code provider} and wraps it with retry behaviour, caching the wrapper
   * per normalized provider id. See {@link #wrap(LlmClient)} for which retry policy the wrapper is
   * given.
   *
   * @param provider provider id; normalized by trimming and lowercasing
   *
   * @return the retrying wrapper around the resolved client
   */
  @Override
  public LlmClient resolve(String provider) {
    String key = StringUtils.lowerCase(StringUtils.trimToEmpty(provider));
    return cachedClients.computeIfAbsent(key, k -> wrap(delegate.resolve(k)));
  }

  /**
   * Selects the retry policy for one resolved client: the client's own policy when it declares one,
   * otherwise this resolver's default. The selected policy is what the returned wrapper retries
   * with and what {@link RetryingLlmClient#getRetryPolicy()} then reports.
   *
   * @param inner the resolved client to wrap
   *
   * @return the wrapper carrying the selected policy
   */
  private RetryingLlmClient wrap(LlmClient inner) {
    LlmRetryPolicy policy = inner.getRetryPolicy();
    return new RetryingLlmClient(inner, policy != null ? policy : defaultPolicy);
  }

  @Override
  public boolean isProviderAvailable(String provider) {
    return delegate.isProviderAvailable(provider);
  }

  @Override
  public List<String> listAvailableClients() {
    return delegate.listAvailableClients();
  }
}
