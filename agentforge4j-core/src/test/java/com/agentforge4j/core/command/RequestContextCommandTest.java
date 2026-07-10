// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextCommandTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static ContextSelector selector() {
    return new ContextSelector(ContextSourceKind.ARTIFACT, "design.md", ContextVariant.FULL);
  }

  @Test
  void rejectsEmptySelectors() {
    assertThatThrownBy(() -> new RequestContextCommand(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deserializesFromRequestContextType() throws Exception {
    RequestContextCommand original = new RequestContextCommand(List.of(selector()));
    String json = mapper.writeValueAsString((LlmCommand) original);

    assertThat(json).contains("REQUEST_CONTEXT");
    LlmCommand parsed = mapper.readValue(json, LlmCommand.class);
    assertThat(parsed).isInstanceOf(RequestContextCommand.class);
    assertThat(((RequestContextCommand) parsed).requestedSelectors()).hasSize(1);
  }
}
