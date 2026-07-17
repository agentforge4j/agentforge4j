// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandTypeContract;
import com.agentforge4j.core.command.schema.LlmCommandSubtypeRegistry;
import com.agentforge4j.util.Validate;
import com.agentforge4j.util.text.CodeFence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Parses raw LLM output text into a list of {@link LlmCommand}s, validating each command's type
 * against the agent's {@link CommandResponseSchema} and required fields before binding.
 */
public final class LlmCommandParser {

  private static final System.Logger LOG = System.getLogger(LlmCommandParser.class.getName());

  private final ObjectMapper objectMapper;

  public LlmCommandParser(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper should not be null");
  }

  /**
   * Parses {@code rawResponse} into commands allowed by {@code schema}.
   *
   * @param rawResponse raw model output text, expected to contain a JSON array of command objects
   * @param schema      the calling agent's command schema, used to validate types and required fields
   * @return parsed commands in response order
   * @throws LlmCommandParseException if the output is not valid JSON, is not a non-empty JSON array,
   *                                   or any element names an unknown/disallowed type or fails binding
   */
  public List<LlmCommand> parse(String rawResponse, CommandResponseSchema schema) {
    Validate.notBlank(rawResponse, "rawResponse must not be blank");
    Validate.notNull(schema, "schema must not be null");
    ArrayNode array = extractArrayNode(rawResponse);

    Set<String> allKnown = LlmCommandSubtypeRegistry.allSubtypes().keySet();
    Set<String> allowedForAgent = new TreeSet<>(schema.supportedCommandTypes());
    Map<String, CommandTypeContract> contracts = schema.contractsByTypeName();

    List<LlmCommand> commands = new ArrayList<>();
    for (int i = 0; i < array.size(); i++) {
      JsonNode node = array.get(i);
      CommandTypeResolution typeContract = resolveCommandTypeContract(
          node, i, allKnown, allowedForAgent, contracts);

      typeContract.contract().requiredJsonPropertyNames().forEach(required ->
          Validate.isTrue(node.has(required) && !node.get(required).isNull(),
              () -> new LlmCommandParseException(
                  "Command type '%s' is missing required field '%s'.".formatted(typeContract.type(),
                      required))));
      try {
        commands.add(objectMapper.convertValue(node, LlmCommand.class));
      } catch (Exception e) {
        LOG.log(System.Logger.Level.DEBUG,
            "Jackson failed to bind command type %s: %s".formatted(typeContract.type(),
                e.getMessage()), e);
        throw new LlmCommandParseException(
            "Command type '%s' has invalid field values.".formatted(typeContract.type()), e);
      }
    }
    return commands;
  }

  private static CommandTypeResolution resolveCommandTypeContract(JsonNode node,
      int finalI, Set<String> allKnown, Set<String> allowedForAgent,
      Map<String, CommandTypeContract> contracts) {
    Validate.isTrue(node != null && node.isObject(), () -> new LlmCommandParseException(
        "Command at index %d must be a JSON object.".formatted(finalI)));
    JsonNode typeNode = node.get("type");
    Validate.isTrue(typeNode != null && typeNode.isTextual(), () -> new LlmCommandParseException(
        "Command at index %d must include a textual \"type\" field.".formatted(finalI)));
    String type = typeNode.asText();
    Validate.isTrue(allKnown.contains(type), () -> new LlmCommandParseException(
        "Unknown command type '%s'. Allowed LlmCommand types: %s."
            .formatted(type, formatSorted(allKnown))));
    Validate.isTrue(allowedForAgent.contains(type), () -> new LlmCommandParseException(
        "Command type '%s' is not enabled for this agent. Allowed for this agent: %s."
            .formatted(type, formatSorted(allowedForAgent))));
    return new CommandTypeResolution(type, contracts.get(type));
  }

  private record CommandTypeResolution(String type, CommandTypeContract contract) {

  }

  private ArrayNode extractArrayNode(String rawResponse) {
    String cleaned = CodeFence.strip(rawResponse.strip());
    JsonNode root;
    try {
      root = objectMapper.readTree(cleaned);
    } catch (Exception e) {
      LOG.log(System.Logger.Level.DEBUG, "LLM output is not valid JSON", e);
      throw new LlmCommandParseException("Output is not valid JSON.", e);
    }
    Validate.isTrue(root != null && root.isArray(),
        () -> new LlmCommandParseException("Output must be a JSON array of command objects."));

    ArrayNode array = (ArrayNode) root;
    Validate.isTrue(array != null && !array.isEmpty(),
        () -> new LlmCommandParseException("Command array must contain at least one object."));
    return array;
  }

  private static String formatSorted(Set<String> names) {
    return names.stream().sorted().collect(Collectors.joining(", "));
  }
}
