// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * {@link ArtifactValidator} that asserts a generated agent bundle "loads as a valid {@code AgentDefinition}" by running
 * the production parse/validate path on the captured artifacts: Jackson-parse {@code agent.json}, validate core fields,
 * resolve the system prompt (inline or from the captured {@code systemprompt.md} / {@code boundaries.md}), and build
 * the definition (applying the record's invariants). Any parse, validation, or build failure yields an invalid result,
 * which the runtime turns into a fail-closed run.
 */
public final class AgentBundleArtifactValidator implements ArtifactValidator {

  /**
   * Stable id a {@code VALIDATE} step uses to select this validator.
   */
  public static final String VALIDATOR_ID = "agent-bundle";
  /**
   * Required bundle entry carrying the agent definition JSON.
   */
  public static final String AGENT_FILE_NAME = "agent.json";

  private final ObjectMapper objectMapper;
  private final AgentDefinitionAssembler assembler;

  /**
   * Creates a validator over the given mapper.
   *
   * @param objectMapper the mapper used to parse {@code agent.json}; must not be {@code null}
   */
  public AgentBundleArtifactValidator(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.assembler = new AgentDefinitionAssembler();
  }

  @Override
  public String validatorId() {
    return VALIDATOR_ID;
  }

  @Override
  public ValidationResult validate(ArtifactValidationContext context) {
    Map<String, String> artifacts = context.artifacts();
    String matchedKey = BundleArtifactPaths.findKey(artifacts, AGENT_FILE_NAME);
    if (matchedKey == null) {
      return ValidationResult.invalid(
          "agent bundle is missing required '%s'".formatted(AGENT_FILE_NAME));
    }
    String prefix = BundleArtifactPaths.prefixOf(matchedKey, AGENT_FILE_NAME);
    String agentJson = artifacts.get(matchedKey);
    AgentDefinitionFile file;
    try {
      file = objectMapper.readValue(agentJson, AgentDefinitionFile.class);
    } catch (JsonProcessingException e) {
      return ValidationResult.invalid(
          "%s is not valid JSON: %s".formatted(AGENT_FILE_NAME, e.getOriginalMessage()));
    }
    try {
      assembler.assemble(file, matchedKey, name -> artifacts.get(prefix + name));
    } catch (RuntimeException e) {
      return ValidationResult.invalid(
          "agent bundle does not load as a valid AgentDefinition: %s".formatted(e.getMessage()));
    }
    return ValidationResult.ok();
  }
}
