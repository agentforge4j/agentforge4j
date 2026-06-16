// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.UserPromptCommand;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCommandParserTest {

  private ObjectMapper mapper;
  private LlmCommandParser parser;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    parser = new LlmCommandParser(mapper);
  }

  @Test
  void valid_response_accepted() {
    CommandResponseSchema schema = CommandSchemaFactory.build(
        List.of("USER_PROMPT", "COMPLETE"), mapper);
    String json = """
        [{"type":"USER_PROMPT","message":"hi","responseRequired":true},{"type":"COMPLETE"}]
        """;

    List<LlmCommand> commands = parser.parse(json, schema);

    assertThat(commands).hasSize(2);
    assertThat(commands.get(0)).isInstanceOf(UserPromptCommand.class);
    assertThat(commands.get(1)).isInstanceOf(CompleteCommand.class);
  }

  @Test
  void unsupported_but_known_command_rejected() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("COMPLETE"), mapper);
    String json = """
        [{"type":"USER_PROMPT","message":"x","responseRequired":false},{"type":"COMPLETE"}]
        """;

    assertThatThrownBy(() -> parser.parse(json, schema))
        .isInstanceOf(LlmCommandParseException.class)
        .hasMessageContaining("USER_PROMPT")
        .hasMessageContaining("not enabled for this agent");
  }

  @Test
  void create_file_rejected_when_agent_supports_only_complete() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("COMPLETE"), mapper);
    String json = """
        [{"type":"CREATE_FILE","path":"a.md","content":"x"},{"type":"COMPLETE"}]
        """;

    assertThatThrownBy(() -> parser.parse(json, schema))
        .isInstanceOf(LlmCommandParseException.class)
        .hasMessageContaining("CREATE_FILE")
        .hasMessageContaining("not enabled for this agent");
  }

  @Test
  void unknown_command_rejected() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("COMPLETE"), mapper);
    String json = "[{\"type\":\"NOT_REAL\"}]";

    assertThatThrownBy(() -> parser.parse(json, schema))
        .isInstanceOf(LlmCommandParseException.class)
        .hasMessageContaining("NOT_REAL")
        .hasMessageContaining("Allowed LlmCommand types");
  }

  @Test
  void missing_required_field_rejected() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("USER_PROMPT", "COMPLETE"), mapper);
    String json = """
        [{"type":"USER_PROMPT","responseRequired":false},{"type":"COMPLETE"}]
        """;

    assertThatThrownBy(() -> parser.parse(json, schema))
        .isInstanceOf(LlmCommandParseException.class)
        .hasMessageContaining("USER_PROMPT")
        .hasMessageContaining("message");
  }
}
