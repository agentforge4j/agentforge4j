// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.command;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.verification.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Black-box coverage of the LLM command handlers an agent can emit (9 of the 10; {@code TOOL_INVOCATION}
 * is exercised by the tool tier). Each scenario drives a single AGENT step whose fake response is the
 * command array under test, and asserts the observable effect (context, file, pause state, event,
 * failure). Also covers {@code supportedCommands} gating: a restricted agent emitting a disallowed
 * command fails the run.
 */
class CommandHandlingTest {

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/command/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/command/agents"))
        .script(script())
        .build();
  }

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(Files.readString(Fixtures.dir("/fixtures/command/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read command fake script", e);
    }
  }

  @Test
  void setContextWritesTypedContextValue() {
    WorkflowRunResult result = harness().run("cmd-set-context");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .contextEquals("greeting", "hello");
  }

  @Test
  void createFileIsCaptured() {
    WorkflowRunResult result = harness().run("cmd-create-file");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .createdFile("out/result.txt");
  }

  @Test
  void userPromptWithoutResponseStoresMessageAndContinues() {
    WorkflowRunResult result = harness().run("cmd-user-prompt");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.CONTEXT_UPDATED);
  }

  @Test
  void escalatePausesAwaitingApproval() {
    WorkflowRunResult result = harness().run("cmd-escalate");
    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_APPROVAL);
  }

  @Test
  void generateQuestionsPausesAwaitingInput() {
    WorkflowRunResult result = harness().run("cmd-generate-questions");
    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_INPUT);
  }

  @Test
  void runCommandCompletesViaNoOpRunner() {
    WorkflowRunResult result = harness().run("cmd-run-command");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .emittedEvent(WorkflowEventType.CONTEXT_UPDATED);
  }

  @Test
  void disallowedCommandFailsTheRun() {
    WorkflowRunResult result = harness().run("cmd-gating");
    WorkflowRunAssert.assertThat(result)
        .isFailed()
        .contextMissing("x");
  }

  @Test
  void requestContextGrantsDeniesReservedAndOutOfScopeInOrder() {
    // The requested selectors, in order: shared-note (declared in expandableScope, resolvable) ->
    // GRANTED; __reserved (declared, but the '__' runtime namespace is always denied at grant time)
    // -> DENIED reason=RESERVED_NAMESPACE; not-declared (never in expandableScope) -> DENIED
    // reason=NOT_IN_EXPANDABLE_SCOPE. maxExpansions=3 keeps all three under the round limit so the
    // limit check never masks the scope/reserved-namespace checks this scenario targets.
    WorkflowRunResult result = harness().run("cmd-request-context");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .eventCount(WorkflowEventType.CONTEXT_EXPANSION_GRANTED, 1)
        .eventCount(WorkflowEventType.CONTEXT_EXPANSION_DENIED, 2)
        .eventsInOrder(WorkflowEventType.CONTEXT_EXPANSION_GRANTED,
            WorkflowEventType.CONTEXT_EXPANSION_DENIED);
  }

  @Test
  void requestContextDeniesBeyondMaxExpansions() {
    // Two in-scope, resolvable selectors requested against the default maxExpansions=1: the first
    // is granted, the second exceeds the round limit and is denied reason=MAX_EXPANSIONS_REACHED —
    // even though it would otherwise have been a legitimate grant.
    WorkflowRunResult result = harness().run("cmd-request-context-max-expansions");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .eventCount(WorkflowEventType.CONTEXT_EXPANSION_GRANTED, 1)
        .eventCount(WorkflowEventType.CONTEXT_EXPANSION_DENIED, 1)
        .eventsInOrder(WorkflowEventType.CONTEXT_EXPANSION_GRANTED,
            WorkflowEventType.CONTEXT_EXPANSION_DENIED);
  }
}
