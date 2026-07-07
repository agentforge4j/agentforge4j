// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

/**
 * Loads workflow definitions from a backing source.
 */
@RequiredArgsConstructor
public abstract class BaseWorkflowBundleLoader {

  /**
   * Maximum <em>inline</em> definition nesting accepted at load time; aligns with the runtime's default. Distinct from
   * the reachable-graph traversal bounds (the runtime's configurable {@code DEFAULT_MAX_NESTING_DEPTH} and the
   * ref-following walks in {@code WorkflowAgentRefCollector} / {@code ReachableStepGraph}): this one bounds only the
   * static inline-structure walk below and does not follow {@code BlueprintRef}/{@code WORKFLOW} references, so it is
   * intentionally not single-sourced with them.
   */
  private static final int MAX_NESTING_DEPTH = 32;

  protected static final String WORKFLOW_DEFINITION_FILE = "workflow.json";
  protected static final String WORKFLOW_DIR_SUFFIX = ".workflow";
  protected static final String BLUEPRINT_SUFFIX = ".blueprint.json";
  protected static final String ARTIFACT_SUFFIX = ".artifact.json";
  protected static final String STEP_PROMPT_SUFFIX = ".step.prompt.md";
  protected static final String AGENTS_DIR = "agents";

  final ObjectMapper workflowMapper;
  private final String loaderName;
  private final WorkflowSource workflowSource;

  protected abstract void log(System.Logger.Level level, String message, Object... args);

  protected abstract List<String> getWorkflowIdList(WorkflowBundleLoadContext context);

  protected abstract WorkflowDefinition retrieveWorkflowDefinition(
      WorkflowBundleLoadContext context,
      String workflowId,
      Map<String, AgentDefinition> bundledAgents);

  protected abstract List<String> retrieveFilesFromIndex(WorkflowBundleLoadContext context,
      String workflowId);

  protected abstract <T> T loadItem(WorkflowBundleLoadContext context, String path,
      String workflowId,
      String derivedId, String type,
      Class<T> typeClass);

  protected abstract String loadRawContent(WorkflowBundleLoadContext context, String classpathPath,
      String workflowId);

  protected abstract Map<String, AgentDefinition> loadBundledAgentsFromBundle(
      WorkflowBundleLoadContext context, String workflowId,
      List<String> bundleFiles);

  public WorkflowDirectoryLoad loadWorkflows(Path root) {
    return loadWorkflows(WorkflowBundleLoadContext.filesystem(root));
  }

  protected WorkflowDirectoryLoad loadWorkflows(WorkflowBundleLoadContext context) {
    log(System.Logger.Level.DEBUG, "Loading {0} workflows", loaderName);

    Map<String, WorkflowDefinition> loaded = new LinkedHashMap<>();
    Map<String, AgentDefinition> bundledAgents = new LinkedHashMap<>();

    for (String workflowPath : getWorkflowIdList(context)) {
      Validate.isTrue(workflowPath.endsWith(WORKFLOW_DIR_SUFFIX),
          "Workflow index entry must end with .workflow: %s".formatted(workflowPath));
      String workflowId = workflowPath.substring(
          0,
          workflowPath.length() - WORKFLOW_DIR_SUFFIX.length());
      WorkflowDefinition def = readWorkflow(context, workflowId, bundledAgents);
      WorkflowDefinition previous = loaded.put(def.id(), def);
      Validate.isTrue(previous == null, "Duplicate workflow id found: %s".formatted(def.id()));
    }

    return new WorkflowDirectoryLoad(Map.copyOf(loaded), Map.copyOf(bundledAgents));
  }

