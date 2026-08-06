// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.context.ContextSource;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * A model's request for additional context: the sources it would like to be given, in the order it
 * asked for them. The request expresses intent only — it addresses sources, it does not grant them.
 * Whether any requested source may be read, and what the requester ultimately receives, is decided
 * elsewhere, because the workflow author owns the context a step can ever see and the model only
 * asks within that envelope.
 *
 * <p>This type is the request model on its own. It is not part of the {@link LlmCommand} hierarchy,
 * so no command schema advertises it, no model output deserializes into it, and no runtime path
 * accepts or executes it.
 *
 * <p>Order is preserved and duplicates are kept. Asking for the same source twice is a request for
 * two grants, not one, so collapsing duplicates here would silently change what was asked.
 *
 * <p>The list is defensively copied, which also rejects a {@code null} element.
 *
 * @param requestedSources the sources requested, in request order; never {@code null}, never empty,
 *                         and never containing {@code null}
 */
public record RequestContextCommand(
    List<ContextSource> requestedSources) {

  /**
   * Validates that at least one source was requested and takes an immutable copy of the list.
   */
  public RequestContextCommand {
    Validate.notEmpty(requestedSources,
        "RequestContextCommand requestedSources must not be empty");
    requestedSources = List.copyOf(requestedSources);
  }
}
