// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.command.schema.CommandTypeContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandResponseSchemaTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void contracts_by_type_name_is_unmodifiable_and_lookups_work() {
    var schema = CommandSchemaFactory.build(List.of("COMPLETE", "USER_PROMPT"), mapper);

    var byName = schema.contractsByTypeName();

    assertThat(byName).hasSize(2);
    assertThat(byName.get("COMPLETE").implementation()).isEqualTo(CompleteCommand.class);
    assertThatThrownBy(() -> byName.put("X", byName.get("COMPLETE")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void duplicate_contract_type_names_make_contracts_by_type_name_fail_fast() {
    CommandTypeContract c = new CommandTypeContract("COMPLETE", CompleteCommand.class, List.of());
    var schema = new CommandResponseSchema(
        CommandResponseSchema.COMMAND_SCHEMA_VERSION,
        List.of("COMPLETE", "COMPLETE"),
        List.of(c, c),
        "1|COMPLETE,COMPLETE");

    assertThatThrownBy(schema::contractsByTypeName)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("COMPLETE");
  }

  @Test
  void null_supported_command_types_rejected() {
    assertThatThrownBy(() -> new CommandResponseSchema(
        "1",
        null,
        List.of(),
        "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supportedCommandTypes");
  }

  @Test
  void blank_cache_key_rejected() {
    assertThatThrownBy(() -> new CommandResponseSchema(
        "1",
        List.of("COMPLETE"),
        List.of(new CommandTypeContract("COMPLETE", CompleteCommand.class, List.of())),
        "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cacheKey");
  }
}
