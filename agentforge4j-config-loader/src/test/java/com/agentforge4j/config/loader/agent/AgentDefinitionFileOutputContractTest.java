// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.OutputDiscipline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An {@code outputContract} declared in agent JSON must reach the loaded {@link AgentDefinition},
 * not be silently dropped by the DTO adapter between schema-validated JSON and the domain object.
 */
class AgentDefinitionFileOutputContractTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void outputContract_survives_the_load_path() throws Exception {
    AgentDefinitionFile file = mapper.readValue("""
        {
          "id": "structured",
          "name": "Structured",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
          "supportedCommands": ["COMPLETE"],
          "outputContract": {
            "schemaRef": "structured-response.schema.json",
            "discipline": "STRUCTURED_ONLY",
            "rationaleAllowed": true
          }
        }
        """, AgentDefinitionFile.class);

    AgentDefinition def = file.toDefinition("sys");

    assertThat(def.outputContract()).isNotNull();
    assertThat(def.outputContract().schemaRef()).isEqualTo("structured-response.schema.json");
    assertThat(def.outputContract().discipline()).isEqualTo(OutputDiscipline.STRUCTURED_ONLY);
    assertThat(def.outputContract().rationaleAllowed()).isTrue();
  }

  @Test
  void agent_without_outputContract_still_loads_with_a_null_contract() throws Exception {
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
          "supportedCommands": ["COMPLETE"]
        }
        """, AgentDefinitionFile.class);

    AgentDefinition def = file.toDefinition("sys");

    assertThat(def.outputContract()).isNull();
  }
}
