package com.agentforge4j.core.command.schema;

import com.agentforge4j.util.Validate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Describes the allowed command set and required JSON properties per command for one agent.
 */
public record CommandResponseSchema(
    String commandSchemaVersion,
    List<String> supportedCommandTypes,
    List<CommandTypeContract> commandContracts,
    String cacheKey
) {

  /** Current version string for the command JSON schema contract and cache keys. */
  public static final String COMMAND_SCHEMA_VERSION = "1";

  /**
   * @param commandSchemaVersion version of the schema format
   * @param supportedCommandTypes list of allowed command type names
   * @param commandContracts detailed contracts for each command type
   * @param cacheKey unique key for caching this schema
   */
  public CommandResponseSchema {
    Validate.notBlank(commandSchemaVersion, "commandSchemaVersion must not be blank");
    Validate.notNull(supportedCommandTypes, "supportedCommandTypes must not be null");
    Validate.notNull(commandContracts, "commandContracts must not be null");
    Validate.notBlank(cacheKey, "cacheKey must not be blank");
    supportedCommandTypes = List.copyOf(supportedCommandTypes);
    commandContracts = List.copyOf(commandContracts);
  }

  /**
   * Returns an unmodifiable map from command type name to its contract.
   *
   * @return map of type names to contracts
   */
  public Map<String, CommandTypeContract> contractsByTypeName() {
    return commandContracts.stream()
        .collect(Collectors.toUnmodifiableMap(CommandTypeContract::typeName, c -> c));
  }
}
