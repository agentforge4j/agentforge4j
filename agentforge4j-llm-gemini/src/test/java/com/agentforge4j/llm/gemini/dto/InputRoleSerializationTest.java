// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputRoleSerializationTest {

  @Test
  void should_serialize_user_role_as_wire_string() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    assertThat(mapper.writeValueAsString(InputRole.USER)).isEqualTo("\"user\"");
  }
}
