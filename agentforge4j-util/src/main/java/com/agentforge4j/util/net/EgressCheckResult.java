// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.net;

import com.agentforge4j.util.Validate;

/**
 * Outcome of an {@link OutboundEgressGuard} classification of a target URL. This is a neutral, dependency-free
 * <em>classification</em> result — it states whether the target resolves to an eligible (public) destination and, when
 * not, a non-secret {@link #reason()} describing why. It carries no policy verb and never throws; the caller decides
 * how to react (fail a tool call, refuse a configuration, and so on).
 *
 * @param allowed whether the target is an eligible egress destination
 * @param reason  {@code null} when {@link #allowed()} is {@code true}; otherwise a non-secret description of the
 *                classification (host and address detail, never a request body, header, or secret)
 */
public record EgressCheckResult(boolean allowed, String reason) {

  /**
   * Enforces the result invariants: an allowed result carries no (misleading) denial reason, and a denied result
   * carries a non-blank reason. The reason being non-secret is a caller contract — the guard only ever supplies
   * host/scheme classification detail — and cannot be machine-checked here.
   *
   * @throws IllegalArgumentException if {@code allowed} is {@code true} with a non-null reason, or {@code false} with a
   *                                  blank reason
   */
  public EgressCheckResult {
    if (allowed) {
      Validate.isTrue(reason == null, "an allowed EgressCheckResult must not carry a reason");
    } else {
      Validate.notBlank(reason, "a denied EgressCheckResult must have a non-blank reason");
    }
  }

  /**
   * @return an allowed result with no reason
   */
  public static EgressCheckResult allow() {
    return new EgressCheckResult(true, null);
  }

  /**
   * @param reason non-blank, non-secret description of why the target is not eligible
   *
   * @return a denied result carrying {@code reason}
   */
  public static EgressCheckResult deny(String reason) {
    return new EgressCheckResult(false, reason);
  }
}
