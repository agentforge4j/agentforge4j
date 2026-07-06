// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the shipped context-pack manifest schema and the ledger envelope schemas.
 */
class ContextPackLedgerSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path CONTEXT_PACK_SCHEMA =
      Path.of("src/main/resources/schema/context-pack.schema.json").toAbsolutePath().normalize();
  private static final Path REQUIREMENT_LEDGER_SCHEMA =
      Path.of("src/main/resources/schema/ledger/requirement-ledger.schema.json")
          .toAbsolutePath().normalize();

  private static List<Error> validate(Path schemaPath, String instanceJson) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(schemaPath));
    Schema schema =
        SCHEMA_REGISTRY.getSchema(SchemaLocation.of(schemaPath.toUri().toString()), schemaNode);
    return schema.validate(MAPPER.readTree(instanceJson));
  }

  @Test
  void contextPack_acceptsNameVersionAndVariants() throws Exception {
    String json = """
        {"name":"coding-standards","version":"1.2.0","description":"...","tags":["software-delivery"],
         "variants":{"full":"content.md","compact":"content.compact.md"}}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isEmpty();
  }

  @Test
  void contextPack_rejectsMissingVariants() throws Exception {
    String json = """
        {"name":"coding-standards","version":"1.2.0"}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isNotEmpty();
  }

  @Test
  void contextPack_rejectsUnknownProperty() throws Exception {
    String json = """
        {"name":"p","version":"1.0.0","variants":{"full":"c.md"},"unexpected":true}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isNotEmpty();
  }

  @Test
  void requirementLedger_acceptsEnvelopeWithIdentifiedEntries() throws Exception {
    String json = """
        {"entries":[{"id":"REQ-1","summary":"open text","priority":"HIGH"}],
         "openQuestions":["who owns onboarding?"],
         "conflicts":[]}""";
    assertThat(validate(REQUIREMENT_LEDGER_SCHEMA, json)).isEmpty();
  }

  @Test
  void requirementLedger_rejectsEntryWithoutId() throws Exception {
    String json = """
        {"entries":[{"summary":"no id"}]}""";
    assertThat(validate(REQUIREMENT_LEDGER_SCHEMA, json)).isNotEmpty();
  }
}
