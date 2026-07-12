// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@code workflow.schema.json} describes only the executable workflow document, not a merged bundle manifest.
 */
class WorkflowExecutableSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/workflow.schema.json").toAbsolutePath().normalize();

  @Test
  void workflowSchema_rejectsTopLevelArtifacts() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "x",
          "name": "X",
          "steps": [{"kind": "STEP", "stepId": "s1", "name": "S", "behaviour": {"type": "FAIL", "reason": "r"}}],
          "artifacts": {}
        }
        """);
    List<Error> violations = schema.validate(instance);
    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getMessage() != null && v.getMessage().contains("artifacts"));
  }

  @Test
  void workflowSchema_rejectsTopLevelBlueprints() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "x",
          "name": "X",
          "steps": [{"kind": "STEP", "stepId": "s1", "name": "S", "behaviour": {"type": "FAIL", "reason": "r"}}],
          "blueprints": {}
        }
        """);
    List<Error> violations = schema.validate(instance);
    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getMessage() != null && v.getMessage().contains("blueprints"));
  }

  @Test
  void workflowSchema_acceptsStepPrompt() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "x",
          "name": "X",
          "steps": [{"kind": "STEP", "stepId": "s1", "name": "S", "stepPrompt": "do the thing", "behaviour": {"type": "FAIL", "reason": "r"}}]
        }
        """);
    List<Error> violations = schema.validate(instance);
    assertThat(violations).isEmpty();
  }

  @Test
  void workflowSchema_rejectsUnsupportedSchemaVersion() throws Exception {
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "schemaVersion": 2,
          "id": "x",
          "name": "X",
          "steps": [
            {
              "kind": "STEP",
              "stepId": "s1",
              "name": "S",
              "behaviour": {
                "type": "FAIL",
                "reason": "r"
              }
            }
          ]
        }
        """);

    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    assertThat(schema.validate(instance)).isNotEmpty();
  }
}
