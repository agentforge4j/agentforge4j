// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serialization tests for the canonical {@link InputRole}, shared by every
 * {@code agentforge4j-llm-*} provider module whose wire protocol follows this OpenAI-style shape
 * (previously eight near-identical per-provider copies).
 */
class InputRoleTest {

  @Test
  void serializes_each_role_as_its_lowercase_wire_string() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    assertThat(mapper.writeValueAsString(InputRole.SYSTEM)).isEqualTo("\"system\"");
    assertThat(mapper.writeValueAsString(InputRole.USER)).isEqualTo("\"user\"");
    assertThat(mapper.writeValueAsString(InputRole.ASSISTANT)).isEqualTo("\"assistant\"");
    assertThat(mapper.writeValueAsString(InputRole.TOOL)).isEqualTo("\"tool\"");
  }

  @Test
  void deserializes_each_role_from_its_wire_string() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    // The enum sits on response-side DTOs too, so reading is the load-bearing direction: a
    // provider reporting "assistant" must map, not throw.
    assertThat(mapper.readValue("\"system\"", InputRole.class)).isEqualTo(InputRole.SYSTEM);
    assertThat(mapper.readValue("\"user\"", InputRole.class)).isEqualTo(InputRole.USER);
    assertThat(mapper.readValue("\"assistant\"", InputRole.class)).isEqualTo(InputRole.ASSISTANT);
    assertThat(mapper.readValue("\"tool\"", InputRole.class)).isEqualTo(InputRole.TOOL);
  }

  @Test
  void round_trips_every_constant() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    for (InputRole role : InputRole.values()) {
      assertThat(mapper.readValue(mapper.writeValueAsString(role), InputRole.class))
          .isEqualTo(role);
    }
  }

  @Test
  void rejects_a_role_the_wire_protocol_does_not_define() {
    ObjectMapper mapper = new ObjectMapper();

    // Documents the current boundary rather than endorsing it: an unmodelled role is a hard
    // failure, not a silent null. Gemini's "model" role is the known case and is handled by that
    // provider never deserializing a role it did not send.
    assertThatThrownBy(() -> mapper.readValue("\"developer\"", InputRole.class))
        .isInstanceOf(InvalidFormatException.class);
  }
}
