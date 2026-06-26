// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.context.StringContextValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code type} discriminator wiring for the artifact-validation behaviours, so the
 * config loader (Jackson) deserializes them from workflow JSON.
 */
class StepBehaviourJsonMappingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserializes_validate_behaviour() throws Exception {
    String json = """
        {"type":"VALIDATE","validatorId":"agent-bundle",
         "requiredArtifacts":["agent.json","systemprompt.md"],
         "contextEqualityContracts":[
           {"artifactPath":"agent.json","jsonPointer":"/modelTier","contextKey":"recommendedTier"}]}
        """;

    StepBehaviour behaviour = mapper.readValue(json, StepBehaviour.class);

    assertThat(behaviour).isInstanceOf(ValidateBehaviour.class);
    ValidateBehaviour validate = (ValidateBehaviour) behaviour;
    assertThat(validate.validatorId()).isEqualTo("agent-bundle");
    assertThat(validate.requiredArtifacts()).containsExactly("agent.json", "systemprompt.md");
    assertThat(validate.contextEqualityContracts()).hasSize(1);
    assertThat(validate.contextEqualityContracts().get(0).contextKey()).isEqualTo("recommendedTier");
  }

  @Test
  void deserializes_assign_context_behaviour_with_scalar_value() throws Exception {
    String json = """
        {"type":"ASSIGN_CONTEXT","contextKey":"recommendedTier",
         "value":{"type":"STRING","value":"POWERFUL"}}
        """;

    StepBehaviour behaviour = mapper.readValue(json, StepBehaviour.class);

    assertThat(behaviour).isInstanceOf(AssignContextBehaviour.class);
    AssignContextBehaviour assign = (AssignContextBehaviour) behaviour;
    assertThat(assign.contextKey()).isEqualTo("recommendedTier");
    assertThat(assign.value()).isInstanceOf(StringContextValue.class);
    assertThat(((StringContextValue) assign.value()).value()).isEqualTo("POWERFUL");
  }

  @Test
  void deserializes_branch_behaviour_with_predicates_and_fail_on_unmatched() throws Exception {
    String json = """
        {"type":"BRANCH","contextKey":"tier",
         "predicates":[{"kind":"MEMBER_OF","members":["LITE","STANDARD"]}],
         "failOnUnmatched":true}
        """;

    StepBehaviour behaviour = mapper.readValue(json, StepBehaviour.class);

    assertThat(behaviour).isInstanceOf(BranchBehaviour.class);
    BranchBehaviour branch = (BranchBehaviour) behaviour;
    assertThat(branch.failOnUnmatched()).isTrue();
    assertThat(branch.predicates()).hasSize(1);
    assertThat(branch.predicates().get(0).kind()).isEqualTo(BranchPredicateKind.MEMBER_OF);
    assertThat(branch.predicates().get(0).members()).containsExactlyInAnyOrder("LITE", "STANDARD");
  }

  @Test
  void unknown_predicate_kind_fails_to_deserialize() {
    String json = """
        {"type":"BRANCH","contextKey":"tier","predicates":[{"kind":"BOGUS"}]}
        """;

    assertThatThrownBy(() -> mapper.readValue(json, StepBehaviour.class))
        .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
  }
}
