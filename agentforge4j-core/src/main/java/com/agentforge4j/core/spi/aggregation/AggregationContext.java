// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.aggregation;

import com.agentforge4j.core.workflow.context.ContextValue;
import java.util.Map;

/**
 * Read-only view passed to a {@link ContextAggregator}: the run context values keyed by the
 * selecting {@code AGGREGATE} step's declared {@code contextMapping} input keys. The view is
 * scoped to exactly those declared keys — an aggregator sees exactly the values it was selected to
 * combine, mirroring {@link com.agentforge4j.core.spi.validation.ArtifactValidationContext}'s
 * declared-scope governance for artifact validators.
 */
@FunctionalInterface
public interface AggregationContext {

  /**
   * Returns the step's declared input keys resolved against the run context, as an immutable
   * {@code key -> value} map.
   *
   * @return immutable map of declared context key to its typed value
   */
  Map<String, ContextValue> values();
}
