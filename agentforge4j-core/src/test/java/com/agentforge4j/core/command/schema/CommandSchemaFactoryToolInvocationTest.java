package com.agentforge4j.core.command.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandSchemaFactoryToolInvocationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void toolInvocationIsExcludedFromTheAllCommandsDefault() {
    CommandResponseSchema schema = CommandSchemaFactory.build(null, objectMapper);

    assertThat(schema.supportedCommandTypes()).doesNotContain("TOOL_INVOCATION");
    assertThat(schema.supportedCommandTypes()).contains("COMPLETE");
  }

  @Test
  void toolInvocationIsAdvertisedWhenExplicitlyListed() {
    CommandResponseSchema schema =
        CommandSchemaFactory.build(List.of("TOOL_INVOCATION", "COMPLETE"), objectMapper);

    assertThat(schema.supportedCommandTypes()).contains("TOOL_INVOCATION", "COMPLETE");
  }
}
