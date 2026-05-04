package com.agentforge4j.core.command.schema;

import com.agentforge4j.core.command.LlmCommand;

import java.util.List;

/**
 * Per-command JSON contract: Jackson property names marked required (excluding the polymorphic
 * {@code type} discriminator).
 */
public record CommandTypeContract(
    String typeName,
    Class<? extends LlmCommand> implementation,
    List<String> requiredJsonPropertyNames
) {

  /**
   * @param typeName the command type name (e.g., "CREATE_FILE")
   * @param implementation the Java class implementing this command
   * @param requiredJsonPropertyNames list of required JSON property names for this command
   */
  public CommandTypeContract {
  }
}
