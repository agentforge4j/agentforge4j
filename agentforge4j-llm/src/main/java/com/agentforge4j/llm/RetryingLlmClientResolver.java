package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;

/**
 * Wraps an LLM client resolver with retrying clients (cached per normalized provider id).
 */
public final class RetryingLlmClientResolver implements LlmClientResolver {

  private final LlmClientResolver delegate;
  private final LlmRetryPolicy defaultPolicy;
  private final Map<String, LlmClient> cachedClients = new ConcurrentHashMap<>();

  public RetryingLlmClientResolver(LlmClientResolver delegate, LlmRetryPolicy defaultPolicy) {
    this.delegate = Validate.notNull(delegate, "delegate must not be null");
    this.defaultPolicy = Validate.notNull(defaultPolicy, "defaultPolicy must not be null");
  }

  @Override
  public LlmClient resolve(String provider) {
    String key = StringUtils.lowerCase(StringUtils.trimToEmpty(provider));
    return cachedClients.computeIfAbsent(key, k -> wrap(delegate.resolve(k)));
  }

  private RetryingLlmClient wrap(LlmClient inner) {
    LlmRetryPolicy policy = inner.getRetryPolicy().orElse(defaultPolicy);
    return new RetryingLlmClient(inner, policy);
  }

  @Override
  public boolean isProviderAvailable(String provider) {
    return delegate.isProviderAvailable(provider);
  }
}
