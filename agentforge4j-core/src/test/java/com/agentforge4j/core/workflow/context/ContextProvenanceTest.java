// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextProvenanceTest {

  @Test
  void system_generated_and_llm_generated_are_trusted() {
    assertThat(ContextProvenance.SYSTEM_GENERATED.isTrusted()).isTrue();
    assertThat(ContextProvenance.LLM_GENERATED.isTrusted()).isTrue();
  }

  @Test
  void user_supplied_and_external_tool_are_untrusted() {
    assertThat(ContextProvenance.USER_SUPPLIED.isTrusted()).isFalse();
    assertThat(ContextProvenance.EXTERNAL_TOOL.isTrusted()).isFalse();
  }
}