  protected WorkflowDefinition readWorkflow(WorkflowBundleLoadContext context, String workflowId,
      Map<String, AgentDefinition> bundledAgents) {
    log(System.Logger.Level.DEBUG, "%s found workflowId=%s".formatted(loaderName, workflowId));

    WorkflowDefinition workflowDefinition = retrieveWorkflowDefinition(context, workflowId,
        bundledAgents);
    validateWorkflow(workflowDefinition, workflowId);
    validateBehaviourConfiguration(workflowDefinition.steps(), workflowDefinition.id());
    List<String> workflowFiles = retrieveFilesFromIndex(context, workflowId);

    Map<String, BlueprintDefinition> blueprints = loadItemFromBundle(context, workflowFiles,
        workflowId,
        BLUEPRINT_SUFFIX, "blueprint", BlueprintDefinition.class, BlueprintDefinition::blueprintId);
    Map<String, ArtifactDefinition> artifacts = loadItemFromBundle(context, workflowFiles,
        workflowId,
        ARTIFACT_SUFFIX, "artifact", ArtifactDefinition.class, ArtifactDefinition::id);
    Map<String, String> stepPrompts = loadItemFromBundle(context, workflowFiles, workflowId,
        STEP_PROMPT_SUFFIX, "step prompt", String.class, null);

    Map<String, AgentDefinition> bundleAgents = loadBundledAgentsFromBundle(context, workflowId,
        workflowFiles);
    for (Map.Entry<String, AgentDefinition> entry : bundleAgents.entrySet()) {
      AgentDefinition prior = bundledAgents.put(entry.getKey(), entry.getValue());
      Validate.isTrue(prior == null,
          "Duplicate bundled agent id '%s' across %s (conflict while loading workflow '%s')"
              .formatted(entry.getKey(), loaderName, workflowId));
    }

    return WorkflowDefinition.duplicate(
        workflowDefinition,
        workflowSource,
        WorkflowLifecycle.ACTIVE,
        artifacts,
        injectStepPromptsInBlueprintMap(blueprints, stepPrompts),
        injectStepPrompts(workflowDefinition.steps(), stepPrompts));
  }

  private void validateWorkflow(WorkflowDefinition definition,
      String expectedIdFromDirName) {
    Validate.notNull(definition,
        "Workflow file produced null definition: %s".formatted(expectedIdFromDirName));
    Validate.notBlank(definition.id(),
        "Workflow id is required: %s".formatted(expectedIdFromDirName));
    Validate.isTrue(definition.id().equals(expectedIdFromDirName),
        "Workflow id '%s' must match bundle directory name <id>.workflow (expected id '%s'): %s"
            .formatted(definition.id(), expectedIdFromDirName, expectedIdFromDirName));
    Validate.notBlank(definition.name(),
        "Workflow name is required: %s".formatted(expectedIdFromDirName));
    Validate.notEmpty(definition.steps(),
        "Workflow steps must not be empty: %s".formatted(expectedIdFromDirName));
  }

  private <T> Map<String, T> loadItemFromBundle(
      WorkflowBundleLoadContext context, List<String> bundleFiles, String workflowId, String suffix,
      String type,
      Class<T> typeClass, Function<T, String> idExtractor) {
    Map<String, T> loaded = new LinkedHashMap<>();
    for (String filename : bundleFiles) {
      if (!filename.endsWith(suffix)) {
        continue;
      }
      log(System.Logger.Level.DEBUG,
          "Bundle entry path=%s, resolvedType=%s".formatted(filename, type));
      String fileNameOnly = filename.substring(Math.max(
          filename.lastIndexOf('/'),
          filename.lastIndexOf('\\')) + 1);
      String derivedId = fileNameOnly.substring(0, fileNameOnly.length() - suffix.length());

      T item;
      if (String.class == typeClass) {
        item = (T) loadRawContent(context, filename, workflowId);
      } else {
        item = loadItem(context, filename, workflowId, derivedId, type, typeClass);
      }
      if (idExtractor != null) {
        Validate.isTrue(idExtractor.apply(item).equals(derivedId),
            "Declared %s id '%s' does not match expected id '%s' derived from filename in workflow '%s'"
                .formatted(type, idExtractor.apply(item), derivedId, workflowId));
      }
      validateDuplicateItem(workflowId, loaded, item, derivedId, type);
    }
    return Map.copyOf(loaded);
  }

  private <T> void validateDuplicateItem(String workflowId,
      Map<String, T> map,
      T definition, String id, String type) {
    T duplicate = map.put(id, definition);
    Validate.isTrue(duplicate == null, "Duplicate %s id '%s' in %s '%s'"
        .formatted(type, id, loaderName, workflowId));
  }

