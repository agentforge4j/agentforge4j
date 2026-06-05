package com.agentforge4j.config.loader.repository;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.exception.AgentNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAgentRepositoryTest {

  @Test
  void get_returnsAgentFromCurrentSnapshot() {
    AgentDefinition globalAgent = AgentDefinition.builder()
        .withId("agent-a")
        .withName("Global")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("global")
        .withProviderPreferences(List.of(new ProviderPreference("OPENAI", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
    InMemoryAgentRepository repository = new InMemoryAgentRepository(Map.of("agent-a", globalAgent));

    assertThat(repository.get("agent-a").systemPrompt()).isEqualTo("global");
  }

  @Test
  void get_throwsWhenAgentIsMissing() {
    InMemoryAgentRepository repository = new InMemoryAgentRepository(Map.of());

    assertThatThrownBy(() -> repository.get("missing"))
        .isInstanceOf(AgentNotFoundException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void replaceGlobalAgents_swapsSnapshotForSubsequentReads() {
    AgentDefinition initialAgent = AgentDefinition.builder()
        .withId("agent-a")
        .withName("Initial")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("initial")
        .withProviderPreferences(List.of(new ProviderPreference("OPENAI", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
    AgentDefinition replacementAgent = AgentDefinition.builder()
        .withId("agent-b")
        .withName("Replacement")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("replacement")
        .withProviderPreferences(List.of(new ProviderPreference("OPENAI", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
    InMemoryAgentRepository repository = new InMemoryAgentRepository(Map.of("agent-a", initialAgent));

    repository.replaceGlobalAgents(Map.of("agent-b", replacementAgent));

    assertThat(repository.findAll()).containsOnlyKeys("agent-b");
    assertThat(repository.get("agent-b").systemPrompt()).isEqualTo("replacement");
    assertThatThrownBy(() -> repository.get("agent-a"))
        .isInstanceOf(AgentNotFoundException.class);
  }
}
