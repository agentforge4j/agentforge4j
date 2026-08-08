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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract tests for the shipped context-pack manifest schema and the ledger envelope schemas.
 */
class ContextPackLedgerSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path LEDGER_SCHEMA_DIR =
      Path.of("src/main/resources/schema/ledger").toAbsolutePath().normalize();

  private static final Path CONTEXT_PACK_SCHEMA =
      Path.of("src/main/resources/schema/context-pack.schema.json").toAbsolutePath().normalize();

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
  void contextPack_rejectsWhitespaceOnlyName() throws Exception {
    String json = """
        {"name":" ","version":"1.0.0","variants":{"full":"c.md"}}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isNotEmpty();
  }

  @Test
  void contextPack_rejectsWhitespaceOnlyVersion() throws Exception {
    String json = """
        {"name":"p","version":" ","variants":{"full":"c.md"}}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isNotEmpty();
  }

  @Test
  void contextPack_rejectsWhitespaceOnlyVariantKey() throws Exception {
    String json = """
        {"name":"p","version":"1.0.0","variants":{" ":"c.md"}}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isNotEmpty();
  }

  @Test
  void contextPack_rejectsWhitespaceOnlyVariantValue() throws Exception {
    String json = """
        {"name":"p","version":"1.0.0","variants":{"full":" "}}""";
    assertThat(validate(CONTEXT_PACK_SCHEMA, json)).isNotEmpty();
  }

  @ParameterizedTest
  @MethodSource("ledgerSchemaFileNames")
  void ledger_acceptsEnvelopeWithIdentifiedEntries(String schemaFileName) throws Exception {
    String json = """
        {"entries":[{"id":"L-1","summary":"open text","priority":"HIGH"}],
         "openQuestions":["who owns onboarding?"],
         "conflicts":[]}""";
    assertThat(validate(LEDGER_SCHEMA_DIR.resolve(schemaFileName), json)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("ledgerSchemaFileNames")
  void ledger_rejectsEntryWithoutId(String schemaFileName) throws Exception {
    String json = """
        {"entries":[{"summary":"no id"}]}""";
    assertThat(validate(LEDGER_SCHEMA_DIR.resolve(schemaFileName), json)).isNotEmpty();
  }

  @ParameterizedTest
  @MethodSource("ledgerSchemaFileNames")
  void ledger_rejectsUnknownTopLevelProperty(String schemaFileName) throws Exception {
    String json = """
        {"entries":[{"id":"L-1"}],"surpriseField":true}""";
    assertThat(validate(LEDGER_SCHEMA_DIR.resolve(schemaFileName), json)).isNotEmpty();
  }

  static List<String> ledgerSchemaFileNames() {
    return List.of(
        "architecture-ledger.schema.json",
        "decision-ledger.schema.json",
        "epic-ledger.schema.json",
        "requirement-ledger.schema.json",
        "risk-ledger.schema.json");
  }
}
