// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
