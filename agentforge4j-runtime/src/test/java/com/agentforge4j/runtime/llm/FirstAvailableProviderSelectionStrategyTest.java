package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.llm.api.LlmInvocationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirstAvailableProviderSelectionStrategyTest {

  private final FirstAvailableProviderSelectionStrategy strategy =
      new FirstAvailableProviderSelectionStrategy();

  @Test
  void selectsFirstAvailableProviderInPreferenceOrder() {
    AgentDefinition agent = agentWithPreferences(
        new ProviderPreference("openai", "gpt-4o"),
        new ProviderPreference("ollama", "llama3"));

    ProviderPreference selected = strategy.selectInitialProvider(agent,
        List.of("openai", "ollama"));

    assertThat(selected).isEqualTo(new ProviderPreference("openai", "gpt-4o"));
  }

  @Test
  void skipsUnavailableProviders() {
    AgentDefinition agent = agentWithPreferences(
        new ProviderPreference("openai", "gpt-4o"),
        new ProviderPreference("ollama", "llama3"));

    ProviderPreference selected = strategy.selectInitialProvider(agent,
        List.of("ollama"));

    assertThat(selected).isEqualTo(new ProviderPreference("ollama", "llama3"));
  }

  @Test
  void throwsWhenNoPreferredProviderIsAvailable() {
    AgentDefinition agent = agentWithPreferences(
        new ProviderPreference("openai", "gpt-4o"));

    assertThatThrownBy(() -> strategy.selectInitialProvider(agent, List.of("ollama")))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessage("Agent 'agent-1' has no available provider preferences");
  }

  private static AgentDefinition agentWithPreferences(ProviderPreference... preferences) {
    return AgentDefinition.builder()
        .withId("agent-1")
        .withName("Agent")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(preferences))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
  }
}
