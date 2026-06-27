// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * A step id that is reachable from more than one structural location across a workflow graph, with the conflicting
 * locations.
 *
 * @param stepId    the ambiguous step id
 * @param locations the two or more structural location keys at which the id is reachable
 */
public record AmbiguousStepId(String stepId, List<String> locations) {

  /**
   * Validates the finding and defensively copies {@code locations}.
   */
  public AmbiguousStepId {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notEmpty(locations, "locations must not be empty");
    locations = List.copyOf(locations);
  }
}