  private void validateRetryPreviousBehaviour(StepDefinition step, String workflowId,
      RetryPreviousBehaviour behaviour) {
    Validate.notBlank(behaviour.retryStepId(),
        "RetryPreviousBehaviour retryStepId must not be blank in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
    Validate.notNull(behaviour.retryMode(),
        "RetryPreviousBehaviour retryMode must not be null in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
    Validate.isGreaterThanZero(behaviour.maxAttempts(),
        "RetryPreviousBehaviour maxAttempts must be greater than zero in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
    // The compact constructor already guarantees a non-null fallback; this check is kept
    // deliberately so a violation here reports the friendlier loader-context message (with
    // stepId/workflowId) instead of the record's generic one.
    Validate.notNull(behaviour.fallback(),
        "RetryPreviousBehaviour fallback must not be null in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
  }

  private void validateFailBehaviour(StepDefinition step, String workflowId,
      FailBehaviour behaviour) {
    Validate.notBlank(behaviour.reason(),
        "FailBehaviour reason must not be blank in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
  }

  private void validateBranchBehaviour(StepDefinition step, String workflowId,
      BranchBehaviour behaviour) {
    Validate.notBlank(behaviour.contextKey(),
        "BranchBehaviour contextKey must not be blank in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
    Validate.notNull(behaviour.branches(),
        "BranchBehaviour branches must not be null in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
    Validate.isTrue(!behaviour.branches().isEmpty() || !behaviour.predicates().isEmpty(),
        "BranchBehaviour must declare at least one branch or predicate in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
  }

  private List<Executable> injectStepPrompts(List<Executable> steps,
      Map<String, String> loadedStepPrompts) {
    return steps.stream()
        .map(executable -> injectStepPrompt(executable, loadedStepPrompts))
        .toList();
  }

  private Executable injectStepPrompt(Executable executable,
      Map<String, String> loadedStepPrompts) {
    if (executable instanceof StepDefinition step) {
      return StepDefinition.duplicate(step, loadedStepPrompts.get(step.stepId()));
    } else if (executable instanceof WorkflowDefinition nested) {
      return WorkflowDefinition.duplicate(nested,
          injectStepPromptsInBlueprintMap(nested.blueprints(), loadedStepPrompts),
          injectStepPrompts(nested.steps(), loadedStepPrompts));
    } else if (executable instanceof BlueprintRef ref) {
      return ref;
    }
    throw new IllegalStateException(
        "Unsupported executable type: %s".formatted(executable.getClass().getName()));
  }

  private Map<String, BlueprintDefinition> injectStepPromptsInBlueprintMap(
      Map<String, BlueprintDefinition> blueprints,
      Map<String, String> loadedStepPrompts) {
    Map<String, BlueprintDefinition> injected = new LinkedHashMap<>();
    for (Map.Entry<String, BlueprintDefinition> entry : blueprints.entrySet()) {
      BlueprintDefinition blueprint = entry.getValue();
      injected.put(entry.getKey(), BlueprintDefinition.duplicate(
          blueprint, injectStepPrompts(blueprint.steps(), loadedStepPrompts)));
    }
    return Map.copyOf(injected);
  }


  private void validateBehaviourConfiguration(List<Executable> executables, String workflowId) {
    validateBehaviourConfiguration(executables, workflowId, 0);
  }

  private void validateBehaviourConfiguration(List<Executable> executables, String workflowId,
      int depth) {
    // Matches the runtime's default nesting limit; fails fast with a clear message instead of
    // a StackOverflowError on pathologically deep definitions.
    Validate.isTrue(depth <= MAX_NESTING_DEPTH,
        "Workflow '%s' exceeds the maximum nesting depth of %s"
            .formatted(workflowId, MAX_NESTING_DEPTH));
    for (Executable executable : executables) {
      if (executable instanceof StepDefinition step) {
        validateStepBehaviour(step, workflowId);
      } else if (executable instanceof WorkflowDefinition nested) {
        validateBehaviourConfiguration(nested.steps(), nested.id(), depth + 1);
      } else if (executable instanceof BlueprintRef) {
        // No behaviour validation needed for refs.
      }
    }
  }

  private void validateStepBehaviour(StepDefinition step, String workflowId) {
    Validate.notNull(step, "Step behaviour must not be null in workflow '%s'"
        .formatted(workflowId));
    Validate.notNull(step.behaviour(),
        "Step behaviour must not be null in step '%s' of workflow '%s'"
            .formatted(step.stepId(), workflowId));
    if (step.behaviour() instanceof BranchBehaviour behaviour) {
      validateBranchBehaviour(step, workflowId, behaviour);
    } else if (step.behaviour() instanceof FailBehaviour behaviour) {
      validateFailBehaviour(step, workflowId, behaviour);
    } else if (step.behaviour() instanceof RetryPreviousBehaviour behaviour) {
      validateRetryPreviousBehaviour(step, workflowId, behaviour);
    }
  }
}
