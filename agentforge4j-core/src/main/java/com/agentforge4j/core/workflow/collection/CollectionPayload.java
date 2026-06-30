// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Content carried by a single collection submission: an optional bounded inline JSON string and zero
 * or more file references. {@code core} never inspects the inline content beyond size bounds enforced
 * at submit time and never reads file bytes.
 *
 * @param inlineJson optional raw JSON text; {@code null} when the submission carries only files
 * @param files      file references; never {@code null} (an empty list when none), defensively copied
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionPayload(
    String inlineJson,
    List<FileRef> files
) {

  public CollectionPayload {
    files = files != null ? List.copyOf(files) : List.of();
  }

  /**
   * @return {@code true} when the payload carries neither inline JSON nor any file reference
   */
  public boolean isEmpty() {
    return (inlineJson == null || inlineJson.isBlank()) && files.isEmpty();
  }
}
