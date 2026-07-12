// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.agentcreator;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

class AgentCreatorExampleTest {

  @Test
  void runCompletesWithApprovedLiteTierBundle() {
    AgentForge4j agentForge4j = AgentCreatorExample.assemble();

    WorkflowState finalState = AgentCreatorExample.run(agentForge4j);

    assertThat(finalState.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }
}
