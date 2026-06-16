// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command.schema;

import com.agentforge4j.core.command.CreateFileCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandSchemaFactoryTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void schema_contains_exactly_supported_commands_in_order() {
    CommandResponseSchema schema = CommandSchemaFactory.build(
        List.of("USER_PROMPT", "COMPLETE", "SET_CONTEXT"), mapper);

    assertThat(schema.supportedCommandTypes()).containsExactly("USER_PROMPT", "COMPLETE", "SET_CONTEXT");
    assertThat(schema.commandSchemaVersion()).isEqualTo(CommandResponseSchema.COMMAND_SCHEMA_VERSION);
    assertThat(schema.cacheKey()).isEqualTo(
        CommandResponseSchema.COMMAND_SCHEMA_VERSION + "|USER_PROMPT,COMPLETE,SET_CONTEXT");
  }

  @Test
  void field_contract_matches_record_components_via_jackson_required() {
    CommandResponseSchema schema = CommandSchemaFactory.build(List.of("CREATE_FILE"), mapper);
    CommandTypeContract contract = schema.commandContracts().get(0);

    assertThat(contract.typeName()).isEqualTo("CREATE_FILE");
    assertThat(contract.implementation()).isEqualTo(CreateFileCommand.class);
    assertThat(contract.requiredJsonPropertyNames()).containsExactly("content", "path");

    var recordComponents = Arrays.stream(CreateFileCommand.class.getRecordComponents())
        .map(RecordComponent::getName)
        .sorted()
        .toList();
    assertThat(contract.requiredJsonPropertyNames()).containsExactlyElementsOf(recordComponents);
  }

  @Test
  void empty_supported_commands_means_all_llm_command_types_except_opt_in_tool_invocation() {
    CommandResponseSchema schema = CommandSchemaFactory.build(null, mapper);

    List<String> expected = LlmCommandSubtypeRegistry.allTypeNamesOrdered().stream()
        .filter(name -> !"TOOL_INVOCATION".equals(name))
        .toList();
    assertThat(schema.supportedCommandTypes()).isEqualTo(expected);
    assertThat(schema.supportedCommandTypes()).doesNotContain("TOOL_INVOCATION");
  }

  @Test
  void unknown_supported_command_rejected() {
    assertThatThrownBy(() -> CommandSchemaFactory.build(List.of("NOT_A_COMMAND"), mapper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NOT_A_COMMAND");
  }

  @Test
  void cache_key_stable_for_same_supported_commands() {
    List<String> supported = List.of("USER_PROMPT", "COMPLETE");
    String first = CommandSchemaFactory.build(supported, mapper).cacheKey();
    String second = CommandSchemaFactory.build(List.copyOf(supported), mapper).cacheKey();

    assertThat(first).isEqualTo(second);
    assertThat(first).startsWith(CommandResponseSchema.COMMAND_SCHEMA_VERSION + "|");
  }

  @Test
  void cache_key_differs_when_supported_commands_differ() {
    String completeOnly = CommandSchemaFactory.build(List.of("COMPLETE"), mapper).cacheKey();
    String withPrompt = CommandSchemaFactory.build(List.of("COMPLETE", "USER_PROMPT"), mapper).cacheKey();

    assertThat(completeOnly).isNotEqualTo(withPrompt);
  }

  @Test
  void null_mapper_rejected() {
    assertThatThrownBy(() -> CommandSchemaFactory.build(List.of("COMPLETE"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mapper");
  }

  @Test
  void empty_supported_list_means_all_llm_command_types_like_null() {
    CommandResponseSchema emptyList = CommandSchemaFactory.build(List.of(), mapper);
    CommandResponseSchema nullList = CommandSchemaFactory.build(null, mapper);
    assertThat(emptyList.supportedCommandTypes()).isEqualTo(nullList.supportedCommandTypes());
    assertThat(emptyList.cacheKey()).isEqualTo(nullList.cacheKey());
  }

  @Test
  void supported_commands_strip_whitespace_and_deduplicate_preserving_first_occurrence_order() {
    CommandResponseSchema schema = CommandSchemaFactory.build(
        List.of(" COMPLETE ", "USER_PROMPT", "COMPLETE"),
        mapper);
    assertThat(schema.supportedCommandTypes()).containsExactly("COMPLETE", "USER_PROMPT");
  }

  @Test
  void only_blank_entries_after_resolution_rejected() {
    assertThatThrownBy(() -> CommandSchemaFactory.build(Arrays.asList(" ", "", null, "\t"), mapper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supportedCommands must name at least one valid command type");
  }
}
