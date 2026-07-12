// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.spi.validation.ValidationResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link AgentCreatorBundleValidator}: the agent definition must load and the generated verification
 * starter must be structurally valid (parseable JSON carrying the required fields), with every failure mode failing
 * closed.
 */
class AgentCreatorBundleValidatorTest {

  private static final String AGENT_JSON =
      "{\"id\":\"a\",\"name\":\"A\",\"locality\":\"CLOUD\","
          + "\"providerPreferences\":[{\"provider\":\"openai\",\"model\":null}],\"version\":\"1.0.0\"}";
  private static final String SYSTEM_PROMPT = "You are an agent.";
  private static final String SCRIPT_OK = "{\"schemaVersion\":1,\"responses\":[]}";
  private static final String EXPECTED_OK =
      "{\"workflowId\":\"x\",\"gates\":[],\"expect\":{\"status\":\"COMPLETED\"}}";

  private final AgentCreatorBundleValidator validator = new AgentCreatorBundleValidator();

  private static Map<String, String> bundle(String script, String expected) {
    return bundle("", script, expected);
  }

  private static Map<String, String> bundle(String prefix, String script, String expected) {
    Map<String, String> artifacts = new HashMap<>();
    artifacts.put(prefix + "agent.json", AGENT_JSON);
    artifacts.put(prefix + "systemprompt.md", SYSTEM_PROMPT);
    artifacts.put(prefix + "README.md", "# readme");
    if (script != null) {
      artifacts.put(prefix + "verification/script.json", script);
    }
    if (expected != null) {
      artifacts.put(prefix + "verification/expected-result.json", expected);
    }
    artifacts.put(prefix + "verification/README.md", "# starter");
    return artifacts;
  }

  @Test
  void validatorIdIsAgentCreatorBundle() {
    assertThat(validator.validatorId()).isEqualTo("agent-creator-bundle");
  }

  @Test
  void validBundlePasses() {
    Map<String, String> artifacts = bundle(SCRIPT_OK, EXPECTED_OK);
    assertThat(validator.validate(() -> artifacts).valid()).isTrue();
  }

  @Test
  void invalidAgentDefinitionFails() {
    Map<String, String> artifacts = bundle(SCRIPT_OK, EXPECTED_OK);
    artifacts.remove("agent.json");
    assertThat(validator.validate(() -> artifacts).valid()).isFalse();
  }

  @Test
  void malformedScriptJsonFails() {
    Map<String, String> artifacts = bundle("{ not json", EXPECTED_OK);
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("verification/script.json", "not valid JSON");
  }

  @Test
  void scriptMissingRequiredFieldFails() {
    Map<String, String> artifacts = bundle("{\"schemaVersion\":1}", EXPECTED_OK);
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("responses");
  }

  @Test
  void expectedResultMissingStatusFails() {
    Map<String, String> artifacts = bundle(SCRIPT_OK, "{\"workflowId\":\"x\",\"expect\":{}}");
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("expect.status");
  }

  @Test
  void scriptResponsesNotArrayFails() {
    Map<String, String> artifacts = bundle("{\"schemaVersion\":1,\"responses\":\"nope\"}", EXPECTED_OK);
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("responses", "array");
  }

  @Test
  void expectNotObjectFails() {
    Map<String, String> artifacts = bundle(SCRIPT_OK, "{\"workflowId\":\"x\",\"expect\":\"nope\"}");
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("expect", "object");
  }

  @Test
  void expectStatusNotTextualFails() {
    Map<String, String> artifacts = bundle(SCRIPT_OK, "{\"workflowId\":\"x\",\"expect\":{\"status\":123}}");
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("expect.status");
  }

  @Test
  void missingVerificationFileFails() {
    Map<String, String> artifacts = bundle(null, EXPECTED_OK);
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("verification/script.json");
  }

  /**
   * Regression test for a defect where this validator looked up the verification-starter files by
   * bare relative names ({@code "verification/script.json"}), but {@code ValidateBehaviourHandler}
   * captures them keyed by the step's full declared path (e.g.
   * {@code "shipped-agents/generated.agent/verification/script.json"}), so a prefixed bundle always
   * failed regardless of content. Proves the fix resolves the whole bundle (agent.json plus
   * verification starter) relative to whichever prefix the captured {@code agent.json} key uses.
   */
  @Test
  void validBundleUnderAnArbitraryPrefixPasses() {
    Map<String, String> artifacts = bundle("shipped-agents/generated.agent/", SCRIPT_OK, EXPECTED_OK);
    assertThat(validator.validate(() -> artifacts).valid()).isTrue();
  }

  @Test
  void malformedScriptJsonUnderAPrefixFails() {
    Map<String, String> artifacts = bundle("shipped-agents/generated.agent/", "{ not json", EXPECTED_OK);
    ValidationResult result = validator.validate(() -> artifacts);
    assertThat(result.valid()).isFalse();
    assertThat(result.message())
        .contains("shipped-agents/generated.agent/verification/script.json", "not valid JSON");
  }
}
