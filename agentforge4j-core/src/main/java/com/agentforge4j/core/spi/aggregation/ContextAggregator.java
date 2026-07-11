// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.aggregation;

import com.agentforge4j.core.workflow.context.ContextValue;
import java.util.Map;

/**
 * SPI for deterministic, in-workflow aggregation, selected by a stable {@link #aggregatorId()}
 * that an {@code AGGREGATE} step names. Implementations combine the declared input context values
 * into a derived result the engine itself cannot compute (arithmetic, threshold logic); the runtime
 * writes each returned entry back to context under the selecting step's declared output prefix.
 *
 * <p>Implementations must be deterministic and side-effect free. Discovered via
 * {@link java.util.ServiceLoader} directly (no factory indirection) — unlike
 * {@code ArtifactValidatorFactory}, no implementation of this SPI requires a construction-time
 * dependency such as a shared {@code ObjectMapper}.
 */
public interface ContextAggregator {

  /**
   * The stable identifier an {@code AGGREGATE} step uses to select this aggregator.
   *
   * @return non-blank aggregator id
   */
  String aggregatorId();

  /**
   * Combines the declared input context values into the derived result.
   *
   * @param context read-only view of the selecting step's declared input context values
   *
   * @return logical, unprefixed field name to value map; the runtime applies the step's declared
   *         output prefix, never this method
   */
  Map<String, ContextValue> aggregate(AggregationContext context);
}
