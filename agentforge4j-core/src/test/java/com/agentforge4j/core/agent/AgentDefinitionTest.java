// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDefinitionTest {

  private static AgentDefinition validDefinition(
      String id,
      String name,
      AgentLocality locality,
      String systemPrompt,
      List<ProviderPreference> providerPreferences,
      List<String> supportedCommands,
      String version) {
    return AgentDefinition.builder()
        .withId(id)
        .withName(name)
        .withLocality(locality)
        .withEnabled(true)
        .withSystemPrompt(systemPrompt)
        .withProviderPreferences(providerPreferences)
        .withSupportedCommands(supportedCommands)
        .withVersion(version)
        .build();
  }

  private static AgentDefinition baseline() {
    return validDefinition(
        "agent-1",
        "Agent One",
        AgentLocality.LOCAL,
        "You are a test agent.",
        List.of(new ProviderPreference("ollama", "llama3")),
        null,
        "1.0.0");
  }

  @Test
  void null_supportedCommands_defaults_to_empty_unmodifiable_list() {
    AgentDefinition def = baseline();

    assertThat(def.supportedCommands()).isEmpty();
    assertThatThrownBy(() -> def.supportedCommands().add("X"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void copies_provider_preferences_and_supported_commands_so_callers_cannot_mutate_internal_state() {
    List<ProviderPreference> prefs = new ArrayList<>(
        List.of(new ProviderPreference("a", "m1")));
    List<String> commands = new ArrayList<>(List.of("USER_PROMPT"));

    AgentDefinition def = validDefinition(
        "x",
        "X",
        AgentLocality.CLOUD,
        "prompt",
        prefs,
        commands,
        "0.1.0");

    prefs.add(new ProviderPreference("b", null));
    commands.add("COMPLETE");

    assertThat(def.providerPreferences()).containsExactly(new ProviderPreference("a", "m1"));
    assertThat(def.supportedCommands()).containsExactly("USER_PROMPT");
    assertThatThrownBy(() -> def.providerPreferences().add(new ProviderPreference("c", null)))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> def.supportedCommands().add("COMPLETE"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "  ", "\t"})
  void rejects_blank_id(String id) {
    assertThatThrownBy(() -> validDefinition(
        id,
        "n",
        AgentLocality.LOCAL,
        "p",
        List.of(new ProviderPreference("ollama", null)),
        List.of(),
        "1.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition id must not be blank");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_name(String name) {
    assertThatThrownBy(() -> validDefinition(
        "id-1",
        name,
        AgentLocality.LOCAL,
        "p",
        List.of(new ProviderPreference("ollama", null)),
        List.of(),
        "1.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition name must not be blank for agent: id-1");
  }

  @Test
  void rejects_null_locality() {
    assertThatThrownBy(() -> validDefinition(
        "id-1",
        "Name",
        null,
        "p",
        List.of(new ProviderPreference("ollama", null)),
        List.of(),
        "1.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition locality must not be null for agent: id-1");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "  "})
  void rejects_blank_system_prompt(String systemPrompt) {
    assertThatThrownBy(() -> validDefinition(
        "id-1",
        "Name",
        AgentLocality.CLOUD,
        systemPrompt,
        List.of(new ProviderPreference("openai", "gpt-4o")),
        List.of(),
        "1.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition systemPrompt must not be blank for agent: id-1");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_version(String version) {
    assertThatThrownBy(() -> validDefinition(
        "id-1",
        "Name",
        AgentLocality.LOCAL,
        "prompt",
        List.of(new ProviderPreference("ollama", null)),
        List.of(),
        version))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition version must not be blank for agent: id-1");
  }

  @Test
  void rejects_null_provider_preferences() {
    assertThatThrownBy(() -> validDefinition(
        "id-1",
        "Name",
        AgentLocality.LOCAL,
        "prompt",
        null,
        List.of(),
        "1.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition must have at least one providerPreference for agent: id-1");
  }

  @Test
  void rejects_empty_provider_preferences() {
    assertThatThrownBy(() -> validDefinition(
        "id-1",
        "Name",
        AgentLocality.LOCAL,
        "prompt",
        List.of(),
        List.of(),
        "1.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AgentDefinition must have at least one providerPreference for agent: id-1");
  }

  @Test
  void jackson_round_trip_preserves_values() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    AgentDefinition original = AgentDefinition.builder()
        .withId("orch")
        .withName("Orchestrator")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(false)
        .withSystemPrompt("System prompt line.")
        .withProviderPreferences(List.of(
            new ProviderPreference("bedrock", "claude-3"),
            new ProviderPreference("openai", null)))
        .withSupportedCommands(List.of("USER_PROMPT", "COMPLETE"))
        .withAuthor("Team")
        .withContact("team@example.com")
        .withVersion("2.3.4")
        .build();

    String json = mapper.writeValueAsString(original);
    AgentDefinition restored = mapper.readValue(json, AgentDefinition.class);

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void jackson_omits_null_optional_string_fields_per_json_include() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    AgentDefinition def = validDefinition(
        "a",
        "A",
        AgentLocality.LOCAL,
        "p",
        List.of(new ProviderPreference("p", "m")),
        List.of(),
        "1.0.0");

    String json = mapper.writeValueAsString(def);

    assertThat(json).doesNotContain("author");
    assertThat(json).doesNotContain("contact");

    AgentDefinition restored = mapper.readValue(json, AgentDefinition.class);
    assertThat(restored.author()).isNull();
    assertThat(restored.contact()).isNull();
    assertThat(restored).isEqualTo(def);
  }
}
