// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command.schema;

import com.agentforge4j.core.command.LlmCommand;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Per-command JSON contract: Jackson property names marked required (excluding the polymorphic
 * {@code type} discriminator).
 *
 * @param typeName                  the command type name (e.g., "CREATE_FILE")
 * @param implementation            the Java class implementing this command
 * @param requiredJsonPropertyNames list of required JSON property names for this command
 */
public record CommandTypeContract(
    String typeName,
    Class<? extends LlmCommand> implementation,
    List<String> requiredJsonPropertyNames
) {

  public CommandTypeContract {
    Validate.notBlank(typeName, "CommandTypeContract typeName must not be blank");
    Validate.notNull(implementation, "CommandTypeContract implementation must not be null for type: %s".formatted(typeName));
  }
}
