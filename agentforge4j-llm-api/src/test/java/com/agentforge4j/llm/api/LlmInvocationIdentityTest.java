// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmInvocationIdentityTest {

  @Test
  void preserves_component_order() {
    LlmInvocationIdentity identity = new LlmInvocationIdentity("recruitment", "run-1", "screen-cv", "screener");

    assertThat(identity.workflowId()).isEqualTo("recruitment");
    assertThat(identity.runId()).isEqualTo("run-1");
    assertThat(identity.stepId()).isEqualTo("screen-cv");
    assertThat(identity.agentId()).isEqualTo("screener");
  }

  @Test
  void allows_all_null_components() {
    LlmInvocationIdentity identity = new LlmInvocationIdentity(null, null, null, null);

    assertThat(identity.workflowId()).isNull();
    assertThat(identity.runId()).isNull();
    assertThat(identity.stepId()).isNull();
    assertThat(identity.agentId()).isNull();
  }
}
