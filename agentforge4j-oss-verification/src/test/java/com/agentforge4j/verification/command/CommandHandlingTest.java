// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.command;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.verification.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box coverage of the LLM command handlers an agent can emit (8 of the 9; {@code TOOL_INVOCATION}
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
  void generateQuestionsResumeWithAnswersLandsThemInContextUserSupplied() {
    // Resume the GENERATE_QUESTIONS pause with an answer: it lands in context under the generated
    // artifact's namespaced key (generated.<uuid>.q1) with user-supplied provenance, and the agent
    // resumes to completion on its next scripted turn.
    WorkflowRunResult result = harness().run("cmd-generate-questions",
        List.of(GateResponse.input(Map.of("q1", "Alice"))));

    WorkflowRunAssert.assertThat(result).isCompleted();
    Map.Entry<String, ContextValue> answer = result.finalState().getContext().entrySet().stream()
        .filter(entry -> entry.getKey().startsWith("generated.") && entry.getKey().endsWith(".q1"))
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "No generated.<id>.q1 answer key found in context"));
    assertThat(((StringContextValue) answer.getValue()).value()).isEqualTo("Alice");
    assertThat(answer.getValue().provenance()).isEqualTo(ContextProvenance.USER_SUPPLIED);
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
}
