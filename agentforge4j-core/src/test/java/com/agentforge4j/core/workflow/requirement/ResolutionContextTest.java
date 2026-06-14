package com.agentforge4j.core.workflow.requirement;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolutionContextTest {

  @Test
  void carriesNonEmptyContextValuesToResolver() {
    ResolutionContext context = new ResolutionContext("wf", "run-1",
        Map.of("tenant", "acme", "actor", "alice"));
    RequirementResolver resolver = (requirement, ctx) -> ctx.contextValues().get("tenant");
    WorkflowRequirement requirement = new WorkflowRequirement("r", "rbac",
        RequirementScope.WORKFLOW, null, null, true, null, ResolutionMode.RUN_START);

    assertThat(resolver.resolve(requirement, context)).isEqualTo("acme");
  }

  @Test
  void contextValuesAreDefensivelyCopiedAndImmutable() {
    Map<String, String> source = new HashMap<>();
    source.put("k", "v");
    ResolutionContext context = new ResolutionContext("wf", null, source);
    source.put("k2", "v2"); // mutating the source must not leak into the context

    assertThat(context.contextValues()).containsExactly(Map.entry("k", "v"));
    assertThatThrownBy(() -> context.contextValues().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullContextValuesBecomeEmpty() {
    ResolutionContext context = new ResolutionContext("wf", "run-1", null);

    assertThat(context.contextValues()).isEmpty();
  }

  @Test
  void blankWorkflowId_isRejected() {
    assertThatThrownBy(() -> new ResolutionContext(" ", "run-1", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workflowId");
  }
}
