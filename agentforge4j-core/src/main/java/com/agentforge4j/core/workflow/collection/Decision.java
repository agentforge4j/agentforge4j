// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.util.Validate;

/**
 * Outcome of a {@link CollectionAuthorizer} check: allowed or denied, with a reason on denial.
 *
 * @param allowed whether the operation is permitted
 * @param reason  human-readable reason; {@code null} when {@code allowed}
 */
public record Decision(boolean allowed, String reason) {

  /**
   * Validated at construction, not left to the caller: a custom {@link CollectionAuthorizer}
   * returning a malformed deny (blank or {@code null} reason) must fail here, not downstream where
   * the blank reason would otherwise flow into {@link CollectionAuthorizationException}'s own
   * {@code Validate.notBlank} and substitute the wrong exception type for the one callers are
   * documented to catch.
   */
  public Decision {
    if (!allowed) {
      Validate.notBlank(reason, "deny reason must not be blank");
    } else {
      reason = null;
    }
  }

  private static final Decision ALLOWED = new Decision(true, null);

  /**
   * @return a shared allow decision
   */
  public static Decision allow() {
    return ALLOWED;
  }

  /**
   * @param reason non-blank denial reason
   * @return a deny decision carrying {@code reason}
   */
  public static Decision deny(String reason) {
    return new Decision(false, Validate.notBlank(reason, "deny reason must not be blank"));
  }
}
