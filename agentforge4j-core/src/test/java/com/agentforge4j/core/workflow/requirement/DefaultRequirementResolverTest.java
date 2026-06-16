// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.requirement;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRequirementResolverTest {

  private final DefaultRequirementResolver resolver = new DefaultRequirementResolver();

  private static final ResolutionContext CONTEXT = new ResolutionContext("wf", "run-1", Map.of());

  @Test
  void returnsDeclaredDefault() {
    WorkflowRequirement requirement = new WorkflowRequirement("r", "rbac",
        RequirementScope.WORKFLOW, null, null, true, "{\"mode\":\"all\"}", ResolutionMode.INSTALL);

    assertThat(resolver.resolve(requirement, CONTEXT)).isEqualTo("{\"mode\":\"all\"}");
  }

  @Test
  void returnsNullWhenNoDefault() {
    WorkflowRequirement requirement = new WorkflowRequirement("r", "rbac",
        RequirementScope.WORKFLOW, null, null, true, null, ResolutionMode.RUN_START);

    assertThat(resolver.resolve(requirement, CONTEXT)).isNull();
  }
}
