// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.TokenEstimator;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Resolves the {@link TokenEstimator} to use: a single provider-registered implementation discovered
 * via JPMS {@link ServiceLoader}, or the shipped {@link DefaultTokenEstimator} when none is registered.
 *
 * <p>Estimates are advisory evidence only; resolution never fails closed. When more than one
 * implementation is registered, the first one {@link ServiceLoader} returns is used — the embedding
 * application controls this by controlling which provider modules are on the module path.
 */
public final class TokenEstimatorResolver {

  private TokenEstimatorResolver() {
  }

  /**
   * Resolves the {@link TokenEstimator} to use.
   *
   * @return a registered {@link TokenEstimator} if one is discoverable on the module path; otherwise a
   *         new {@link DefaultTokenEstimator}. Never {@code null}
   */
  public static TokenEstimator resolve() {
    Iterator<TokenEstimator> discovered = ServiceLoader
        .load(TokenEstimator.class, Thread.currentThread().getContextClassLoader())
        .iterator();
    return discovered.hasNext() ? discovered.next() : new DefaultTokenEstimator();
  }
}
