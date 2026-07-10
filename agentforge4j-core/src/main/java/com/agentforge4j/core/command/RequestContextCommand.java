// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Request from the LLM for additional context, referencing selectors the runtime grants only when they
 * are within the step's declared expandable scope. The workflow author decides the maximum context a
 * step can ever see; the agent only chooses within that envelope — the same governance shape as tool
 * invocation (the model requests, the runtime decides).
 *
 * @param requestedSelectors the context selectors the agent requests; never {@code null} or empty
 */
public record RequestContextCommand(
    List<ContextSelector> requestedSelectors
) implements LlmCommand {

  public RequestContextCommand {
    Validate.notEmpty(requestedSelectors,
        "RequestContextCommand requestedSelectors must not be empty");
    requestedSelectors = List.copyOf(requestedSelectors);
  }
}
