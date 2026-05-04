package com.agentforge4j.core.command.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandResponseSchemaRendererTest {

  @Test
  void when_only_complete_supported_allowed_types_line_excludes_create_file() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("COMPLETE"), new ObjectMapper());
    String text = new CommandResponseSchemaRenderer().render(schema);

    assertThat(text).contains("- Allowed types: \"COMPLETE\".");
    assertThat(text).doesNotContain("CREATE_FILE");
  }

  @Test
  void output_is_deterministic_and_lists_only_allowed_types() {
    CommandResponseSchema schema = CommandSchemaFactory.build(
        List.of("USER_PROMPT", "COMPLETE"), new ObjectMapper());
    CommandResponseSchemaRenderer renderer = new CommandResponseSchemaRenderer();

    String first = renderer.render(schema);
    String second = renderer.render(schema);

    assertThat(first).isEqualTo(second);
    assertThat(first).contains("- Allowed types: \"USER_PROMPT\", \"COMPLETE\".");
    assertThat(first).contains("USER_PROMPT");
    assertThat(first).contains("COMPLETE");
    assertThat(first).doesNotContain("CREATE_FILE");
    assertThat(first).contains("Example shape included verbatim in the block:");
    assertThat(first).contains("{ \"type\": \"USER_PROMPT\"");
  }

  @Test
  void per_type_section_marks_commands_without_required_json_fields() {
    CommandResponseSchema schema = CommandSchemaFactory.build(
        List.of("CONTINUE", "COMPLETE", "CREATE_FILE"),
        new ObjectMapper());
    String text = new CommandResponseSchemaRenderer().render(schema);

    assertThat(text).contains("- CONTINUE: (no required fields beyond \"type\")");
    assertThat(text).contains("- COMPLETE: (no required fields beyond \"type\")");
    assertThat(text).contains("- CREATE_FILE:");
    assertThat(text).contains("content");
    assertThat(text).contains("path");
  }

  @Test
  void render_includes_schema_version_line() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("COMPLETE"), new ObjectMapper());
    String text = new CommandResponseSchemaRenderer().render(schema);
    assertThat(text).contains("Command schema version: " + CommandResponseSchema.COMMAND_SCHEMA_VERSION);
  }
}
