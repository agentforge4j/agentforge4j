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
 * Contract tests for the token-governance additions to the workflow and agent schemas: the COMPACT
 * behaviour, the COLLECTION behaviour (previously missing), per-step context selection, workflow
 * ledgers, the PREMIUM tier, the TOOL_INVOCATION and REQUEST_CONTEXT commands, and agent output
 * contracts.
 */
class TokenGovernanceSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path WORKFLOW_SCHEMA =
      Path.of("src/main/resources/schema/workflow.schema.json").toAbsolutePath().normalize();
  private static final Path AGENT_SCHEMA =
      Path.of("src/main/resources/schema/agent.schema.json").toAbsolutePath().normalize();

  private static List<Error> validate(Path schemaPath, String instanceJson) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(schemaPath));
    Schema schema =
        SCHEMA_REGISTRY.getSchema(SchemaLocation.of(schemaPath.toUri().toString()), schemaNode);
    return schema.validate(MAPPER.readTree(instanceJson));
  }

  private static String workflowWithStep(String stepBody) {
    return """
        {"kind":"WORKFLOW","id":"x","name":"X","steps":[%s]}""".formatted(stepBody);
  }

  private static final String AGENT_BASE_FIELDS =
      "\"id\":\"a\",\"name\":\"A\",\"locality\":\"CLOUD\",\"version\":\"1.0.0\","
          + "\"providerPreferences\":[{\"provider\":\"openai\"}]";

  @Test
  void workflow_acceptsCompactStepWithLlmSummary() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"COMPACT",
         "source":{"kind":"LEDGER_SECTION","ref":"requirements"},
         "mode":{"type":"LLM_SUMMARY","modelTier":"STANDARD"},
         "policy":{"minSourceUnits":100,"minDownstreamReuse":1}}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isEmpty();
  }

  @Test
  void workflow_acceptsCompactStepWithDeterministicExtract() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"COMPACT",
         "source":{"kind":"ARTIFACT","ref":"a","variant":"COMPACT_ONLY"},
         "mode":{"type":"DETERMINISTIC_EXTRACT"},
         "policy":{"minSourceUnits":0,"minDownstreamReuse":0}}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isEmpty();
  }

  @Test
  void workflow_rejectsLlmSummaryWithoutModelTier() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"COMPACT",
         "source":{"kind":"ARTIFACT","ref":"a"},
         "mode":{"type":"LLM_SUMMARY"},
         "policy":{"minSourceUnits":0,"minDownstreamReuse":0}}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isNotEmpty();
  }

  @Test
  void workflow_rejectsLlmSummaryWithUnknownModelTier() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"COMPACT",
         "source":{"kind":"ARTIFACT","ref":"a"},
         "mode":{"type":"LLM_SUMMARY","modelTier":"PREMUIM"},
         "policy":{"minSourceUnits":0,"minDownstreamReuse":0}}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isNotEmpty();
  }

  @Test
  void workflow_acceptsCollectionStep() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"COLLECTION",
         "minItems":1,"duplicatePolicy":"ALLOW","reopenPolicy":"NONE",
         "authorizationMode":"OPEN","transition":"AUTO"}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isEmpty();
  }

  @Test
  void workflow_rejectsCollectionStepWithBlankItemSchemaRef() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"COLLECTION",
         "itemSchemaRef":"","minItems":1,"duplicatePolicy":"ALLOW","reopenPolicy":"NONE",
         "authorizationMode":"OPEN","transition":"AUTO"}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isNotEmpty();
  }

  @Test
  void workflow_acceptsStepContextSelectionAndPremiumTier() throws Exception {
    String step = """
        {"kind":"STEP","stepId":"s1","name":"S","modelTier":"PREMIUM",
         "behaviour":{"type":"FAIL","reason":"r"},
         "contextSelection":{
           "selectors":[{"kind":"ARTIFACT","ref":"design.md","variant":"COMPACT_PREFERRED"}],
           "expandableScope":[{"kind":"CONTEXT_PACK","ref":"coding-standards"}]}}""";
    assertThat(validate(WORKFLOW_SCHEMA, workflowWithStep(step))).isEmpty();
  }

  @Test
  void workflow_acceptsLedgersWithMergeByKey() throws Exception {
    String json = """
        {"kind":"WORKFLOW","id":"x","name":"X",
         "steps":[{"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"FAIL","reason":"r"}}],
         "ledgers":[{"id":"requirements","schemaRef":"schema/req.json",
           "mergeStrategy":"MERGE_BY_KEY","mergeKeyField":"id"}]}""";
    assertThat(validate(WORKFLOW_SCHEMA, json)).isEmpty();
  }

  @Test
  void workflow_rejectsMergeByKeyLedgerWithoutKeyField() throws Exception {
    String json = """
        {"kind":"WORKFLOW","id":"x","name":"X",
         "steps":[{"kind":"STEP","stepId":"s1","name":"S","behaviour":{"type":"FAIL","reason":"r"}}],
         "ledgers":[{"id":"r","schemaRef":"s","mergeStrategy":"MERGE_BY_KEY"}]}""";
    assertThat(validate(WORKFLOW_SCHEMA, json)).isNotEmpty();
  }

  @Test
  void agent_acceptsPremiumTierAndGatedCommands() throws Exception {
    String json = "{" + AGENT_BASE_FIELDS
        + ",\"modelTier\":\"PREMIUM\",\"supportedCommands\":[\"TOOL_INVOCATION\",\"REQUEST_CONTEXT\"]}";
    assertThat(validate(AGENT_SCHEMA, json)).isEmpty();
  }

  @Test
  void agent_acceptsStructuredOnlyOutputContractWithSchemaRef() throws Exception {
    String json = "{" + AGENT_BASE_FIELDS
        + ",\"outputContract\":{\"discipline\":\"STRUCTURED_ONLY\",\"schemaRef\":\"schema/out.json\","
        + "\"rationaleAllowed\":true}}";
    assertThat(validate(AGENT_SCHEMA, json)).isEmpty();
  }

  @Test
  void agent_rejectsStructuredOnlyOutputContractWithoutSchemaRef() throws Exception {
    String json = "{" + AGENT_BASE_FIELDS
        + ",\"outputContract\":{\"discipline\":\"STRUCTURED_ONLY\"}}";
    assertThat(validate(AGENT_SCHEMA, json)).isNotEmpty();
  }
}
