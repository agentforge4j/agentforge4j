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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the authoring surface for capability tiers: the {@code modelTier} enums in
 * {@code agent.schema.json} (agent-level) and {@code workflow.schema.json} (step-level override)
 * accept exactly the four declared tiers and reject anything else.
 *
 * <p>This module deliberately has no dependency on the LLM API, so the expected set is spelled out
 * here; {@code ModelTierSchemaAlignmentTest} in {@code agentforge4j-oss-verification} is the test
 * that ties these enums back to {@code ModelTier} itself. What this test adds is the direction the
 * alignment test cannot see: that a declared tier really validates against the whole document, not
 * just that the string appears in an enum array.
 */
class ModelTierSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path MODULE_ROOT = Path.of(".").toAbsolutePath().normalize();
  private static final Path AGENT_SCHEMA_PATH =
      MODULE_ROOT.resolve("src/main/resources/schema/agent.schema.json");
  private static final Path WORKFLOW_SCHEMA_PATH =
      MODULE_ROOT.resolve("src/main/resources/schema/workflow.schema.json");
  private static final Path BUILDER_AGENT_SCHEMA_PATH = MODULE_ROOT.getParent()
      .resolve("agentforge4j-workflow-builder/src/schemas/agent.schema.json");

  private static final List<String> DECLARED_TIERS =
      List.of("LITE", "STANDARD", "POWERFUL", "PREMIUM");

  @Test
  void agentSchemaTierEnumListsExactlyTheDeclaredTiers() throws Exception {
    assertThat(tierEnumOf(AGENT_SCHEMA_PATH, "/properties/modelTier/enum"))
        .containsExactlyInAnyOrderElementsOf(DECLARED_TIERS);
  }

  @Test
  void workflowSchemaStepTierEnumListsExactlyTheDeclaredTiers() throws Exception {
    assertThat(tierEnumOf(WORKFLOW_SCHEMA_PATH, "/$defs/StepDefinition/properties/modelTier/enum"))
        .containsExactlyInAnyOrderElementsOf(DECLARED_TIERS);
  }

  @Test
  void builderAgentSchemaMirrorsTheCanonicalAgentSchema() throws Exception {
    // The workflow builder ships its own copy of agent.schema.json in its published package.
    // Unlike workflow.schema.json, which the builder's scripts/sync-schema.mjs copies from here
    // at build time, that copy is maintained by hand, so nothing but this assertion keeps it from
    // drifting away from the canonical schema it mirrors. The claim being enforced is alignment
    // of a published contract surface, not builder behaviour: the builder does not compile or
    // validate documents against this schema today.
    assertThat(Files.exists(BUILDER_AGENT_SCHEMA_PATH))
        .as("expected the builder's hand-maintained copy of agent.schema.json at %s. It is a "
            + "published contract surface this test keeps aligned with the canonical schema, so "
            + "the check needs the whole repository checked out, not this module alone.",
            BUILDER_AGENT_SCHEMA_PATH)
        .isTrue();

    assertThat(MAPPER.readTree(Files.readString(BUILDER_AGENT_SCHEMA_PATH)))
        .isEqualTo(MAPPER.readTree(Files.readString(AGENT_SCHEMA_PATH)));
  }

  @Test
  void agentSchemaAcceptsEveryDeclaredTier() throws Exception {
    Schema schema = schema(AGENT_SCHEMA_PATH);

    for (String tier : DECLARED_TIERS) {
      assertThat(schema.validate(MAPPER.readTree(agentJson("\"" + tier + "\""))))
          .as("agent.schema.json must accept modelTier %s", tier)
          .isEmpty();
    }
  }

  @Test
  void agentSchemaRejectsAnUndeclaredTier() throws Exception {
    List<Error> violations =
        schema(AGENT_SCHEMA_PATH).validate(MAPPER.readTree(agentJson("\"SUPREME\"")));

    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getInstanceLocation() != null
            && v.getInstanceLocation().toString().contains("modelTier"));
  }

  @Test
  void workflowSchemaAcceptsEveryDeclaredTierAsAStepOverride() throws Exception {
    Schema schema = schema(WORKFLOW_SCHEMA_PATH);

    for (String tier : DECLARED_TIERS) {
      assertThat(schema.validate(MAPPER.readTree(workflowJson("\"" + tier + "\""))))
          .as("workflow.schema.json must accept step modelTier %s", tier)
          .isEmpty();
    }
  }

  @Test
  void workflowSchemaRejectsAnUndeclaredStepTier() throws Exception {
    List<Error> violations =
        schema(WORKFLOW_SCHEMA_PATH).validate(MAPPER.readTree(workflowJson("\"SUPREME\"")));

    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getInstanceLocation() != null
            && v.getInstanceLocation().toString().contains("modelTier"));
  }

  /**
   * Reads a {@code modelTier} enum and returns its declared tier names, dropping the {@code null}
   * member (which encodes "no tier declared", not a tier).
   */
  private static List<String> tierEnumOf(Path schemaPath, String pointer) throws Exception {
    JsonNode enumNode = MAPPER.readTree(Files.readString(schemaPath)).at(pointer);
    assertThat(enumNode.isArray()).as("no modelTier enum at %s in %s", pointer, schemaPath).isTrue();

    List<String> names = new ArrayList<>();
    enumNode.forEach(node -> {
      if (!node.isNull()) {
        names.add(node.asText());
      }
    });
    return names;
  }

  private static Schema schema(Path schemaPath) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(schemaPath));
    return SCHEMA_REGISTRY.getSchema(SchemaLocation.of(schemaPath.toUri().toString()), schemaNode);
  }

  private static String agentJson(String tierLiteral) {
    return """
        {
          "id": "a1",
          "name": "A",
          "locality": "CLOUD",
          "version": "1.0.0",
          "providerPreferences": [{ "provider": "claude" }],
          "modelTier": %s
        }
        """.formatted(tierLiteral);
  }

  private static String workflowJson(String tierLiteral) {
    return """
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "wf-1",
          "name": "WF",
          "steps": [
            {
              "kind": "STEP",
              "stepId": "s1",
              "name": "S",
              "modelTier": %s,
              "behaviour": { "type": "AGENT", "agentRef": "a1", "transition": "AUTO" }
            }
          ]
        }
        """.formatted(tierLiteral);
  }
}
