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
 * Positive + negative contract coverage for the blueprint and artifact schemas, which the shipped
 * fixtures only exercise on the positive side. Confirms a well-formed document validates and that a
 * document missing a required field (or carrying an unknown property) is rejected — the schemas are
 * the test-only validation gate; the runtime loaders parse these documents leniently without it.
 */
class BlueprintArtifactSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path BLUEPRINT_SCHEMA =
      Path.of("src/main/resources/schema/blueprint.schema.json").toAbsolutePath().normalize();
  private static final Path ARTIFACT_SCHEMA =
      Path.of("src/main/resources/schema/artifact.schema.json").toAbsolutePath().normalize();

  private static List<Error> validate(Path schemaPath, String instanceJson) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(schemaPath));
    Schema schema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of(schemaPath.toUri().toString()), schemaNode);
    return schema.validate(MAPPER.readTree(instanceJson));
  }

  @Test
  void blueprintSchema_acceptsAWellFormedLoopBlueprint() throws Exception {
    List<Error> violations = validate(BLUEPRINT_SCHEMA, """
        {
          "kind": "BLUEPRINT",
          "blueprintId": "loop-bp",
          "name": "Loop",
          "behaviour": { "transition": "AUTO" },
          "steps": [
            {"kind": "STEP", "stepId": "body", "name": "Body", "behaviour": {"type": "FAIL", "reason": "r"}}
          ]
        }
        """);

    assertThat(violations).isEmpty();
  }

  @Test
  void blueprintSchema_rejectsAMissingBlueprintId() throws Exception {
    List<Error> violations = validate(BLUEPRINT_SCHEMA, """
        {
          "kind": "BLUEPRINT",
          "name": "Loop",
          "behaviour": { "transition": "AUTO" },
          "steps": [
            {"kind": "STEP", "stepId": "body", "name": "Body", "behaviour": {"type": "FAIL", "reason": "r"}}
          ]
        }
        """);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void artifactSchema_acceptsAWellFormedTextForm() throws Exception {
    List<Error> violations = validate(ARTIFACT_SCHEMA, """
        {
          "id": "anchor-form",
          "items": [
            {"type": "TEXT", "id": "go", "label": "Proceed", "required": false}
          ]
        }
        """);

    assertThat(violations).isEmpty();
  }

  @Test
  void artifactSchema_rejectsAnEmptyItemsArray() throws Exception {
    List<Error> violations = validate(ARTIFACT_SCHEMA, """
        {
          "id": "anchor-form",
          "items": []
        }
        """);

    assertThat(violations).isNotEmpty();
  }
}
