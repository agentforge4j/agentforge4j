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
 * {@code RetryPolicy} shrank from five fields to three ({@code allowRetry},
 * {@code allowRetryFromPrevious}, {@code maxAttempts}) — {@code allowAgentSwap} and
 * {@code allowPromptOverride} were removed as unsupported, decorative promises with no backing
 * runtime operation. Guards that {@code workflow.schema.json}'s {@code RetryPolicy} $def matches: an
 * AGENT step declaring the 3-field shape validates cleanly, and the old 5-field shape (including
 * either removed field) is now rejected by {@code additionalProperties: false}.
 */
class RetryPolicySchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/workflow.schema.json").toAbsolutePath().normalize();

  @Test
  void currentThreeFieldRetryPolicyShapeValidates() throws Exception {
    List<Error> violations = validateWorkflowWithRetryPolicy("""
        {
          "allowRetry": true,
          "allowRetryFromPrevious": false,
          "maxAttempts": 2
        }
        """);

    assertThat(violations).isEmpty();
  }

  @Test
  void removedAllowAgentSwapFieldIsRejected() throws Exception {
    List<Error> violations = validateWorkflowWithRetryPolicy("""
        {
          "allowRetry": true,
          "allowRetryFromPrevious": false,
          "allowAgentSwap": false,
          "maxAttempts": 2
        }
        """);

    // The violation must be the additionalProperties rejection on the retryPolicy node itself —
    // pinned by instance location, not a loose string probe over the whole violation list.
    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getInstanceLocation() != null
            && v.getInstanceLocation().toString().contains("behaviour/retryPolicy")
            && v.getMessage() != null
            && v.getMessage().contains("allowAgentSwap"));
  }

  @Test
  void removedAllowPromptOverrideFieldIsRejected() throws Exception {
    List<Error> violations = validateWorkflowWithRetryPolicy("""
        {
          "allowRetry": true,
          "allowRetryFromPrevious": false,
          "allowPromptOverride": false,
          "maxAttempts": 2
        }
        """);

    assertThat(violations)
        .isNotEmpty()
        .anyMatch(v -> v.getInstanceLocation() != null
            && v.getInstanceLocation().toString().contains("behaviour/retryPolicy")
            && v.getMessage() != null
            && v.getMessage().contains("allowPromptOverride"));
  }

  private static List<Error> validateWorkflowWithRetryPolicy(String retryPolicyJson)
      throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    JsonNode instance = MAPPER.readTree("""
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "x",
          "name": "X",
          "steps": [{
            "kind": "STEP",
            "stepId": "s1",
            "name": "S",
            "behaviour": {
              "type": "AGENT",
              "agentRef": "agent-1",
              "transition": "AUTO",
              "retryPolicy": %s
            }
          }]
        }
        """.formatted(retryPolicyJson));
    return schema.validate(instance);
  }
}
