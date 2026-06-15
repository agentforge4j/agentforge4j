package com.agentforge4j.runtime.execution.behaviour;

import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.spar.SparConfig;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandApplier;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.handler.SparBehaviourHandler;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_MODEL;
import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SparBehaviourHandlerTest {

  private static final String PRIMARY = "architect";
  private static final String CHALLENGER = "developer";
  private static final String GOOD_REASON =
      "The design omits rollback semantics for the payment adapter; this is a concrete implementation risk.";
  private static final String STEP_PROMPT = "Review the blueprint.";

  @Mock
  private AgentInvoker agentInvoker;
  @Mock
  private CommandApplier commandApplier;
  @Mock
  private EventRecorder eventRecorder;

  private WorkflowState state;
  private StepDefinition step;
  private SparBehaviour behaviour;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    SparConfig config = new SparConfig(CHALLENGER, 5, "Resolve the debate.");
    behaviour = new SparBehaviour(PRIMARY, config, StepTransition.AUTO, null);
    step = StepDefinition.builder()
        .withStepId("spar-step")
        .withName("Spar step")
        .withBehaviour(behaviour)
        .withContextMapping(ContextMapping.none())
        .withStepPrompt(STEP_PROMPT)
        .build();
    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId("spar-step");
    state.putStepExecutionUid("spar-step", 7);
  }

  @Test
  void stops_after_round_one_when_neither_requests_valid_continuation() {
    when(agentInvoker.invoke(eq(PRIMARY), any(), any(), anyString(), any(), any()))
        .thenReturn(bareContinueResult())
        .thenReturn(bareContinueResult());
    when(agentInvoker.invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any())).thenReturn(bareContinueResult());
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt())).thenReturn(
        CommandApplicationResult.CONTINUE);

    runHandler();

    verify(agentInvoker, times(2)).invoke(eq(PRIMARY), any(), any(), anyString(), any(), any());
    verify(agentInvoker, times(1)).invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any());
    assertThat(state.getContext()).containsKeys("spar.primary.round.1", "spar.challenger.round.1");
    assertThat(state.getContext()).doesNotContainKey("spar.primary.round.2");
    verify(commandApplier, times(1)).apply(any(), any(), any(), anyString(), anyInt());
  }

  @Test
  void continues_when_one_side_requests_with_valid_reason() {
    when(agentInvoker.invoke(eq(PRIMARY), any(), any(), anyString(), any(), any()))
        .thenReturn(invokeResult(false, null))
        .thenReturn(invokeResult(false, null))
        .thenReturn(invokeResult(false, null));
    when(agentInvoker.invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any()))
        .thenReturn(invokeResult(true, GOOD_REASON))
        .thenReturn(invokeResult(false, null));
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    runHandler();

    verify(agentInvoker, times(3)).invoke(eq(PRIMARY), any(), any(), anyString(), any(), any());
    verify(agentInvoker, times(2)).invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any());
    assertThat(state.getContext()).containsKeys("spar.primary.round.1", "spar.challenger.round.2");
  }

  @Test
  void does_not_continue_on_vague_reason() {
    when(agentInvoker.invoke(eq(PRIMARY), any(), any(), anyString(), any(), any()))
        .thenReturn(invokeResult(true, "I disagree with the proposal as written"))
        .thenReturn(invokeResult(false, null));
    when(agentInvoker.invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any()))
        .thenReturn(invokeResult(true, "This needs more discussion before we can finalize"))
        .thenReturn(invokeResult(false, null));
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    runHandler();

    verify(agentInvoker, times(2)).invoke(eq(PRIMARY), any(), any(), anyString(), any(), any());
    verify(agentInvoker, times(1)).invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any());
  }

  @Test
  void runs_all_max_rounds_when_valid_concerns_persist() {
    SparConfig config = new SparConfig(CHALLENGER, 2, "Resolve the debate.");
    behaviour = new SparBehaviour(PRIMARY, config, StepTransition.AUTO, null);
    step = StepDefinition.builder()
        .withStepId("spar-step")
        .withName("Spar step")
        .withBehaviour(behaviour)
        .withContextMapping(ContextMapping.none())
        .withStepPrompt(STEP_PROMPT)
        .build();

    when(agentInvoker.invoke(eq(PRIMARY), any(), any(), anyString(), any(), any()))
        .thenReturn(invokeResult(true, GOOD_REASON))
        .thenReturn(invokeResult(true, GOOD_REASON))
        .thenReturn(invokeResult(false, null));
    when(agentInvoker.invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any()))
        .thenReturn(invokeResult(true, GOOD_REASON))
        .thenReturn(invokeResult(true, GOOD_REASON));
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    runHandler();

    verify(agentInvoker, times(3)).invoke(eq(PRIMARY), any(), any(), anyString(), any(), any());
    verify(agentInvoker, times(2)).invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any());
    assertThat(state.getContext()).containsKeys(
        "spar.primary.round.1", "spar.challenger.round.1",
        "spar.primary.round.2", "spar.challenger.round.2");
  }

  @Test
  void exchange_invocations_widen_input_keys_per_round_then_resolution_matches_executed_rounds() {
    ContextMapping base = new ContextMapping(List.of("ctx"), List.of());
    step = StepDefinition.builder()
        .withStepId("spar-step")
        .withName("Spar step")
        .withBehaviour(behaviour)
        .withContextMapping(base)
        .withStepPrompt(STEP_PROMPT)
        .build();

    when(agentInvoker.invoke(anyString(), any(), same(state), anyString(), any(), any()))
        .thenReturn(invokeResult(false, null))
        .thenReturn(invokeResult(true, GOOD_REASON))
        .thenReturn(invokeResult(false, null))
        .thenReturn(invokeResult(false, null))
        .thenReturn(bareContinueResult());
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    ArgumentCaptor<ContextMapping> cap = ArgumentCaptor.forClass(ContextMapping.class);
    runHandler();

    verify(agentInvoker, times(5)).invoke(anyString(), cap.capture(), same(state), anyString(), any(), any());
    List<ContextMapping> maps = cap.getAllValues();
    // round 1: primary + challenger — same widened instance per round, never the step's mapping ref
    assertThat(maps.get(0)).isNotSameAs(base);
    assertThat(maps.get(0).inputKeys()).isEqualTo(base.inputKeys());
    assertThat(maps.get(0)).isSameAs(maps.get(1));
    // round 2: primary + challenger — prior round outputs visible
    assertThat(maps.get(2).inputKeys()).containsExactly(
        "ctx",
        SparBehaviourHandler.SPAR_PRIMARY_PREFIX + "1",
        SparBehaviourHandler.SPAR_CHALLENGER_PREFIX + "1");
    assertThat(maps.get(2)).isSameAs(maps.get(3));
    // resolution: executed rounds + prompt
    assertThat(maps.get(4).inputKeys())
        .containsExactly(
            "ctx",
            SparBehaviourHandler.SPAR_PRIMARY_PREFIX + "1",
            SparBehaviourHandler.SPAR_CHALLENGER_PREFIX + "1",
            SparBehaviourHandler.SPAR_PRIMARY_PREFIX + "2",
            SparBehaviourHandler.SPAR_CHALLENGER_PREFIX + "2",
            "spar.resolution.prompt");
  }

  @Test
  void early_stop_after_round_one_never_includes_round_two_spar_keys_in_any_mapping() {
    when(agentInvoker.invoke(anyString(), any(), same(state), anyString(), any(), any()))
        .thenReturn(bareContinueResult())
        .thenReturn(bareContinueResult())
        .thenReturn(bareContinueResult());
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    ArgumentCaptor<ContextMapping> cap = ArgumentCaptor.forClass(ContextMapping.class);
    runHandler();

    verify(agentInvoker, times(3)).invoke(anyString(), cap.capture(), same(state), anyString(), any(), any());
    assertThat(cap.getAllValues()).allSatisfy(m -> assertThat(m.inputKeys())
        .noneMatch(k -> k.endsWith("round.2")));
  }

  @Test
  void resolution_invocation_widens_context_only_for_executed_rounds() {
    when(agentInvoker.invoke(eq(PRIMARY), any(), any(), anyString(), any(), any()))
        .thenReturn(bareContinueResult())
        .thenReturn(bareContinueResult());
    when(agentInvoker.invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any()))
        .thenReturn(bareContinueResult());
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    ArgumentCaptor<ContextMapping> mappingCaptor = ArgumentCaptor.forClass(ContextMapping.class);
    runHandler();

    verify(agentInvoker, times(2)).invoke(eq(PRIMARY), mappingCaptor.capture(), any(), anyString(), any(), any());
    assertThat(mappingCaptor.getAllValues().get(0).inputKeys()).doesNotContain(
        "spar.primary.round.1");
    ContextMapping resolutionMapping = mappingCaptor.getAllValues().get(1);
    assertThat(resolutionMapping.inputKeys())
        .contains("spar.primary.round.1", "spar.challenger.round.1", "spar.resolution.prompt")
        .doesNotContain("spar.primary.round.2", "spar.challenger.round.2");
  }

  @Test
  void spar_round_prompts_include_continuation_instructions_resolution_does_not() {
    when(agentInvoker.invoke(eq(PRIMARY), any(), any(), anyString(), any(), any()))
        .thenReturn(bareContinueResult())
        .thenReturn(bareContinueResult());
    when(agentInvoker.invoke(eq(CHALLENGER), any(), any(), anyString(), any(), any()))
        .thenReturn(bareContinueResult());
    when(commandApplier.apply(any(), any(), any(), anyString(), anyInt()))
        .thenReturn(CommandApplicationResult.CONTINUE);

    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    runHandler();

    verify(agentInvoker, times(2)).invoke(eq(PRIMARY), any(), any(), promptCaptor.capture(), any(), any());
    List<String> primaryPrompts = promptCaptor.getAllValues();
    assertThat(primaryPrompts.get(0)).contains("SPAR round output");
    assertThat(primaryPrompts.get(1)).isEqualTo(STEP_PROMPT);
  }

  private void runHandler() {
    SparBehaviourHandler handler = new SparBehaviourHandler(agentInvoker, commandApplier,
        eventRecorder);
    WorkflowDefinition root = mock(WorkflowDefinition.class);
    ExecutionContext ctx = new ExecutionContext(state, root, 8);
    ExecutionOutcome outcome = handler.handle(step, behaviour, ctx);
    assertThat(outcome).isNotNull();
  }

  private static AgentInvocationResult bareContinueResult() {
    return AgentInvocationResult.builder()
        .withRawResponse("[{\"type\":\"CONTINUE\"}]")
        .withCommands(List.of(new ContinueCommand(null, null, null)))
        .withModelUsed(TEST_MODEL)
        .withTokenUsage(TEST_TOKEN_USAGE)
        .build();
  }

  private static AgentInvocationResult invokeResult(boolean wantsAnotherRound, String reason) {
    String json;
    if (reason == null) {
      json = "[{\"type\":\"CONTINUE\",\"wantsAnotherRound\":" + wantsAnotherRound + "}]";
    } else {
      json = "[{\"type\":\"CONTINUE\",\"wantsAnotherRound\":"
          + wantsAnotherRound
          + ",\"reason\":\""
          + reason.replace("\\", "\\\\").replace("\"", "\\\"")
          + "\"}]";
    }
    List<LlmCommand> cmds = List.of(new ContinueCommand(wantsAnotherRound, reason, null));
    return AgentInvocationResult.builder()
        .withRawResponse(json)
        .withCommands(cmds)
        .withModelUsed(TEST_MODEL)
        .withTokenUsage(TEST_TOKEN_USAGE)
        .build();
  }
}
