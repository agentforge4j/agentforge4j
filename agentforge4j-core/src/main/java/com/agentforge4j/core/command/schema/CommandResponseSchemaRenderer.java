package com.agentforge4j.core.command.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders {@link CommandResponseSchema} as a deterministic instruction block for prompt injection.
 */
public final class CommandResponseSchemaRenderer {

  public CommandResponseSchemaRenderer() {
  }

  private static final String EXAMPLE_SHAPE = """
      [
        { "type": "USER_PROMPT", "message": "...", "responseRequired": true },
        { "type": "SET_CONTEXT", "key": "productVision", "value": { "type": "JSON", "json": "{...}" } },
        { "type": "COMPLETE" }
      ]""";

  /**
   * Renders the schema as a formatted instruction block for inclusion in LLM prompts.
   *
   * @param schema the schema to render
   * @return formatted string describing the command contract
   */
  public String render(CommandResponseSchema schema) {
    String allowed = schema.supportedCommandTypes().stream()
        .map(s -> "\"" + s + "\"")
        .collect(Collectors.joining(", "));

    List<String> perTypeLines = new ArrayList<>();
    for (CommandTypeContract c : schema.commandContracts()) {
      if (c.requiredJsonPropertyNames().isEmpty()) {
        perTypeLines.add("- " + c.typeName() + ": (no required fields beyond \"type\")");
      } else {
        perTypeLines.add("- " + c.typeName() + ": "
            + String.join(", ", c.requiredJsonPropertyNames()));
      }
    }
    String perType = String.join(System.lineSeparator(), perTypeLines);

    return """
        Framework command contract (authoritative; overrides conflicting agent text):
        Command schema version: %s

        - Output MUST be a single JSON array of command objects.
        - No markdown fences, no prose, no commentary.
        - Each object MUST have a `type` field equal to one of the allowed types.
        - Allowed types: %s.
        - Per-type required fields:
        %s
        - Any other type is invalid.

        Example shape included verbatim in the block:
        %s
        """.strip()
        .formatted(
            schema.commandSchemaVersion(),
            allowed,
            perType,
            EXAMPLE_SHAPE.strip());
  }
}
