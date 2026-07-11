// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.LlmSummary;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Bootstrap wiring guard for {@link AgentForge4jBootstrap.Builder#withContextPacksDir(Path)}: a
 * loaded pack must both pass {@code CONTEXT_PACK} selector load-time validation AND be resolvable
 * by the runtime — {@code resolveFull} runs unconditionally before a {@code COMPACT} step's
 * skip/perform decision, so reaching a completed run proves the same {@link
 * com.agentforge4j.runtime.ContextPackRegistry} the bootstrap loaded actually reached the runtime,
 * not merely the load-time validator.
 */
class ContextPackBootstrapWiringTest {

  private static final ContextSelector PACK_SOURCE = new ContextSelector(
      ContextSourceKind.CONTEXT_PACK, "docs", ContextVariant.FULL);

  @Test
  void loadedPackIsResolvableAtRuntimeThroughAContextPackSource(@TempDir Path packsDir)
      throws IOException {
    writePack(packsDir, "docs", "FULL CONTENT");
    WorkflowDefinition wf = compactWorkflow();
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withContextPacksDir(packsDir)
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", wf)))
        .withWorkflowStateRepository(stateRepository)
        .withFileSink(FileSink.NO_OP_FILE_SINK)
        .withAgentInvoker(mock(AgentInvoker.class))
        .build();

    WorkflowRuntime runtime = af.runtime();
    String runId = runtime.start("wf1");

    WorkflowState state = stateRepository.findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void aContextPackSelectorFailsValidationWhenNoPacksAreConfigured(@TempDir Path workflowsDir,
      @TempDir Path agentsDir) throws IOException {
    // Routed through withWorkflowsDir/withAgentsDir (not withWorkflowRepository) deliberately:
    // only a workflow the config-loader itself loads passes through AgentForgeLoader's validation
    // pipeline, which is what this test targets. A workflow injected directly via
    // withWorkflowRepository bypasses that pipeline entirely and would never exercise this check.
    // A valid agent fixture for "summarizer-agent" is required so agent-ref validation (which runs
    // before context-selection validation) passes, isolating the context-pack failure this test
    // targets.
    writeWorkflowFile(workflowsDir);
    writeSummarizerAgent(agentsDir);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
            .withLoadShippedAgents(false)
            .withLoadShippedWorkflows(false)
            .withWorkflowsDir(workflowsDir)
            .withAgentsDir(agentsDir)
            .withFileSink(FileSink.NO_OP_FILE_SINK)
            .withAgentInvoker(mock(AgentInvoker.class))
            .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to load AgentForge4j configuration")
        .hasRootCauseMessage(
            "Step 'compact' in workflow 'wf1' selects unknown context pack 'docs'");
  }

  private static void writeSummarizerAgent(Path agentsDir) throws IOException {
    Path dir = Files.createDirectories(agentsDir.resolve("summarizer-agent.agent"));
    Files.writeString(dir.resolve("agent.json"), """
        {
          "id": "summarizer-agent",
          "name": "Summarizer",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [ { "provider": "openai", "model": "gpt-4o-mini" } ],
          "supportedCommands": ["COMPLETE"]
        }
        """);
    Files.writeString(dir.resolve("systemprompt.md"), "Summarize the input.");
  }

  private static void writeWorkflowFile(Path workflowsDir) throws IOException {
    Path dir = Files.createDirectories(workflowsDir.resolve("wf1.workflow"));
    Files.writeString(dir.resolve("workflow.json"), """
        {
          "kind": "WORKFLOW",
          "schemaVersion": 1,
          "id": "wf1",
          "name": "W",
          "steps": [
            {
              "kind": "STEP",
              "stepId": "compact",
              "name": "Compact",
              "behaviour": {
                "type": "COMPACT",
                "source": { "kind": "CONTEXT_PACK", "ref": "docs", "variant": "FULL" },
                "mode": { "type": "LLM_SUMMARY", "modelTier": "STANDARD", "agentRef": "summarizer-agent" },
                "policy": { "minSourceUnits": 1000000, "minDownstreamReuse": 0 }
              }
            }
          ]
        }
        """);
  }

  private static WorkflowDefinition compactWorkflow() {
    // minSourceUnits set impossibly high so decideSkip always returns SOURCE_TOO_SMALL — the
    // point of this fixture is proving contextSourceResolver.resolveFull(source, ...) (which runs
    // unconditionally before that decision) succeeds against the loaded pack, not exercising
    // LlmSummary's agent invocation.
    CompactBehaviour compact = new CompactBehaviour(PACK_SOURCE,
        new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(1_000_000, 0));
    StepDefinition compactStep = StepDefinition.builder()
        .withStepId("compact")
        .withName("Compact")
        .withBehaviour(compact)
        .build();
    return new WorkflowDefinition("wf1", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(compactStep), List.of(), List.of());
  }

  private static void writePack(Path packsDir, String name, String fullContent)
      throws IOException {
    Path dir = Files.createDirectories(packsDir.resolve(name));
    Files.writeString(dir.resolve("pack.json"), """
        {"name":"%s","version":"1.0.0","variants":{"full":"content.md"}}""".formatted(name));
    Files.writeString(dir.resolve("content.md"), fullContent);
  }
}
