// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.net;

import java.net.URI;

/**
 * Classifies whether a target URL is an eligible outbound destination, as a defence against server-side request forgery
 * (SSRF) on tool-driven HTTP. This is a neutral, dependency-light primitive: it <em>classifies</em> and never enforces
 * — {@link #check(URI)} returns an {@link EgressCheckResult} and never throws a policy exception. Callers own the
 * reaction (fail a tool invocation, refuse a configuration, and so on).
 *
 * <p>The default implementation is {@link HttpEgressGuard}. The SPI references this interface rather
 * than the concrete classifier so the {@code core} tool-provider contract stays free of a concrete util type.
 */
public interface OutboundEgressGuard {

  /**
   * Classifies a target URL.
   *
   * @param uri the absolute target URL; must not be {@code null}
   *
   * @return an allowed result when the scheme is {@code http}/{@code https} and the host resolves entirely to public
   * addresses; otherwise a denied result whose {@link EgressCheckResult#reason()} describes the classification
   */
  EgressCheckResult check(URI uri);
}
