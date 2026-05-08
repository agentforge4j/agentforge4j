package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent JSON stays author-friendly: no {@code responseSchema} required; stray key is ignored.
 */
class AgentDefinitionFileResponseSchemaTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void agent_json_without_response_schema_loads() throws Exception {
    AgentDefinitionFile file = mapper.readValue("""
        {
          "id": "plain",
          "name": "Plain",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
          "supportedCommands": ["COMPLETE", "USER_PROMPT"]
        }
        """, AgentDefinitionFile.class);

    AgentDefinition def = file.toDefinition("system prompt");

    assertThat(def.supportedCommands()).containsExactly("COMPLETE", "USER_PROMPT");
  }

  @Test
  void unexpected_response_schema_key_tolerated_and_does_not_affect_supported_commands() throws Exception {
    AgentDefinitionFile withExtra = mapper.readValue("""
        {
          "id": "x",
          "name": "X",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
          "supportedCommands": ["COMPLETE"],
          "responseSchema": { "bogus": ["CREATE_FILE", "EVERYTHING"] }
        }
        """, AgentDefinitionFile.class);

    AgentDefinitionFile baseline = mapper.readValue("""
        {
          "id": "x",
          "name": "X",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
          "supportedCommands": ["COMPLETE"]
        }
        """, AgentDefinitionFile.class);

    AgentDefinition defWith = withExtra.toDefinition("sys");
    AgentDefinition defBase = baseline.toDefinition("sys");

    assertThat(defWith.supportedCommands()).isEqualTo(defBase.supportedCommands());

    CommandResponseSchema schemaWith = CommandSchemaFactory.build(defWith.supportedCommands(), mapper);
    CommandResponseSchema schemaBase = CommandSchemaFactory.build(defBase.supportedCommands(), mapper);

    assertThat(schemaWith.cacheKey()).isEqualTo(schemaBase.cacheKey());
    assertThat(schemaWith.supportedCommandTypes()).containsExactly("COMPLETE");
    assertThat(schemaWith.supportedCommandTypes()).doesNotContain("CREATE_FILE");
  }

  @Test
  void response_schema_property_not_required_when_present() throws Exception {
    AgentDefinitionFile file = mapper.readValue("""
        {
          "id": "x",
          "name": "X",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
          "supportedCommands": ["COMPLETE"],
          "responseSchema": { "should": "be ignored" }
        }
        """, AgentDefinitionFile.class);

    AgentDefinition def = file.toDefinition("sys");

    assertThat(def.id()).isEqualTo("x");
    assertThat(def.supportedCommands()).containsExactly("COMPLETE");
  }
}
