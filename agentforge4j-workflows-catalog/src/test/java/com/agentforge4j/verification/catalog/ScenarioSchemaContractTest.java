// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.testkit.scenario.ScenarioSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Positive + negative contract coverage for the testkit-owned scenario schema. The schema is loaded
 * from the published testkit artifact via {@link ScenarioSchema} (a classpath resource), not from a
 * sibling source path, so the same loading path serves a future external catalog repo consuming the
 * testkit jar. Confirms every shipped {@code expected-result.json} fixture validates, that the full
 * gate vocabulary (including the tool gates) is accepted, and that malformed scenarios are rejected.
 * The runtime scenario loader parses these documents leniently ({@code @JsonIgnoreProperties}); this
 * schema is the test-only validation gate.
 */
class ScenarioSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Schema SCENARIO_SCHEMA = loadScenarioSchema();

  private static Schema loadScenarioSchema() {
    try {
      // Loaded from the testkit classpath resource (the published artifact), not a source path.
      JsonNode schemaNode = MAPPER.readTree(ScenarioSchema.json());
      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
          SpecificationVersion.DRAFT_2020_12, builder -> {
          });
      return registry.getSchema(
          SchemaLocation.of("classpath:/com/agentforge4j/testkit/scenario/" + ScenarioSchema.RESOURCE_NAME),
          schemaNode);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load scenario schema", e);
    }
  }

  @Test
  void everyShippedScenarioFixtureValidatesAgainstTheSchema() throws IOException {
    List<ScenarioCase> scenarios = CatalogScenarios.discover();
    assertThat(scenarios).as("the shipped catalog must own at least one scenario").isNotEmpty();
    for (ScenarioCase scenario : scenarios) {
      List<Error> violations =
          SCENARIO_SCHEMA.validate(MAPPER.readTree(scenario.expectedResultJson()));
      assertThat(violations)
          .as("scenario '%s' expected-result.json must validate against the scenario schema",
              scenario.name())
          .isEmpty();
    }
  }

  @Test
  void schemaAcceptsAWellFormedScenario() throws IOException {
    List<Error> violations = SCENARIO_SCHEMA.validate(MAPPER.readTree("""
        {
          "workflowId": "demo",
          "gates": [
            {"type": "input", "answers": {"name": "x"}},
            {"type": "stepApproval", "approve": true, "note": "ok"},
            {"type": "review", "note": "looks good"}
          ],
          "expect": {"status": "COMPLETED", "visitedSteps": ["s1", "s2"]}
        }
        """));

    assertThat(violations).isEmpty();
  }

  @Test
  void schemaAcceptsContextPresenceAndRegexShapeAssertions() throws IOException {
    List<Error> violations = SCENARIO_SCHEMA.validate(MAPPER.readTree("""
        {
          "workflowId": "demo",
          "expect": {
            "status": "AWAITING_STEP_APPROVAL",
            "contextPresent": ["estimatedMinTokens", "estimatedMaxTokens"],
            "contextMatches": {"confidence": "HIGH|MEDIUM|LOW|VERY_LOW"}
          }
        }
        """));

    assertThat(violations).isEmpty();
  }

  @Test
  void schemaAcceptsAllToolGateVerbs() throws IOException {
    List<Error> violations = SCENARIO_SCHEMA.validate(MAPPER.readTree("""
        {
          "workflowId": "tool-demo",
          "gates": [
            {"type": "toolApprove"},
            {"type": "toolReject", "reason": "denied by operator"},
            {"type": "toolContinue"},
            {"type": "toolRetry"},
            {"type": "toolApprove", "toolInvocationId": "tool-1"}
          ],
          "expect": {"status": "COMPLETED"}
        }
        """));

    assertThat(violations).isEmpty();
  }

  @Test
  void schemaAcceptsStepVisitCountsAndOrderedSteps() throws IOException {
    List<Error> violations = SCENARIO_SCHEMA.validate(MAPPER.readTree("""
        {
          "workflowId": "demo",
          "expect": {
            "status": "COMPLETED",
            "stepVisitCounts": {"revise": 2},
            "orderedSteps": ["draft", "revise", "publish"]
          }
        }
        """));

    assertThat(violations).isEmpty();
  }

  @Test
  void schemaRejectsAMissingWorkflowId() throws IOException {
    assertThat(SCENARIO_SCHEMA.validate(MAPPER.readTree("{\"gates\": []}"))).isNotEmpty();
  }

  @Test
  void schemaRejectsAnUnknownGateType() throws IOException {
    assertThat(SCENARIO_SCHEMA.validate(
        MAPPER.readTree("{\"workflowId\": \"w\", \"gates\": [{\"type\": \"telepathy\"}]}")))
        .isNotEmpty();
  }

  @Test
  void schemaRejectsAToolGateWithAnUnknownProperty() throws IOException {
    assertThat(SCENARIO_SCHEMA.validate(MAPPER.readTree(
        "{\"workflowId\": \"w\", \"gates\": [{\"type\": \"toolApprove\", \"bogus\": true}]}")))
        .isNotEmpty();
  }

  @Test
  void schemaRejectsAToolGateWithABlankInvocationId() throws IOException {
    assertThat(SCENARIO_SCHEMA.validate(MAPPER.readTree(
        "{\"workflowId\": \"w\", \"gates\": [{\"type\": \"toolRetry\", \"toolInvocationId\": \"\"}]}")))
        .isNotEmpty();
  }

  @Test
  void schemaRejectsAnUnknownTopLevelProperty() throws IOException {
    assertThat(SCENARIO_SCHEMA.validate(
        MAPPER.readTree("{\"workflowId\": \"w\", \"surprise\": true}")))
        .isNotEmpty();
  }
}
