// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemWorkflowLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadWorkflows_loadsValidFilesystemWorkflow() throws IOException {
    Path workflowsRoot = createWorkflowBundle(
        "sample",
        """
            {
              "kind": "WORKFLOW",
              "id": "sample",
              "name": "Sample",
              "description": "Sample",
              "artifacts": {},
              "blueprints": {},
              "steps": [
                {
                  "kind": "STEP",
                  "stepId": "s1",
                  "name": "S1",
                  "behaviour": {
                    "type": "AGENT",
                    "agentRef": "sample.bundle-agent",
                    "transition": "AUTO"
                  }
                }
              ]
            }
            """);
    createBundledAgent(workflowsRoot.resolve("sample.workflow"), "local-agent.agent",
        "local-agent");

    WorkflowDirectoryLoad loaded = new FileSystemWorkflowLoader(new ObjectMapper())
        .loadWorkflows(workflowsRoot);

    WorkflowDefinition definition = loaded.workflows().get("sample");
    assertThat(definition).isNotNull();
    assertThat(definition.source()).isEqualTo(WorkflowSource.CUSTOM);
    assertThat(definition.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
    assertThat(loaded.bundledAgents()).containsKey("local-agent");
  }

  @Test
  void loadWorkflows_workflowIdMatchesDirectoryName() throws IOException {
    Path workflowsRoot = createWorkflowBundle(
        "expected",
        """
            {
              "kind": "WORKFLOW",
              "id": "different-id",
              "name": "Mismatch",
              "description": "Mismatch",
              "artifacts": {},
              "blueprints": {},
              "steps": [
                {
                  "kind": "STEP",
                  "stepId": "s1",
                  "name": "S1",
                  "behaviour": {
                    "type": "FAIL",
                    "reason": "stop"
                  }
                }
              ]
            }
            """);

    assertThatThrownBy(
        () -> new FileSystemWorkflowLoader(new ObjectMapper()).loadWorkflows(workflowsRoot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match bundle directory name");
  }

  @Test
  void loadWorkflows_blueprintAndArtifactIdMatchFilename() throws IOException {
    Path workflowsRoot = createWorkflowBundle(
        "sample",
        """
            {
              "kind": "WORKFLOW",
              "id": "sample",
              "name": "Sample",
              "description": "Sample",
              "artifacts": {},
              "blueprints": {},
              "steps": [
                {
                  "kind": "STEP",
                  "stepId": "s1",
                  "name": "S1",
                  "behaviour": {
                    "type": "FAIL",
                    "reason": "stop"
                  }
                }
              ]
            }
            """);
    Path workflowDir = workflowsRoot.resolve("sample.workflow");
    Files.writeString(workflowDir.resolve("wrong.blueprint.json"), """
        {
          "blueprintId": "different",
          "name": "Wrong",
          "behaviour": {
            "loopConfig": {
              "terminationStrategy": "AGENT_SIGNAL",
              "maxIterations": 2,
              "maxIterationsAction": "AWAIT_USER"
            },
            "transition": "AUTO"
          },
          "steps": [
            {
              "kind": "STEP",
              "stepId": "bp-step",
              "name": "BP Step",
              "behaviour": {
                "type": "FAIL",
                "reason": "done"
              }
            }
          ]
        }
        """);
    Files.writeString(workflowDir.resolve("wrong.artifact.json"), """
        {
          "id": "different-artifact",
          "items": [
            {
              "type": "TEXT_AREA",
              "id": "artifactField",
              "label": "Field",
              "required": true
            }
          ]
        }
        """);

    FileSystemWorkflowLoader loader = new FileSystemWorkflowLoader(new ObjectMapper());
    assertThatThrownBy(() -> loader.loadWorkflows(workflowsRoot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Declared blueprint id");

    Files.delete(workflowDir.resolve("wrong.blueprint.json"));
    assertThatThrownBy(() -> loader.loadWorkflows(workflowsRoot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Declared artifact id");
  }

  @Test
  void loadWorkflows_duplicateBundledAgentsFail() throws IOException {
    Path workflowsRoot = createWorkflowBundle("first",
        minimalWorkflowJson("first", "common"));
    createWorkflowBundle("second", minimalWorkflowJson("second", "common"));
    createBundledAgent(workflowsRoot.resolve("first.workflow"), "common.agent", "common");
    createBundledAgent(workflowsRoot.resolve("second.workflow"), "common.agent", "common");

    assertThatThrownBy(
        () -> new FileSystemWorkflowLoader(new ObjectMapper()).loadWorkflows(workflowsRoot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Duplicate bundled agent id 'common' across FileSystemWorkflowLoader (conflict while loading workflow 'second')");
  }

  @Test
  void loadWorkflows_stepPromptsAreInjected() throws IOException {
    Path workflowsRoot = createWorkflowBundle("sample",
        minimalWorkflowJson("sample", "agent.one"));
    Path workflowDir = workflowsRoot.resolve("sample.workflow");
    Files.writeString(workflowDir.resolve("s1.step.prompt.md"), "Prompt for step s1");

    WorkflowDirectoryLoad loaded = new FileSystemWorkflowLoader(new ObjectMapper())
        .loadWorkflows(workflowsRoot);
    WorkflowDefinition workflow = loaded.workflows().get("sample");
    Executable executable = workflow.steps().get(0);
    assertThat(executable).isInstanceOf(StepDefinition.class);
    assertThat(((StepDefinition) executable).stepPrompt()).isEqualTo("Prompt for step s1");
  }

  @Test
  void loadWorkflows_maxAttemptsZeroFails() throws IOException {
    Path workflowsRoot = createWorkflowBundle(
        "sample",
        """
            {
              "kind": "WORKFLOW",
              "id": "sample",
              "name": "Sample",
              "description": "Sample",
              "artifacts": {},
              "blueprints": {},
              "steps": [
                {
                  "kind": "STEP",
                  "stepId": "s1",
                  "name": "S1",
                  "behaviour": {
                    "type": "RETRY_PREVIOUS",
                    "retryStepId": "s0",
                    "retryMode": "FROM_STEP",
                    "maxAttempts": 0,
                    "fallback": {
                      "kind": "BLUEPRINT_REF",
                      "blueprintId": "fallback-bp"
                    }
                  }
                }
              ]
            }
            """);

    assertThatThrownBy(
        () -> new FileSystemWorkflowLoader(new ObjectMapper()).loadWorkflows(workflowsRoot))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Failed to parse workflow file")
        .hasStackTraceContaining("maxAttempts must be greater than zero");
  }

  @Test
  void loadWorkflows_missingAgentsDirectoryWorks() throws IOException {
    Path workflowsRoot = createWorkflowBundle("sample",
        minimalWorkflowJson("sample", "agent.one"));
    WorkflowDirectoryLoad loaded = new FileSystemWorkflowLoader(new ObjectMapper())
        .loadWorkflows(workflowsRoot);
    assertThat(loaded.workflows()).containsKey("sample");
    assertThat(loaded.bundledAgents()).isEmpty();
  }

  @Test
  void loadWorkflows_reusableLoaderSupportsDifferentRootsWithoutStateLeak() throws IOException {
    Path rootA = createWorkflowBundle("workflow-a", minimalWorkflowJson("workflow-a", "agent-a"));
    Path rootB = tempDir.resolve("workflows-b");
    Path workflowB = rootB.resolve("workflow-b.workflow");
    Files.createDirectories(workflowB);
    Files.writeString(workflowB.resolve("workflow.json"), minimalWorkflowJson("workflow-b", "agent-b"));

    FileSystemWorkflowLoader loader = new FileSystemWorkflowLoader(new ObjectMapper());
    WorkflowDirectoryLoad loadedA = loader.loadWorkflows(rootA);
    WorkflowDirectoryLoad loadedB = loader.loadWorkflows(rootB);

    assertThat(loadedA.workflows()).containsOnlyKeys("workflow-a");
    assertThat(loadedB.workflows()).containsOnlyKeys("workflow-b");
  }

  @Test
  void loadWorkflows_concurrentCallsWithDifferentRoots_doNotLeakState() throws IOException,
      ExecutionException, InterruptedException {
    Path rootA = createWorkflowBundle("concurrent-a", minimalWorkflowJson("concurrent-a", "agent-a"));
    Path rootB = tempDir.resolve("workflows-concurrent-b");
    Path workflowB = rootB.resolve("concurrent-b.workflow");
    Files.createDirectories(workflowB);
    Files.writeString(workflowB.resolve("workflow.json"), minimalWorkflowJson("concurrent-b", "agent-b"));

    FileSystemWorkflowLoader loader = new FileSystemWorkflowLoader(new ObjectMapper());
    CompletableFuture<WorkflowDirectoryLoad> loadA = CompletableFuture.supplyAsync(
        () -> loader.loadWorkflows(rootA));
    CompletableFuture<WorkflowDirectoryLoad> loadB = CompletableFuture.supplyAsync(
        () -> loader.loadWorkflows(rootB));

    WorkflowDirectoryLoad loadedA = loadA.get();
    WorkflowDirectoryLoad loadedB = loadB.get();

    assertThat(loadedA.workflows()).containsOnlyKeys("concurrent-a");
    assertThat(loadedB.workflows()).containsOnlyKeys("concurrent-b");
  }

  @Test
  void loaderHasNoMutablePerCallRootField() {
    Set<String> forbiddenNames = Set.of("activeWorkflowsRoot", "workflowsRoot", "activeRoot");
    assertThat(Arrays.stream(FileSystemWorkflowLoader.class.getDeclaredFields())
        .noneMatch(field -> forbiddenNames.contains(field.getName())))
        .isTrue();
  }

  private Path createWorkflowBundle(String dirId, String workflowJson)
      throws IOException {
    Path workflowsRoot = tempDir.resolve("workflows");
    Path workflowDir = workflowsRoot.resolve(dirId + ".workflow");
    Files.createDirectories(workflowDir);
    Files.writeString(workflowDir.resolve("workflow.json"), workflowJson);
    return workflowsRoot;
  }

  private void createBundledAgent(Path workflowDir, String bundleDirName, String agentId)
      throws IOException {
    Path agentDir = workflowDir.resolve("agents").resolve(bundleDirName);
    Files.createDirectories(agentDir);
    Files.writeString(agentDir.resolve("agent.json"), """
        {
          "id": "%s",
          "name": "Local Agent",
          "locality": "CLOUD",
          "enabled": true,
          "version": "1.0.0",
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
          "supportedCommands": ["COMPLETE"]
        }
        """.formatted(agentId));
    Files.writeString(agentDir.resolve("systemprompt.md"), "You are local.");
  }

  private String minimalWorkflowJson(String workflowId, String agentRef) {
    return """
        {
          "kind": "WORKFLOW",
          "id": "%s",
          "name": "Sample",
          "description": "Sample",
          "artifacts": {},
          "blueprints": {},
          "steps": [
            {
              "kind": "STEP",
              "stepId": "s1",
              "name": "S1",
              "behaviour": {
                "type": "AGENT",
                "agentRef": "%s",
                "transition": "AUTO"
              }
            }
          ]
        }
        """.formatted(workflowId, agentRef);
  }
}
