// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolInvocationCommandTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesObjectArgumentsIntoMap() throws Exception {
    String json = """
        {"type":"TOOL_INVOCATION","capability":"github.create_pull_request",\
        "arguments":{"title":"Fix bug","draft":true}}""";

    LlmCommand command = objectMapper.readValue(json, LlmCommand.class);

    assertThat(command).isInstanceOf(ToolInvocationCommand.class);
    ToolInvocationCommand tool = (ToolInvocationCommand) command;
    assertThat(tool.toolInvocationId()).isNotBlank();
    assertThat(tool.capability()).isEqualTo("github.create_pull_request");
    assertThat(tool.arguments()).containsEntry("title", "Fix bug").containsEntry("draft", true);
  }

  @Test
  void deserializesWithoutArgumentsYieldsEmptyMapNotNull() throws Exception {
    String json = """
        {"type":"TOOL_INVOCATION","capability":"slack.post_message"}""";

    ToolInvocationCommand tool =
        (ToolInvocationCommand) objectMapper.readValue(json, LlmCommand.class);

    assertThat(tool.arguments()).isNotNull().isEmpty();
    assertThat(tool.toolInvocationId()).isNotBlank();
  }

  @Test
  void deserializesWithExplicitToolInvocationIdAndObjectArguments() throws Exception {
    String json = """
        {"type":"TOOL_INVOCATION","toolInvocationId":"fixed-id-123",\
        "capability":"jira.create_issue","arguments":{"summary":"x"}}""";

    ToolInvocationCommand tool =
        (ToolInvocationCommand) objectMapper.readValue(json, LlmCommand.class);

    assertThat(tool.toolInvocationId()).isEqualTo("fixed-id-123");
    assertThat(tool.arguments()).containsEntry("summary", "x");
  }

  @Test
  void blankToolInvocationIdIsReplacedWithGeneratedUuid() {
    ToolInvocationCommand tool =
        new ToolInvocationCommand("   ", "slack.post_message", Map.of(), null);

    assertThat(tool.toolInvocationId()).isNotBlank();
    assertThat(tool.toolInvocationId()).isNotEqualTo("   ");
  }

  @Test
  void blankCapabilityIsRejected() {
    assertThatThrownBy(() -> new ToolInvocationCommand(null, "  ", Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
