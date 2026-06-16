// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.llm.api.TokenUsageReport;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_MODEL;
import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentInvocationResultTest {

  @Test
  void builder_with_test_constants_round_trips_correctly() {
    List<LlmCommand> commands = List.of(new CompleteCommand(null));

    AgentInvocationResult result = AgentInvocationResult.builder()
        .withRawResponse("raw")
        .withCommands(commands)
        .withModelUsed(TEST_MODEL)
        .withTokenUsage(TEST_TOKEN_USAGE)
        .build();

    assertThat(result.rawResponse()).isEqualTo("raw");
    assertThat(result.commands()).hasSize(1);
    assertThat(result.modelUsed()).isEqualTo(TEST_MODEL);
    assertThat(result.tokenUsage()).isEqualTo(TEST_TOKEN_USAGE);
  }

  @Test
  void builder_defaults_tier_metadata_when_only_core_components_set() {
    List<LlmCommand> commands = List.of(new CompleteCommand("done"));

    AgentInvocationResult result = AgentInvocationResult.builder()
        .withRawResponse("response-text")
        .withCommands(commands)
        .withModelUsed("model-x")
        .withTokenUsage(new TokenUsageReport(1, 2, 3, 4))
        .build();

    assertThat(result.rawResponse()).isEqualTo("response-text");
    assertThat(result.commands()).containsExactlyElementsOf(commands);
    assertThat(result.modelUsed()).isEqualTo("model-x");
    assertThat(result.resolvedModel()).isNull();
    assertThat(result.modelSource()).isEqualTo(ModelSource.PROVIDER_DEFAULT);
    assertThat(result.requestedModelTier()).isNull();
  }

  @Test
  void existing_rawResponse_and_commands_validation_unchanged() {
    TokenUsageReport usage = new TokenUsageReport(1, 2, null, null);
    List<LlmCommand> commands = List.of(new CompleteCommand(null));

    assertThatThrownBy(() -> AgentInvocationResult.builder()
        .withRawResponse("  ").withCommands(commands).withTokenUsage(usage).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawResponse must not be blank");

    assertThatThrownBy(() -> AgentInvocationResult.builder()
        .withRawResponse("ok").withCommands(null).withModelUsed("model").withTokenUsage(usage)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commands must not be null");

    AgentInvocationResult withNullUsageFields = AgentInvocationResult.builder()
        .withRawResponse("ok").withCommands(commands).build();
    assertThat(withNullUsageFields.modelUsed()).isNull();
    assertThat(withNullUsageFields.tokenUsage()).isNull();
  }
}
