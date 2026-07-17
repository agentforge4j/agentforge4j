// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.SetContextCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

    CommandApplicationResult result =
        handler.apply(cmd, new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1));

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

    handler.apply(cmd, new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1));

    assertThat(state.getContextValue("k").orElseThrow().provenance())
        .isEqualTo(ContextProvenance.LLM_GENERATED);
  }

  @Test
  void rejects_the_reserved_envelope_key_at_the_write_path() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand(UntrustedInputEnvelope.KEY,
        new StringContextValue("x", ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() ->
        handler.apply(cmd, new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UntrustedInputEnvelope.KEY)
        .hasMessageContaining("reserved");
    assertThat(state.getContextValue(UntrustedInputEnvelope.KEY)).isEmpty();
  }

  @Test
  void rejects_reserved_retry_attempt_counter_key() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("__retry_a_attempts",
        new StringContextValue("0", ContextProvenance.USER_SUPPLIED));

    assertThatThrownBy(() ->
        handler.apply(cmd, new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1)))
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

    assertThatThrownBy(() ->
        handler.apply(cmd, new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("__llm_tokens_total")
        .hasMessageContaining("reserved");
    assertThat(state.getContextValue("__llm_tokens_total")).isEmpty();
  }

  @Test
  void reserved_namespace_rejection_applies_regardless_of_output_keys_allow_list() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("__retry_a_attempts",
        new StringContextValue("0", ContextProvenance.USER_SUPPLIED));
    // An outputKeys allow-list that explicitly names the reserved key must not rescue it: the
    // reserved-namespace guard is absolute, checked independently of the allow-list.
    ContextMapping mapping = new ContextMapping(List.of(), List.of("__retry_a_attempts"));

    assertThatThrownBy(() ->
        handler.apply(cmd, new CommandApplicationRequest(state, mapping, "agent-1", 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @Test
  void normal_non_reserved_key_writes_continue_to_work() {
    WorkflowState state = stateAtStep("s1");
    SetContextCommand cmd = new SetContextCommand("myKey",
        new StringContextValue("value", ContextProvenance.USER_SUPPLIED));

    CommandApplicationResult result =
        handler.apply(cmd, new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getContextValue("myKey")).isPresent();
  }

  private static WorkflowState stateAtStep(String stepId) {
    WorkflowState state =
        new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId(stepId);
    return state;
  }
}
