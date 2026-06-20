// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.behaviour;

import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.verification.support.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Black-box coverage of the nested-workflow cycle guard: a workflow whose {@code WORKFLOW} behaviour
 * references itself is rejected at load time with a circular-reference error (surfaced as the typed
 * cause of the build wrapper). The {@code maxNestingDepth} runtime guard is held — see the
 * verification {@code CHANGES.md}.
 */
class NestedWorkflowGuardTest {

  @Test
  void cyclicWorkflowReferenceIsRejected() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withWorkflowsDir(Fixtures.dir("/fixtures/nesting/workflows"))
        .withLoadShippedWorkflows(false)
        .build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("circular references");
  }
}
