// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.testkit.tool.ScriptedToolProvider;
import com.agentforge4j.verification.support.FailOnceThenSucceedToolProvider;
import com.agentforge4j.verification.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tool-governance verification. The runtime tool chokepoint (resolve → validate → policy → invoke
 * and the approval / decision suspend-resume branches) is driven end-to-end by a deterministic
 * in-process {@link ScriptedToolProvider} via the harness tool wiring — no MCP transport. The agent
 * emits a single {@code TOOL_INVOCATION} (fixture {@code /fixtures/tool}); the policy and provider
 * vary per case to reach every governance outcome: executed, approval→resume, approval→reject,
 * policy-deny→decision, validation-failure→decision, and provider-failure→decision.
 *
 * <p>The suspend-resume cases resolve the gate with no explicit invocation id, exercising the
 * harness auto-targeting of the run's single current pending invocation.
 */
class ToolGovernanceTest {

  private static final String CAPABILITY = "test.echo";
  private static final String OUTPUT = "{\"ok\":true}";

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/tool/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read tool fake script", e);
    }
  }

  private static WorkflowTestHarness.Builder harness(ToolProvider provider) {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/tool/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/tool/agents"))
        .script(script())
        .toolProviders(List.of(provider));
  }

  private static ToolPolicy requireApproval() {
    return (cmd, descriptor, ctx) -> new PolicyDecision.RequireApproval("needs review", null);
  }

  private static ToolPolicy deny() {
    return (cmd, descriptor, ctx) -> new PolicyDecision.Deny("capability not allowed");
  }

  @Test
  void allowedToolExecutesAndCompletes() {
    WorkflowRunResult result = harness(ScriptedToolProvider.succeeding("p", CAPABILITY, OUTPUT))
        .build()
        .run("tool-run");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .invokedTool(CAPABILITY)
        // A successful invocation emits REQUESTED then COMPLETED, in that order.
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED)
        .eventsInOrder(WorkflowEventType.TOOL_INVOCATION_REQUESTED,
            WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void requireApprovalThenApproveResumesAndExecutes() {
    WorkflowRunResult result = harness(ScriptedToolProvider.succeeding("p", CAPABILITY, OUTPUT))
        .toolPolicy(requireApproval())
        .build()
        // No explicit id: auto-target the run's single pending approval (AWAITING_TOOL_APPROVAL).
        .run("tool-run", List.of(GateResponse.toolApprove()));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .invokedTool(CAPABILITY)
        // The call suspends for approval (APPROVAL_PENDING) and, once approved, executes (COMPLETED).
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_APPROVAL_PENDING)
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED)
        .eventsInOrder(WorkflowEventType.TOOL_INVOCATION_REQUESTED,
            WorkflowEventType.TOOL_INVOCATION_APPROVAL_PENDING,
            WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void requireApprovalThenRejectDeniesTheCallAndAdvances() {
    WorkflowRunResult result = harness(ScriptedToolProvider.succeeding("p", CAPABILITY, OUTPUT))
        .toolPolicy(requireApproval())
        .build()
        .run("tool-run", List.of(GateResponse.toolReject("rejected by operator")));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        // The call suspended for approval before the operator rejected it; the rejected call is
        // never executed.
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_APPROVAL_PENDING)
        .didNotEmitEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void policyDenyThenContinueDecisionAdvances() {
    WorkflowRunResult result = harness(ScriptedToolProvider.succeeding("p", CAPABILITY, OUTPUT))
        .toolPolicy(deny())
        .build()
        // Deny suspends in AWAITING_TOOL_DECISION; continue proceeds without a tool result.
        .run("tool-run", List.of(GateResponse.toolContinue()));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        // Policy denial is recorded and the denied call is never executed.
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_DENIED)
        .didNotEmitEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void argumentValidationFailureThenContinueDecisionAdvances() {
    // The capability requires input field "city"; the scripted arguments omit it, so validation
    // fails before the provider is invoked and the run suspends in AWAITING_TOOL_DECISION.
    WorkflowRunResult result =
        harness(ScriptedToolProvider.requiring("p", CAPABILITY, OUTPUT, "city"))
            .build()
            .run("tool-run", List.of(GateResponse.toolContinue()));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        // Validation fails before invoke, so the call fails and never completes.
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_FAILED)
        .didNotEmitEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void providerFailureThenContinueDecisionAdvances() {
    WorkflowRunResult result =
        harness(ScriptedToolProvider.failing("p", CAPABILITY, "provider exploded"))
            .build()
            // Invoke failure suspends in AWAITING_TOOL_DECISION; continue proceeds without a result.
            .run("tool-run", List.of(GateResponse.toolContinue()));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        // The provider error is recorded as a failed invocation; the call never completes.
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_FAILED)
        .didNotEmitEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void providerFailsOnceThenRetryReplaysTheCallAndAppliesTheResult() {
    FailOnceThenSucceedToolProvider provider =
        new FailOnceThenSucceedToolProvider("p", CAPABILITY, OUTPUT, "transient failure");

    WorkflowRunResult result = harness(provider)
        .build()
        // First invocation fails → AWAITING_TOOL_DECISION; toolRetry replays the stored call, which
        // now succeeds, applying the tool result and advancing the run to completion.
        .run("tool-run", List.of(GateResponse.toolRetry()));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .invokedTool(CAPABILITY)
        // The first invocation failed once (one TOOL_INVOCATION_FAILED) before the retry replayed
        // the call and completed it — proving the run advanced on the replayed result, not by
        // continuing past the failure.
        .eventCount(WorkflowEventType.TOOL_INVOCATION_FAILED, 1)
        .emittedEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED)
        .eventsInOrder(WorkflowEventType.TOOL_INVOCATION_FAILED,
            WorkflowEventType.TOOL_INVOCATION_COMPLETED)
        // The retried (second) invocation's output is applied under the reserved tool context key.
        .contextEquals("tool." + CAPABILITY, OUTPUT);
    assertThat(provider.invocationCount()).isEqualTo(2);
  }
}
