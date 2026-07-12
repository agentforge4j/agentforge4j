// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * {@link ArtifactValidator} for the full Agent Creator output bundle: the agent definition plus the generated
 * verification starter. It first runs the production agent-bundle parse/validate path (so {@code agent.json} must load
 * as a valid {@link com.agentforge4j.core.agent.AgentDefinition}, resolving its system prompt), then structurally
 * validates the verification starter — {@code verification/script.json} must parse as JSON and carry
 * {@code schemaVersion} and {@code responses}; {@code verification/expected-result.json} must parse as JSON and carry
 * {@code workflowId} and {@code expect.status}. Any failure yields an invalid result, which the runtime turns into a
 * fail-closed run. Required-file presence and path safety are enforced by the runtime around the validator.
 */
public final class AgentCreatorBundleValidator implements ArtifactValidator {

  /**
   * Stable id a {@code VALIDATE} step uses to select this validator.
   */
  public static final String VALIDATOR_ID = "agent-creator-bundle";

  private static final String SCRIPT_FILE = "verification/script.json";
  private static final String EXPECTED_RESULT_FILE = "verification/expected-result.json";

  private final AgentBundleArtifactValidator agentBundleValidator;
  private final ObjectMapper objectMapper;

  /**
   * Creates a validator with a default {@link ObjectMapper}.
   */
  public AgentCreatorBundleValidator() {
    this(new ObjectMapper());
  }

  /**
   * Creates a validator over the given mapper.
   *
   * @param objectMapper the mapper used to parse the bundle JSON; must not be {@code null}
   */
  public AgentCreatorBundleValidator(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.agentBundleValidator = new AgentBundleArtifactValidator(objectMapper);
  }

  @Override
  public String validatorId() {
    return VALIDATOR_ID;
  }

  @Override
  public ValidationResult validate(ArtifactValidationContext context) {
    ValidationResult agentResult = agentBundleValidator.validate(context);
    if (agentResult == null) {
      return ValidationResult.invalid("agent-bundle validation returned no result");
    }
    if (!agentResult.valid()) {
      return agentResult;
    }

    Map<String, String> artifacts = context.artifacts();
    // The delegated agent-bundle validation above already proved an "agent.json" artifact resolves
    // (bare or under a prefix), so re-deriving that same prefix here (deterministic given the same
    // artifacts map) locates the verification-starter files under the identical bundle root, rather
    // than assuming they sit at the map root.
    String agentJsonKey = BundleArtifactPaths.findKey(artifacts, "agent.json");
    String prefix = BundleArtifactPaths.prefixOf(agentJsonKey, "agent.json");
    String scriptPath = prefix + SCRIPT_FILE;
    String expectedResultPath = prefix + EXPECTED_RESULT_FILE;

    Parsed script = parse(artifacts.get(scriptPath), scriptPath);
    if (script.error() != null) {
      return script.error();
    }
    ValidationResult scriptFields = requireFields(script.node(), scriptPath, "schemaVersion", "responses");
    if (!scriptFields.valid()) {
      return scriptFields;
    }
    if (!script.node().get("responses").isArray()) {
      return ValidationResult.invalid(
          "%s field 'responses' must be a JSON array".formatted(scriptPath));
    }

    Parsed expected = parse(artifacts.get(expectedResultPath), expectedResultPath);
    if (expected.error() != null) {
      return expected.error();
    }
    ValidationResult expectedFields = requireFields(expected.node(), expectedResultPath, "workflowId", "expect");
    if (!expectedFields.valid()) {
      return expectedFields;
    }
    JsonNode expectNode = expected.node().get("expect");
    if (!expectNode.isObject()) {
      return ValidationResult.invalid(
          "%s field 'expect' must be a JSON object".formatted(expectedResultPath));
    }
    if (!expectNode.path("status").isTextual()) {
      return ValidationResult.invalid(
          "%s field 'expect.status' must be a non-null string".formatted(expectedResultPath));
    }
    return ValidationResult.ok();
  }

  private Parsed parse(String content, String fileName) {
    if (content == null) {
      return new Parsed(null,
          ValidationResult.invalid("verification bundle is missing required '%s'".formatted(fileName)));
    }
    try {
      return new Parsed(objectMapper.readTree(content), null);
    } catch (JacksonException e) {
      return new Parsed(null,
          ValidationResult.invalid("%s is not valid JSON: %s".formatted(fileName, e.getOriginalMessage())));
    }
  }

  private static ValidationResult requireFields(JsonNode root, String fileName, String... fields) {
    if (!root.isObject()) {
      return ValidationResult.invalid("%s must be a JSON object".formatted(fileName));
    }
    for (String field : fields) {
      if (!root.has(field)) {
        return ValidationResult.invalid("%s is missing required field '%s'".formatted(fileName, field));
      }
    }
    return ValidationResult.ok();
  }

  private record Parsed(JsonNode node, ValidationResult error) {
  }
}
