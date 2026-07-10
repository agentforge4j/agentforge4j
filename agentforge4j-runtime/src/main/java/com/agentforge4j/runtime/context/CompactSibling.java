// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.util.Validate;

/**
 * A compaction step's output: the compact content alongside its provenance. Stored together at the
 * reserved compact-sibling context key so the two can never drift apart.
 *
 * @param content  the compact form's content; never {@code null}
 * @param metadata the compact sibling's provenance; never {@code null}
 */
public record CompactSibling(String content, CompactSiblingMetadata metadata) {

  public CompactSibling {
    Validate.notNull(content, "CompactSibling content must not be null");
    Validate.notNull(metadata, "CompactSibling metadata must not be null");
  }
}
