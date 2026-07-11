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
 * Contract lock for the {@code COLLECTION} behaviour in {@code workflow.schema.json}: a fully
 * declared collection gate is schema-valid, and the schema stays closed against unknown fields and
 * unknown policy values.
 */
class CollectionSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/workflow.schema.json").toAbsolutePath().normalize();

  @Test
  void workflowSchema_acceptsFullyDeclaredCollectionGate() throws Exception {
    List<Error> violations = validate("""
        {
          "kind": "WORKFLOW",
          "id": "adoption-applications",
          "name": "Adoption applications",
          "steps": [{
            "kind": "STEP",
            "stepId": "application-intake",
            "name": "Application intake",
            "behaviour": {
              "type": "COLLECTION",
              "itemSchemaRef": "adoption-application",
              "minItems": 1,
              "maxItems": 20,
              "maxItemsPerActor": 1,
              "maxInlinePayloadBytes": 65536,
              "duplicatePolicy": "REJECT_BY_CLIENT_TOKEN",
              "replacementPolicy": "OWNER_REPLACE",
              "withdrawalPolicy": "OWNER_WITHDRAW",
              "manualClose": true,
              "externalDeadlineClosable": false,
              "reopenPolicy": "NONE",
              "authorizationMode": "OPEN",
              "transition": "AUTO"
            }
          }]
        }
        """);

    assertThat(violations).isEmpty();
  }

  @Test
  void workflowSchema_acceptsMinimalCollectionGate() throws Exception {
    List<Error> violations = validate("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{
            "kind": "STEP",
            "stepId": "s1",
            "name": "S",
            "behaviour": {"type": "COLLECTION"}
          }]
        }
        """);

    assertThat(violations).isEmpty();
  }

  @Test
  void workflowSchema_rejectsUnknownCollectionField() throws Exception {
    List<Error> violations = validate("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{
            "kind": "STEP",
            "stepId": "s1",
            "name": "S",
            "behaviour": {"type": "COLLECTION", "rejectClientToken": true}
          }]
        }
        """);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void workflowSchema_rejectsBlankItemSchemaRef() throws Exception {
    List<Error> violations = validate("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{
            "kind": "STEP",
            "stepId": "s1",
            "name": "S",
            "behaviour": {"type": "COLLECTION", "itemSchemaRef": ""}
          }]
        }
        """);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void workflowSchema_acceptsExplicitNullForOptionalPolicyFields() throws Exception {
    List<Error> violations = validate("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{
            "kind": "STEP",
            "stepId": "s1",
            "name": "S",
            "behaviour": {
              "type": "COLLECTION",
              "duplicatePolicy": null,
              "replacementPolicy": null,
              "withdrawalPolicy": null,
              "reopenPolicy": null,
              "authorizationMode": null
            }
          }]
        }
        """);

    assertThat(violations).isEmpty();
  }

  @Test
  void workflowSchema_rejectsUnknownDuplicatePolicyValue() throws Exception {
    List<Error> violations = validate("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{
            "kind": "STEP",
            "stepId": "s1",
            "name": "S",
            "behaviour": {"type": "COLLECTION", "duplicatePolicy": "REJECT_ALL"}
          }]
        }
        """);

    assertThat(violations).isNotEmpty();
  }

  private static List<Error> validate(String workflowJson) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    return schema.validate(MAPPER.readTree(workflowJson));
  }
}
