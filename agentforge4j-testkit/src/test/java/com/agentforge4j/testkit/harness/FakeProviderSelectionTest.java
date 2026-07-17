// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the fake provider-selection seam no longer depends on a hardcoded provider catalog: the
 * harness drives any agent regardless of which providers it prefers. Guards against a regression of
 * the former {@code FakeLlmClientResolver.KNOWN_PROVIDERS} list, which silently rejected an agent
 * whose only preference was absent from it.
 */
class FakeProviderSelectionTest {

  private static final String WORKFLOW_JSON = """
      {
        "kind": "WORKFLOW",
        "schemaVersion": 1,
        "id": "exotic-provider",
        "name": "Exotic Provider",
        "steps": [
          {
            "kind": "STEP",
            "stepId": "run-agent",
            "name": "Run Agent",
            "behaviour": { "type": "AGENT", "agentRef": "exotic-agent", "transition": "AUTO" }
          }
        ]
      }
      """;

  // providerPreferences names a provider that was NOT in the former hardcoded KNOWN_PROVIDERS list.
  private static final String AGENT_JSON = """
      {
        "id": "exotic-agent",
        "name": "Exotic Agent",
        "locality": "CLOUD",
        "enabled": true,
        "version": "1.0.0",
        "providerPreferences": [ { "provider": "acme-llm" } ],
        "supportedCommands": ["COMPLETE"]
      }
      """;

  private static final String SCRIPT_JSON = """
      {
        "schemaVersion": 1,
        "responses": [
          {
            "workflowId": "exotic-provider",
            "stepId": "run-agent",
            "agentId": "exotic-agent",
            "ordinal": 0,
            "responseText": "[{\\"type\\":\\"COMPLETE\\"}]"
          }
        ]
      }
      """;

  @Test
  void harnessRunsAgentPreferringProviderAbsentFromAnyCatalog(@TempDir Path workflowsDir)
      throws IOException {
    writeFixture(workflowsDir);

    WorkflowTestHarness harness = WorkflowTestHarness.builder()
        .workflowsDir(workflowsDir)
        .script(new ScenarioScriptLoader().fromJson(SCRIPT_JSON))
        .build();

    WorkflowRunResult result = harness.run("exotic-provider");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .visitedStep("run-agent")
        .providerCallCount(1);
  }

  @Test
  void strategySelectsFirstDeclaredPreferenceIgnoringAvailability() {
    AgentDefinition agent = AgentDefinition.builder()
        .withId("exotic-agent")
        .withName("Exotic Agent")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("test prompt")
        .withVersion("1.0.0")
        .withProviderPreferences(List.of(
            new ProviderPreference("acme-llm", null),
            new ProviderPreference("fake", null)))
        .build();

    // The advertised-available list is empty (the wildcard resolver enumerates nothing), yet the
    // first declared preference is still selected — normal order-preserving selection is intact.
    assertThat(new FakeProviderSelectionStrategy().selectInitialProvider(agent, List.of()).provider())
        .isEqualTo("acme-llm");
  }

  private static void writeFixture(Path workflowsDir) throws IOException {
    Path bundle = workflowsDir.resolve("exotic-provider.workflow");
    Path agentDir = bundle.resolve("agents").resolve("exotic-agent.agent");
    Files.createDirectories(agentDir);
    Files.writeString(bundle.resolve("workflow.json"), WORKFLOW_JSON);
    Files.writeString(agentDir.resolve("agent.json"), AGENT_JSON);
    Files.writeString(agentDir.resolve("systemprompt.md"), "You are a deterministic test agent.");
  }
}
