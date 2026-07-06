// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader;

import com.agentforge4j.config.loader.validation.WorkflowValidator;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.exception.DuplicateAgentIdException;
import com.agentforge4j.core.exception.DuplicateWorkflowIdException;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.config.loader.workflow.WorkflowDirectoryLoader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentForgeLoaderTest {

  @Test
  void mergeBundledAgentsStrict_rejectsDuplicateIds() {
    Map<String, AgentDefinition> target = new LinkedHashMap<>();
    AgentDefinition a = sampleAgent("dup", "A");
    target.put("dup", a);
    Map<String, AgentDefinition> more = Map.of("dup", sampleAgent("dup", "B"));
    assertThatThrownBy(() -> AgentForgeLoader.mergeBundledAgentsStrict(target, more, "second source"))
        .isInstanceOf(DuplicateAgentIdException.class)
        .hasMessageContaining("dup");
  }

  @Test
  void validateAgentRefs_reportsAllMissingRefsInOneException() {
    WorkflowValidator validator = new WorkflowValidator();
    StepDefinition step1 = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("missing-one", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    StepDefinition step2 = StepDefinition.builder()
        .withStepId("s2")
        .withName("S2")
        .withBehaviour(new AgentBehaviour("missing-two", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step1, step2), List.of(), List.of());
    assertThatThrownBy(() -> validator.validateAgentRefs(Map.of("wf1", wf), Map.of()))
        .isInstanceOf(UnresolvedAgentReferenceException.class)
        .hasMessageContaining("missing-one")
        .hasMessageContaining("missing-two")
        .hasMessageContaining("step 's1'")
        .hasMessageContaining("step 's2'");
  }

  private static AgentDefinition sampleAgent(String id, String name) {
    return AgentDefinition.builder()
        .withId(id)
        .withName(name)
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
  }

  @Test
  void validateAgentRefs_succeedsWhenAgentsRegisteredBeforeValidation() {
    WorkflowValidator validator = new WorkflowValidator();
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S1")
        .withBehaviour(new AgentBehaviour("present", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step), List.of(), List.of());
    Map<String, AgentDefinition> agents = Map.of("present", sampleAgent("present", "P"));
    validator.validateAgentRefs(Map.of("wf1", wf), agents);
  }

  @Test
  void mergeBundledAgentsStrict_noOpWhenAdditionsEmpty() {
    Map<String, AgentDefinition> target = new LinkedHashMap<>();
    target.put("a", sampleAgent("a", "A"));

    AgentForgeLoader.mergeBundledAgentsStrict(target, Map.of(), "empty");

    assertThat(target).containsOnlyKeys("a");
  }

  @Test
  void mergeWorkflowsStrict_noOpWhenAdditionsEmpty() {
    Map<String, WorkflowDefinition> target = new LinkedHashMap<>();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("S1")
            .withBehaviour(new FailBehaviour("stop"))
            .withContextMapping(new ContextMapping(List.of(), List.of()))
            .build()), List.of(), List.of());
    target.put("wf", wf);

    AgentForgeLoader.mergeWorkflowsStrict(target, Map.of(), "empty");

    assertThat(target).containsOnlyKeys("wf");
  }

  @Test
  void mergeWorkflowsStrict_rejectsDuplicateIds() {
    Map<String, WorkflowDefinition> target = new LinkedHashMap<>();
    WorkflowDefinition existing = new WorkflowDefinition(
        "dup-wf", "Existing", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("S1")
            .withBehaviour(new FailBehaviour("stop"))
            .withContextMapping(new ContextMapping(List.of(), List.of()))
            .build()), List.of(), List.of());
    target.put("dup-wf", existing);
    WorkflowDefinition duplicate = new WorkflowDefinition(
        "dup-wf", "Duplicate", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(StepDefinition.builder()
            .withStepId("s2")
            .withName("S2")
            .withBehaviour(new FailBehaviour("stop"))
            .withContextMapping(new ContextMapping(List.of(), List.of()))
            .build()), List.of(), List.of());

    assertThatThrownBy(() -> AgentForgeLoader.mergeWorkflowsStrict(
        target, Map.of("dup-wf", duplicate), "second source"))
        .isInstanceOf(DuplicateWorkflowIdException.class)
        .hasMessageContaining("dup-wf");
  }

  @Test
  void loadWorkflowDirectory_usesProvidedPathViaDirectoryLoader() {
    AtomicReference<Path> capturedPath = new AtomicReference<>();
    WorkflowDirectoryLoader directoryLoader = root -> {
      capturedPath.set(root);
      return new WorkflowDirectoryLoad(Map.of(), Map.of());
    };
    AgentForgeLoader loader = new AgentForgeLoader(emptyAgentLoader(), directoryLoader);

    Path draftRoot = Path.of("target/drafts");
    loader.loadWorkflowDirectory(draftRoot);

    assertThat(capturedPath.get()).isEqualTo(draftRoot);
  }

  @Test
  void loadWorkflows_usesProvidedPathViaDirectoryLoader() {
    AtomicReference<Path> capturedPath = new AtomicReference<>();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf", "W", "d", null, null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("S1")
            .withBehaviour(new FailBehaviour("stop"))
            .withContextMapping(new ContextMapping(List.of(), List.of()))
            .build()), List.of(), List.of());
    WorkflowDirectoryLoader directoryLoader = root -> {
      capturedPath.set(root);
      return new WorkflowDirectoryLoad(Map.of("wf", workflow), Map.of());
    };
    AgentForgeLoader loader = new AgentForgeLoader(emptyAgentLoader(),
        directoryLoader);

    Path customRoot = Path.of("target/workflows");
    Map<String, WorkflowDefinition> loaded = loader.loadWorkflows(customRoot);

    assertThat(capturedPath.get()).isEqualTo(customRoot);
    assertThat(loaded).containsKey("wf");
  }

  @Test
  void load_withWorkflowDir_usesPathScopedWorkflowDirectoryLoader() {
    AtomicReference<Path> capturedPath = new AtomicReference<>();
    WorkflowDirectoryLoader directoryLoader = root -> {
      capturedPath.set(root);
      return new WorkflowDirectoryLoad(Map.of(), Map.of());
    };
    AgentForgeLoader loader = new AgentForgeLoader(
        emptyAgentLoader(),
        directoryLoader);
    Path workflowsRoot = Path.of("target/explicit-workflows");

    LoadedConfiguration loaded = loader.load(Optional.empty(), Optional.of(workflowsRoot),
        Optional.empty(), Optional.empty());

    assertThat(capturedPath.get()).isEqualTo(workflowsRoot);
    assertThat(loaded.workflows()).isEmpty();
    assertThat(loaded.agents()).isEmpty();
  }

  @Test
  void load_withBlankOptionalPaths_doesNotInvokeFilesystemLoaders() {
    AtomicReference<Path> capturedPath = new AtomicReference<>();
    AgentForgeLoader loader = new AgentForgeLoader(
        emptyAgentLoader(),
        root -> {
          capturedPath.set(root);
          return new WorkflowDirectoryLoad(Map.of(), Map.of());
        });

    LoadedConfiguration loaded = loader.load(Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty());

    assertThat(capturedPath.get()).isNull();
    assertThat(loaded.agents()).isEmpty();
    assertThat(loaded.workflows()).isEmpty();
  }

  @Test
  void pathScopedLoadMethods_failClearlyWithoutWorkflowDirectoryLoader() {
    AgentForgeLoader loader = new AgentForgeLoader(emptyAgentLoader());

    assertThatThrownBy(() -> loader.loadWorkflowDirectory(Path.of("target/any")))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Path-scoped workflow loading is unavailable");
  }

  private static AgentLoader emptyAgentLoader() {
    return new AgentLoader() {
      @Override
      public Map<String, AgentDefinition> loadAgents() {
        return Map.of();
      }

      @Override
      public Map<String, AgentDefinition> loadAgents(List<String> bundleFiles) {
        return Map.of();
      }
    };
  }

  private static WorkflowLoader emptyWorkflowLoader() {
    return () -> new WorkflowDirectoryLoad(Map.of(), Map.of());
  }

}
