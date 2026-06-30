// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * How a collection gate treats duplicate submissions.
 */
public enum DuplicatePolicy {
  /**
   * Duplicates are accepted; every submission becomes a distinct item.
   */
  ALLOW,
  /**
   * A submission carrying a {@code clientToken} already seen is refused.
   */
  REJECT_BY_CLIENT_TOKEN,
  /**
   * A submission carrying a {@code dedupeKey} already seen on a live item is refused. The key is
   * caller-supplied and opaque; {@code core} performs no content hashing.
   */
  REJECT_BY_DEDUPE_KEY
}
