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
  void canonical_constructor_with_test_constants_round_trips_correctly() {
    List<LlmCommand> commands = List.of(new CompleteCommand(null));

    AgentInvocationResult result = new AgentInvocationResult(
        "raw", commands, TEST_MODEL, TEST_TOKEN_USAGE);

    assertThat(result.rawResponse()).isEqualTo("raw");
    assertThat(result.commands()).hasSize(1);
    assertThat(result.modelUsed()).isEqualTo(TEST_MODEL);
    assertThat(result.tokenUsage()).isEqualTo(TEST_TOKEN_USAGE);
  }

  @Test
  void canonical_constructor_preserves_all_four_components() {
    TokenUsageReport usage = new TokenUsageReport(1, 2, 3, 4);
    List<LlmCommand> commands = List.of(new CompleteCommand("done"));

    AgentInvocationResult result = new AgentInvocationResult(
        "response-text", commands, "model-x", usage);

    assertThat(result.rawResponse()).isEqualTo("response-text");
    assertThat(result.commands()).containsExactlyElementsOf(commands);
    assertThat(result.modelUsed()).isEqualTo("model-x");
    assertThat(result.tokenUsage()).isEqualTo(usage);
  }

  @Test
  void existing_rawResponse_and_commands_validation_unchanged() {
    TokenUsageReport usage = new TokenUsageReport(1, 2, null, null);
    List<LlmCommand> commands = List.of(new CompleteCommand(null));

    assertThatThrownBy(() -> new AgentInvocationResult("  ", commands, null, usage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawResponse must not be blank");

    assertThatThrownBy(() -> new AgentInvocationResult("ok", null, "model", usage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commands must not be null");

    AgentInvocationResult withNullUsageFields = new AgentInvocationResult(
        "ok", commands, null, null);
    assertThat(withNullUsageFields.modelUsed()).isNull();
    assertThat(withNullUsageFields.tokenUsage()).isNull();
  }
}
