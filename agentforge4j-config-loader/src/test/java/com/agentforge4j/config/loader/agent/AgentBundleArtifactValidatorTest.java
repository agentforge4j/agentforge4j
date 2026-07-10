// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBundleArtifactValidatorTest {

  private final AgentBundleArtifactValidator validator =
      new AgentBundleArtifactValidator(new ObjectMapper());

  private static final String VALID_AGENT_JSON = """
      {"id":"a1","name":"A","locality":"CLOUD",
       "providerPreferences":[{"provider":"openai","model":"gpt-4o-mini"}],
       "version":"1.0.0","supportedCommands":["COMPLETE"]}
      """;

  private static ArtifactValidationContext context(Map<String, String> artifacts) {
    return () -> artifacts;
  }

  @Test
  void validator_id_is_agent_bundle() {
    assertThat(validator.validatorId()).isEqualTo(AgentBundleArtifactValidator.VALIDATOR_ID);
  }

  @Test
  void valid_bundle_with_sibling_system_prompt_loads() {
    ValidationResult result = validator.validate(context(Map.of(
        "agent.json", VALID_AGENT_JSON,
        "systemprompt.md", "You are a helpful agent.")));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void valid_bundle_with_inline_system_prompt_loads() {
    ValidationResult result = validator.validate(context(Map.of(
        "agent.json", """
            {"id":"a1","name":"A","locality":"CLOUD","systemPrompt":"inline",
             "providerPreferences":[{"provider":"openai","model":"gpt-4o-mini"}],"version":"1.0.0"}
            """)));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void missing_agent_json_is_invalid() {
    ValidationResult result = validator.validate(context(Map.of("systemprompt.md", "sys")));

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("missing required 'agent.json'");
  }

  @Test
  void malformed_json_is_invalid() {
    ValidationResult result = validator.validate(context(Map.of("agent.json", "{not json")));

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("not valid JSON");
  }

  @Test
  void missing_required_field_is_invalid() {
    ValidationResult result = validator.validate(context(Map.of(
        "agent.json", """
            {"id":"a1","name":"A","locality":"CLOUD","systemPrompt":"inline","version":"1.0.0"}
            """)));

    assertThat(result.valid()).isFalse();
    assertThat(result.message())
        .contains("does not load as a valid AgentDefinition")
        .contains("providerPreference");
  }

  @Test
  void missing_system_prompt_is_invalid() {
    ValidationResult result = validator.validate(context(Map.of("agent.json", VALID_AGENT_JSON)));

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("systemprompt.md");
  }
}
