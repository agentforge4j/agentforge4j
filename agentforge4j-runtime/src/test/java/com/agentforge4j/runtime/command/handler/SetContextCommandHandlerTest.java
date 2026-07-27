// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.SetContextCommand;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetContextCommandHandlerTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private final SetContextCommandHandler handler =
      new SetContextCommandHandler(new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK));

  @Test
  void restamps_llm_supplied_value_as_llm_generated_ignoring_inbound_provenance() {
    WorkflowState state = stateAtStep("s1");
    // Laundering attempt: the LLM-supplied value claims a trusted provenance. The handler must
    // re-stamp it LLM_GENERATED, never honour the inbound classification.
    SetContextCommand cmd = new SetContextCommand("k",
        new StringContextValue("evil", ContextProvenance.SYSTEM_GENERATED));

    CommandApplicationResult result = handler.apply(cmd, request(state));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    ContextValue stored = state.getContextValue("k").orElseThrow();
    assertThat(stored.provenance()).isEqualTo(ContextProvenance.LLM_GENERATED);
    assertThat(((StringContextValue) stored).value()).isEqualTo("evil");
  }

  @Test
  void stamps_default_provenance_value_as_llm_generated() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("k",
        new StringContextValue("v", ContextProvenance.USER_SUPPLIED));

    handler.apply(cmd, request(state));

    assertThat(state.getContextValue("k").orElseThrow().provenance())
        .isEqualTo(ContextProvenance.LLM_GENERATED);
  }

  @Test
  void rejects_the_reserved_envelope_key_at_the_write_path() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand(UntrustedInputEnvelope.KEY,
        new StringContextValue("x", ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() -> handler.apply(cmd, request(state)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UntrustedInputEnvelope.KEY)
        .hasMessageContaining("reserved");
    assertThat(state.getContextValue(UntrustedInputEnvelope.KEY)).isEmpty();
  }

  @Test
  void rejects_a_key_targeting_the_reserved_double_underscore_namespace() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("__ledger.requirements",
        new StringContextValue("x", ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() -> handler.apply(cmd, request(state)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("__ledger.requirements")
        .hasMessageContaining("reserved");
    assertThat(state.getContextValue("__ledger.requirements")).isEmpty();
  }

  @Test
  void rejects_reserved_retry_attempt_counter_key() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("__retry_a_attempts",
        new StringContextValue("0", ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() -> handler.apply(cmd, request(state)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("__retry_a_attempts")
        .hasMessageContaining("reserved");
    assertThat(state.getContextValue("__retry_a_attempts")).isEmpty();
  }

  @Test
  void rejects_reserved_llm_token_total_key() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("__llm_tokens_total",
        new StringContextValue("0", ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() -> handler.apply(cmd, request(state)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("__llm_tokens_total")
        .hasMessageContaining("reserved");
    assertThat(state.getContextValue("__llm_tokens_total")).isEmpty();
  }

  @Test
  void reserved_namespace_cannot_even_be_declared_in_an_output_keys_allow_list() {
    // An outputKeys allow-list naming a reserved key can no longer exist at all: ContextMapping
    // rejects it at construction, closing the "allow-list rescues a reserved key" avenue one layer
    // before the handler's own absolute guard (which remains as write-path defense, proven by the
    // rejection tests above against an empty mapping).
    assertThatThrownBy(() -> new ContextMapping(List.of(), List.of("__retry_a_attempts")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved")
        .hasMessageContaining("__retry_a_attempts");
  }

  @Test
  void normal_non_reserved_key_writes_continue_to_work() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("myKey",
        new StringContextValue("value", ContextProvenance.USER_SUPPLIED));

    CommandApplicationResult result = handler.apply(cmd, request(state));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getContextValue("myKey")).isPresent();
  }

  @Test
  void single_underscore_key_is_not_reserved_and_writes() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("_x",
        new StringContextValue("v", ContextProvenance.USER_SUPPLIED));

    CommandApplicationResult result = handler.apply(cmd, request(state));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getContextValue("_x")).isPresent();
  }

  @Test
  void interior_double_underscore_key_is_not_reserved_and_writes() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("a__b",
        new StringContextValue("v", ContextProvenance.USER_SUPPLIED));

    CommandApplicationResult result = handler.apply(cmd, request(state));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getContextValue("a__b")).isPresent();
  }

  private static WorkflowState stateAtStep(String stepId) {
    WorkflowState state =
        new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId(stepId);
    return state;
  }

  private static CommandApplicationRequest request(WorkflowState state) {
    StepDefinition step = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(new FailBehaviour("stop")).build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf-1", "W", null, null, null, "1.0.0",
        null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        List.of(), List.of());
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1, step, workflow,
        0);
  }
}
