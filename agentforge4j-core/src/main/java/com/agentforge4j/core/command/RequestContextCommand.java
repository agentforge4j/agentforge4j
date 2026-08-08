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
 * two grants, not one, so collapsing duplicates here would silently change what was asked. Repeats
 * therefore count separately towards the size limit below.
 *
 * <p>A request carries at most 16 sources. The ceiling is deliberate rather than defensive
 * housekeeping: this list arrives in model-generated output, and whatever eventually evaluates a
 * request has to look at every entry to decide it — so without a bound here, one oversized piece of
 * output turns into an unbounded amount of downstream work. Sixteen is far more than a genuine
 * request needs, and an over-long one is rejected whole rather than silently truncated, because
 * quietly dropping entries would answer a different request from the one that was made.
 *
 * <p>The list is defensively copied, which also rejects a {@code null} element.
 *
 * @param requestedSources the sources requested, in request order; never {@code null}, never empty,
 *                         never containing {@code null}, and never longer than 16 entries
 */
public record RequestContextCommand(
    List<ContextSource> requestedSources) {

  /**
   * The most sources a single request may carry.
   */
  private static final int MAX_REQUESTED_SOURCES = 16;

  /**
   * Validates that the request holds at least one source and no more than
   * {@value #MAX_REQUESTED_SOURCES}, then takes an immutable copy of the list.
   */
  public RequestContextCommand {
    Validate.notEmpty(requestedSources,
        "RequestContextCommand requestedSources must not be empty");
    Validate.isTrue(requestedSources.size() <= MAX_REQUESTED_SOURCES,
        "RequestContextCommand requestedSources must not exceed %d entries, but had %d"
            .formatted(MAX_REQUESTED_SOURCES, requestedSources.size()));
    requestedSources = List.copyOf(requestedSources);
  }
}
