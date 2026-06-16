// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.blueprint;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.util.Validate;

/**
 * Reference to a {@link BlueprintDefinition} by id for inclusion in a parent workflow.
 *
 * @param blueprintId non-blank id resolved via repository or catalog at runtime
 */
public record BlueprintRef(String blueprintId) implements Executable {

  public BlueprintRef {
    Validate.notBlank(blueprintId, "BlueprintRef blueprintId must not be blank");
  }
}
