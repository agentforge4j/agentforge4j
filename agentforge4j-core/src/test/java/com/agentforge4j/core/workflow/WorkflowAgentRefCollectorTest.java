package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.core.workflow.step.spar.SparConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowAgentRefCollectorTest {

  private static WorkflowDefinition workflow(
      String id,
      List<Executable> steps,
      Map<String, BlueprintDefinition> blueprints) {
    return new WorkflowDefinition(
        id,
        "W",
        null,
        null,
        null,
        "1.0.0",
        null,
        null,
        null,
        Map.of(),
        blueprints,
        steps);
  }

  @Test
  void rejects_null_root() {
    assertThatThrownBy(() -> WorkflowAgentRefCollector.collect(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("root");
  }

  @Test
  void collects_agent_behaviour_ref() {
    var step = new StepDefinition(
        "s1",
        "N",
        new AgentBehaviour("agent-a", StepTransition.AUTO, null),
        null,
        null,
        null);
    var wf = workflow("root", List.of(step), Map.of());

    assertThat(WorkflowAgentRefCollector.collect(wf))
        .containsExactly(new WorkflowAgentRefCollector.AgentRefSite("agent-a", "root", "s1"));
  }

  @Test
  void collects_both_agents_for_spar_behaviour() {
    SparConfig cfg = new SparConfig("challenger", 2, "Resolve the debate.");
    var step = new StepDefinition(
        "s1",
        "N",
        new SparBehaviour("primary", cfg, StepTransition.AUTO, null),
        null,
        null,
        null);
    var wf = workflow("root", List.of(step), Map.of());

    assertThat(WorkflowAgentRefCollector.collect(wf))
        .containsExactlyInAnyOrder(
            new WorkflowAgentRefCollector.AgentRefSite("primary", "root", "s1"),
            new WorkflowAgentRefCollector.AgentRefSite("challenger", "root", "s1"));
  }

  @Test
  void follows_blueprint_ref_and_collects_inner_agent() {
    var innerStep = new StepDefinition(
        "in",
        "I",
        new AgentBehaviour("inner-agent", StepTransition.AUTO, null),
        null,
        null,
        null);
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL,
        null,
        null,
        1,
        MaxIterationsAction.FAIL);
    BlueprintDefinition bp = new BlueprintDefinition(
        "bp1",
        "BP",
        new BlueprintBehaviour(loop, StepTransition.AUTO),
        List.of(innerStep));
    var wf = workflow("root", List.of(new BlueprintRef("bp1")), Map.of("bp1", bp));

    assertThat(WorkflowAgentRefCollector.collect(wf))
        .containsExactly(new WorkflowAgentRefCollector.AgentRefSite("inner-agent", "root", "in"));
  }

  @Test
  void unknown_blueprint_ref_fails_fast() {
    var wf = workflow("root", List.of(new BlueprintRef("missing")), Map.of());

    assertThatThrownBy(() -> WorkflowAgentRefCollector.collect(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown blueprint")
        .hasMessageContaining("missing");
  }

  @Test
  void nested_workflow_uses_inner_workflow_id_in_sites() {
    var innerAgent = new StepDefinition(
        "inner",
        "I",
        new AgentBehaviour("nested-agent", StepTransition.AUTO, null),
        null,
        null,
        null);
    WorkflowDefinition nested = new WorkflowDefinition(
        "nested-wf",
        "Nested",
        null,
        null,
        null,
        "1.0.0",
        null,
        null,
        null,
        Map.of(),
        Map.of(),
        List.of(innerAgent));
    var outer = workflow("outer", List.of(nested), Map.of());

    assertThat(WorkflowAgentRefCollector.collect(outer))
        .containsExactly(
            new WorkflowAgentRefCollector.AgentRefSite("nested-agent", "nested-wf", "inner"));
  }

  @Test
  void collects_agent_inside_named_branch_without_visiting_default() {
    var branchStep = new StepDefinition(
        "inner",
        "I",
        new AgentBehaviour("leaf", StepTransition.AUTO, null),
        null,
        null,
        null);
    var branch = new BranchBehaviour(
        "routeKey",
        Map.of("path-a", branchStep),
        new StepDefinition("def", "D", new FailBehaviour("x"), null, null, null));
    var step = new StepDefinition("b1", "Branch step", branch, null, null, null);
    var wf = workflow("root", List.of(step), Map.of());

    assertThat(WorkflowAgentRefCollector.collect(wf))
        .containsExactly(new WorkflowAgentRefCollector.AgentRefSite("leaf", "root", "inner"));
  }

  @Test
  void collects_agent_on_branch_default_path() {
    var defaultStep = new StepDefinition(
        "def",
        "D",
        new AgentBehaviour("def-agent", StepTransition.AUTO, null),
        null,
        null,
        null);
    var branch = new BranchBehaviour(
        "routeKey",
        Map.of("x", new StepDefinition("x", "X", new FailBehaviour("r"), null, null, null)),
        defaultStep);
    var step = new StepDefinition("b1", "Branch step", branch, null, null, null);
    var wf = workflow("root", List.of(step), Map.of());

    assertThat(WorkflowAgentRefCollector.collect(wf))
        .containsExactly(new WorkflowAgentRefCollector.AgentRefSite("def-agent", "root", "def"));
  }

  @Test
  void inline_blueprint_definition_uses_enclosing_workflow_scope() {
    var inner = new StepDefinition(
        "in",
        "I",
        new AgentBehaviour("a1", StepTransition.AUTO, null),
        null,
        null,
        null);
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 1, null);
    BlueprintDefinition bp = new BlueprintDefinition(
        "bid",
        "BN",
        new BlueprintBehaviour(loop, StepTransition.AUTO),
        List.of(inner));
    var wf = workflow("root", List.of(bp), Map.of());

    assertThat(WorkflowAgentRefCollector.collect(wf))
        .containsExactly(new WorkflowAgentRefCollector.AgentRefSite("a1", "root", "in"));
  }
}
