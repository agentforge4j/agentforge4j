// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based fuzzing of {@link LlmCommandParser} to complement the example cases in
 * {@code LlmCommandParserTest} and {@code LlmCommandParserSecurityTest}.
 *
 * <p>Invariant: for a single-command JSON array whose {@code type} is randomly a known or unknown
 * value and whose required fields are randomly present or dropped, parsing either yields a list of
 * valid in-hierarchy {@link LlmCommand}s or fails with the defined {@link LlmCommandParseException}
 * - never a partially constructed command and never an undefined exception type. Named {@code *Test}
 * so Surefire discovers it; the jqwik engine runs the {@code @Property} methods.
 */
class LlmCommandParserPropertyTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final LlmCommandParser parser = new LlmCommandParser(mapper);
  private final CommandResponseSchema schema = CommandSchemaFactory.build(null, mapper);

  @Property(tries = 300)
  void parsingYieldsValidCommandsOrThrowsDefinedException(
      @ForAll("commandType") String type,
      @ForAll boolean includeMessage,
      @ForAll boolean includeFileFields,
      @ForAll boolean includeResponseRequired) {
    ObjectNode command = mapper.createObjectNode();
    command.put("type", type);
    if (includeMessage) {
      command.put("message", "hello");
    }
    if (includeResponseRequired) {
      command.put("responseRequired", true);
    }
    if (includeFileFields) {
      command.put("path", "out.txt");
      command.put("content", "data");
    }
    ArrayNode array = mapper.createArrayNode();
    array.add(command);
    String json = array.toString();

    try {
      List<LlmCommand> commands = parser.parse(json, schema);
      // Success path: a non-empty list of fully constructed, in-hierarchy commands.
      assertThat(commands).isNotEmpty();
      assertThat(commands).allMatch(Objects::nonNull);
    } catch (LlmCommandParseException expected) {
      // Acceptable: malformed/unknown/incomplete input fails explicitly with the defined type.
      // Any other exception escaping here fails the property, which is the safety check.
    }
  }

  @Provide
  Arbitrary<String> commandType() {
    Arbitrary<String> known = Arbitraries.of(
        "COMPLETE", "USER_PROMPT", "CREATE_FILE", "CONTINUE", "SET_CONTEXT",
        "ESCALATE", "RUN_COMMAND", "GENERATE_QUESTIONS", "CALL_ENDPOINT");
    Arbitrary<String> unknown = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12);
    return Arbitraries.oneOf(known, unknown);
  }
}
