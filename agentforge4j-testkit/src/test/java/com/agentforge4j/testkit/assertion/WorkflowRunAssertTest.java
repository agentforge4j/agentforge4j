// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.assertion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.testkit.capture.CaptureBundle;
import com.agentforge4j.testkit.capture.CapturedFile;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRunAssertTest {

  private final List<WorkflowEvent> events = new ArrayList<>();
  private final List<CapturedFile> files = new ArrayList<>();
  private final WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
  private int eventSeq;

  private void event(WorkflowEventType type, String stepId, String payload) {
    eventSeq++;
    events.add(new WorkflowEvent(
        "e" + eventSeq, "run-1", stepId, type, payload, "runtime", Instant.EPOCH));
  }

  private WorkflowRunAssert assertRun() {
    return WorkflowRunAssert.assertThat(
        new WorkflowRunResult("run-1", state, new CaptureBundle(events, files)));
  }

  @Test
  void statusVerbs() {
    state.setStatus(WorkflowStatus.COMPLETED);

    assertThatCode(() -> assertRun().isCompleted().hasStatus(WorkflowStatus.COMPLETED))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().isFailed()).isInstanceOf(AssertionError.class);
  }

  @Test
  void failureKindVerbs() {
    state.setStatus(WorkflowStatus.FAILED);
    state.setRunFailure(new RunFailure.ExceptionFailure("boom", "step-a", "support-1"));

    assertThatCode(() -> assertRun().isFailed().failedWith(FailureKind.EXCEPTION))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().failedWith(FailureKind.STEP_REJECTION))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void failedWithRejection() {
    state.setStatus(WorkflowStatus.FAILED);
    state.setRunFailure(new RunFailure.StepRejectionFailure("nope", "step-a", "support-1"));

    assertThatCode(() -> assertRun().failedWith(FailureKind.STEP_REJECTION))
        .doesNotThrowAnyException();
  }

  @Test
  void failedWithNoFailureThrows() {
    assertThatThrownBy(() -> assertRun().failedWith(FailureKind.EXCEPTION))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void stepVerbs() {
    event(WorkflowEventType.STEP_STARTED, "step-a", null);
    event(WorkflowEventType.STEP_STARTED, "step-b", null);
    event(WorkflowEventType.STEP_STARTED, "step-a", null);

    assertThatCode(() -> assertRun()
        .visitedStep("step-a")
        .stepVisitCount("step-a", 2)
        .didNotVisitStep("step-c")
        .stepsInOrderedSubsequence("step-a", "step-b"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().visitedStep("step-c"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().stepsInOrderedSubsequence("step-b", "step-c"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void contextVerbs() {
    state.putContextValue("name", new StringContextValue("Ada", ContextProvenance.USER_SUPPLIED));
    state.putContextValue("doc", new JsonContextValue("{\"field\":1}", ContextProvenance.USER_SUPPLIED));

    assertThatCode(() -> assertRun()
        .contextHas("name")
        .contextMissing("absent")
        .contextNonEmpty("name")
        .contextEquals("name", "Ada")
        .contextMatchesRegex("name", "A.*")
        .contextHasField("doc", "field"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().contextEquals("name", "Grace"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextHasField("doc", "missing"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextHas("absent"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void outputsHaveNoForbiddenTermsPassesWhenCleanAndFailsOnContextHit() {
    state.putContextValue("report",
        new StringContextValue("execution shape only", ContextProvenance.SYSTEM_GENERATED));

    assertThatCode(() -> assertRun().outputsHaveNoForbiddenTerms(List.of("$", "billing")))
        .doesNotThrowAnyException();

    state.putContextValue("leak",
        new StringContextValue("this mentions Billing", ContextProvenance.SYSTEM_GENERATED));
    assertThatThrownBy(() -> assertRun().outputsHaveNoForbiddenTerms(List.of("billing")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("billing");
  }

  @Test
  void outputsHaveNoForbiddenTermsScansCapturedFiles() {
    files.add(new CapturedFile("run-1", "s1", "/out/report.json", "{\"note\":\"costs $5\"}"));

    assertThatThrownBy(() -> assertRun().outputsHaveNoForbiddenTerms(List.of("$")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("report.json");
  }

  @Test
  void outputsHaveNoForbiddenTermsScansStepOutputs() {
    state.putStepOutput("step-a", "raw model response mentions billing");

    assertThatThrownBy(() -> assertRun().outputsHaveNoForbiddenTerms(List.of("billing")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("stepOutput['step-a']")
        .hasMessageContaining("billing");
  }

  @Test
  void artifactVerbs() {
    files.add(new CapturedFile("run-1", "step-a", "out/result.txt", "hello"));

    assertThatCode(() -> assertRun()
        .createdFile("out/result.txt")
        .artifactPresent("out/result.txt")
        .artifactAbsent("out/other.txt"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().createdFile("out/missing.txt"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void eventVerbs() {
    event(WorkflowEventType.RUN_STARTED, null, null);
    event(WorkflowEventType.STEP_STARTED, "step-a", null);
    event(WorkflowEventType.RUN_COMPLETED, null, null);

    assertThatCode(() -> assertRun()
        .emittedEvent(WorkflowEventType.RUN_COMPLETED)
        .didNotEmitEvent(WorkflowEventType.RUN_FAILED)
        .eventCount(WorkflowEventType.STEP_STARTED, 1)
        .eventsInOrder(WorkflowEventType.RUN_STARTED, WorkflowEventType.RUN_COMPLETED))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().emittedEvent(WorkflowEventType.RUN_FAILED))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun()
        .eventsInOrder(WorkflowEventType.RUN_COMPLETED, WorkflowEventType.RUN_STARTED))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void pendingApprovalAndInputVerbs() {
    state.setStatus(WorkflowStatus.AWAITING_STEP_APPROVAL);
    event(WorkflowEventType.STEP_AWAITING_APPROVAL, "gate", null);
    event(WorkflowEventType.STEP_APPROVED, "gate", "looks good");
    event(WorkflowEventType.AWAITING_INPUT, "ask", "need name");

    assertThatCode(() -> assertRun()
        .reachedPendingState(WorkflowStatus.AWAITING_STEP_APPROVAL)
        .approvalRequested("gate")
        .approvalDecision("gate", ApprovalOutcome.APPROVED)
        .inputRequested("ask"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().approvalDecision("gate", ApprovalOutcome.REJECTED))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void approvalDecisionRequiresNonEmptyReason() {
    event(WorkflowEventType.STEP_REJECTED, "gate", "  ");

    assertThatThrownBy(() -> assertRun().approvalDecision("gate", ApprovalOutcome.REJECTED))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void iterationVerbs() {
    event(WorkflowEventType.LOOP_ITERATION_STARTED, "loop", "iteration=1");
    event(WorkflowEventType.LOOP_ITERATION_STARTED, "loop", "iteration=2");

    assertThatCode(() -> assertRun().loopIterations(2).forEachIterations(2))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().loopIterations(1)).isInstanceOf(AssertionError.class);
  }

  @Test
  void toolAndProviderVerbs() {
    event(WorkflowEventType.TOOL_INVOCATION_REQUESTED, "step-a", "{\"capability\":\"http:get\"}");
    event(WorkflowEventType.LLM_CALL_COMPLETED, "step-a", "{\"requestedModelTier\":\"STANDARD\"}");

    assertThatCode(() -> assertRun()
        .invokedTool("http:get")
        .didNotInvokeTool("http:post")
        .toolCallCount(1)
        .providerCallCount(1)
        .providerCallTier(ModelTier.STANDARD))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().invokedTool("http:post"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().didNotInvokeTool("http:get"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().providerCallTier(ModelTier.POWERFUL))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void tokenTotalsVerb() {
    state.putContextValue(ReservedContextKeys.LLM_TOKENS_TOTAL, new NumberContextValue(42, ContextProvenance.USER_SUPPLIED));

    assertThatCode(() -> assertRun().tokenTotals(42)).doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().tokenTotals(7)).isInstanceOf(AssertionError.class);
  }

  @Test
  void failedBecauseMatchesFailureReason() {
    state.setStatus(WorkflowStatus.FAILED);
    state.setRunFailure(new RunFailure.ExceptionFailure("path escape blocked", "step-a", "sup-1"));

    assertThatCode(() -> assertRun().failedBecause("path escape")).doesNotThrowAnyException();
    assertThatThrownBy(() -> assertRun().failedBecause("no such fragment"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void failedBecauseRequiresAFailure() {
    state.setStatus(WorkflowStatus.FAILED);

    assertThatThrownBy(() -> assertRun().failedBecause("anything"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void asStringRendersEveryContextValueType() {
    state.putContextValue("num", new NumberContextValue(7, ContextProvenance.USER_SUPPLIED));
    state.putContextValue("flag", new BooleanContextValue(true, ContextProvenance.USER_SUPPLIED));
    state.putContextValue("doc", new JsonContextValue("{\"field\":1}", ContextProvenance.USER_SUPPLIED));
    state.putContextValue("items",
        new ContextValueList(List.of(
            new StringContextValue("a", ContextProvenance.USER_SUPPLIED),
            new StringContextValue("b", ContextProvenance.USER_SUPPLIED)),
            ContextProvenance.USER_SUPPLIED));

    assertThatCode(() -> assertRun()
        .contextNonEmpty("num")
        .contextNonEmpty("flag")
        .contextNonEmpty("doc")
        .contextNonEmpty("items"))
        .doesNotThrowAnyException();
  }

  @Test
  void negativeAssertionsThrow() {
    state.setStatus(WorkflowStatus.COMPLETED);
    state.putContextValue("present",
        new StringContextValue("value", ContextProvenance.USER_SUPPLIED));
    state.putContextValue("name", new StringContextValue("Ada", ContextProvenance.USER_SUPPLIED));
    state.putContextValue("notjson",
        new StringContextValue("plain", ContextProvenance.USER_SUPPLIED));
    state.putContextValue("badjson",
        new JsonContextValue("{not json", ContextProvenance.USER_SUPPLIED));
    files.add(new CapturedFile("run-1", "step-a", "out/result.txt", "hello"));
    event(WorkflowEventType.STEP_STARTED, "step-a", null);
    event(WorkflowEventType.RUN_COMPLETED, null, null);

    assertThatThrownBy(() -> assertRun().didNotVisitStep("step-a"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().stepVisitCount("step-a", 5))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextMissing("present"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextNonEmpty("absent"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextMatchesRegex("name", "Z.*"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextHasField("notjson", "field"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().contextHasField("badjson", "field"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().artifactAbsent("out/result.txt"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().didNotEmitEvent(WorkflowEventType.RUN_COMPLETED))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().eventCount(WorkflowEventType.STEP_STARTED, 9))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().reachedPendingState(WorkflowStatus.AWAITING_INPUT))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().approvalRequested("step-a"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().inputRequested("step-a"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().toolCallCount(3))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().providerCallCount(3))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void toolAndProviderJsonFieldHandlesMalformedPayload() {
    event(WorkflowEventType.TOOL_INVOCATION_REQUESTED, "step-a", "{not json");
    event(WorkflowEventType.LLM_CALL_COMPLETED, "step-a", null);

    assertThatThrownBy(() -> assertRun().invokedTool("http:get"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertRun().providerCallTier(ModelTier.STANDARD))
        .isInstanceOf(AssertionError.class);
  }
}
