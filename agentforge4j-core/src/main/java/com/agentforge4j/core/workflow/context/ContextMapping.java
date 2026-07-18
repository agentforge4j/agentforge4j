// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Defines which context keys a step reads from and writes to. Instances are immutable.
 *
 * <p>Output keys may not lie in the reserved runtime-owned {@code __} namespace: a declared output
 * key authorizes external writers (LLM commands, end-user answers) to write that bare key, and
 * reserved keys back runtime governance state that no external writer may touch.
 */
public record ContextMapping(List<String> inputKeys, List<String> outputKeys) {

  public ContextMapping {
    inputKeys = inputKeys != null ? List.copyOf(inputKeys) : List.of();
    outputKeys = outputKeys != null ? List.copyOf(outputKeys) : List.of();
    for (String outputKey : outputKeys) {
      Validate.isTrue(!ReservedContextKeys.isReserved(outputKey),
          "ContextMapping outputKeys must not declare a reserved '%s' context key: %s"
              .formatted(ReservedContextKeys.RESERVED_PREFIX, outputKey));
    }
  }

  /**
   * Factory method for a mapping with no input or output keys.
   *
   * @return empty context mapping
   */
  public static ContextMapping none() {
    return new ContextMapping(List.of(), List.of());
  }
}
