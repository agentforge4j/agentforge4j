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
 * {@code CollectionBehaviour} is a half-landed public surface kept intact for a planned
 * future completion (ADR-0014 / #19), but has no registered runtime handler. This pins the
 * existing protection that a JSON-authored workflow can never declare a {@code COLLECTION} step:
 * the step-type enum in {@code workflow.schema.json} omits it, so schema validation rejects the
 * document before it ever reaches a loader or the runtime.
 */
class WorkflowSchemaCollectionStepRejectionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/workflow.schema.json").toAbsolutePath().normalize();

  @Test
  void workflowSchema_rejectsCollectionStepType() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "x",
          "name": "X",
          "steps": [
            {
              "kind": "STEP",
              "stepId": "collect1",
              "name": "Collect",
              "behaviour": {
                "type": "COLLECTION",
                "minItems": 0
              }
            }
          ]
        }
        """);

    List<Error> violations = schema.validate(instance);

    // The step-type enum in workflow.schema.json omits COLLECTION, so no branch of the
    // behaviour oneOf/type-enum accepts it; the enum violation on /steps/0/behaviour/type is the
    // one that pins this specific protection (as opposed to some unrelated missing field).
    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getInstanceLocation() != null
            && v.getInstanceLocation().toString().contains("behaviour/type")
            && v.getMessage() != null
            && v.getMessage().contains("does not have a value in the enumeration"));
  }
}
