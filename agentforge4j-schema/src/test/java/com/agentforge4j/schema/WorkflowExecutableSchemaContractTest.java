package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ensures {@code workflow.schema.json} describes only the executable workflow document, not a
 * merged bundle manifest.
 */
class WorkflowExecutableSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemaFactory SCHEMA_FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/workflow.schema.json").toAbsolutePath().normalize();

  @Test
  void workflowSchema_rejectsTopLevelArtifacts() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    JsonSchema schema = SCHEMA_FACTORY.getSchema(SCHEMA_PATH.toUri(), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{"kind": "STEP", "stepId": "s1", "name": "S", "behaviour": {"type": "FAIL", "reason": "r"}}],
          "artifacts": {}
        }
        """);
    Set<ValidationMessage> violations = schema.validate(instance);
    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getMessage() != null && v.getMessage().contains("artifacts"));
  }

  @Test
  void workflowSchema_rejectsTopLevelBlueprints() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    JsonSchema schema = SCHEMA_FACTORY.getSchema(SCHEMA_PATH.toUri(), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{"kind": "STEP", "stepId": "s1", "name": "S", "behaviour": {"type": "FAIL", "reason": "r"}}],
          "blueprints": {}
        }
        """);
    Set<ValidationMessage> violations = schema.validate(instance);
    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getMessage() != null && v.getMessage().contains("blueprints"));
  }

  @Test
  void workflowSchema_acceptsStepPrompt() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    JsonSchema schema = SCHEMA_FACTORY.getSchema(SCHEMA_PATH.toUri(), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "id": "x",
          "name": "X",
          "steps": [{"kind": "STEP", "stepId": "s1", "name": "S", "stepPrompt": "do the thing", "behaviour": {"type": "FAIL", "reason": "r"}}]
        }
        """);
    Set<ValidationMessage> violations = schema.validate(instance);
    assertThat(violations).isEmpty();
  }
}
