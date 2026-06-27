// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader;

import com.agentforge4j.config.loader.agent.ClasspathAgentLoader;
import com.agentforge4j.config.loader.catalog.CatalogCompatibilityGate;
import com.agentforge4j.config.loader.validation.WorkflowValidator;
import com.agentforge4j.config.loader.workflow.ClasspathWorkflowLoader;
import com.agentforge4j.config.loader.workflow.WorkflowDirectoryLoader;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.exception.DuplicateAgentIdException;
import com.agentforge4j.core.exception.DuplicateWorkflowIdException;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and validates agent and workflow configuration from filesystem and optional classpath
 * bundles.
 */
public final class AgentForgeLoader {

  private static final System.Logger LOG = System.getLogger(AgentForgeLoader.class.getName());

  private final AgentLoader agentLoader;
  private final WorkflowDirectoryLoader workflowDirectoryLoader;

  public AgentForgeLoader(AgentLoader agentLoader) {
    this(agentLoader,
        root -> {
          throw new UnsupportedOperationException(
              "Path-scoped workflow loading is unavailable; construct AgentForgeLoader with "
                  + "WorkflowDirectoryLoader to use loadWorkflowDirectory/loadWorkflows.");
        });
  }

  public AgentForgeLoader(AgentLoader agentLoader,
      WorkflowDirectoryLoader workflowDirectoryLoader) {
    this.agentLoader = Validate.notNull(agentLoader, "agentLoader must not be null");
    this.workflowDirectoryLoader = Validate.notNull(workflowDirectoryLoader,
        "workflowDirectoryLoader must not be null");
  }

  private static void loadWorkflowsFromClasspath(
      Optional<ClasspathWorkflowLoader> classpathWorkflowLoader,
      Map<String, AgentDefinition> agents, Map<String, WorkflowDefinition> workflows) {
    classpathWorkflowLoader.ifPresent(cwl -> {
      // Fail fast if a shipped catalog is present but incompatible; a no-op when none is present.
      CatalogCompatibilityGate.defaults().enforce();
      WorkflowDirectoryLoad shipped = cwl.loadWorkflows();
      mergeBundledAgentsStrict(agents, shipped.bundledAgents(),
          "classpath shipped workflow bundles");
      mergeWorkflowsStrict(workflows, shipped.workflows(), "classpath shipped workflows");
    });
  }

  private static void loadAgentsFromClasspath(Optional<ClasspathAgentLoader> classpathAgentLoader,
      Map<String, AgentDefinition> agents) {
    classpathAgentLoader.ifPresent(cal ->
        mergeBundledAgentsStrict(agents, cal.loadAgents(), "classpath shipped agents"));
  }

  /**
   * Merges {@code additions} into {@code target}, failing if any agent id in {@code additions} is
   * already present in {@code target}.
   *
   * @param target               destination map to update
   * @param additions            agents to merge into {@code target}
   * @param additionsDescription description used in duplicate-id errors
   * @throws DuplicateAgentIdException when any id from {@code additions} already exists in
   *                                   {@code target}
   */
  public static void mergeBundledAgentsStrict(Map<String, AgentDefinition> target,
      Map<String, AgentDefinition> additions,
      String additionsDescription) {
    if (additions.isEmpty()) {
      return;
    }
    List<String> duplicateLines = additions.keySet().stream()
        .filter(target::containsKey)
        .map(id -> "agent id '%s' is already registered; %s conflicts with existing entry"
            .formatted(id, additionsDescription))
        .toList();
    Validate.isTrue(duplicateLines.isEmpty(), () -> new DuplicateAgentIdException(duplicateLines));
    target.putAll(additions);
  }

  /**
   * Merges workflow definitions into an existing map while rejecting duplicate workflow ids.
   *
   * @param target               destination map to update
   * @param additions            workflows to merge into {@code target}
   * @param additionsDescription description used in duplicate-id errors
   * @throws DuplicateWorkflowIdException when any id from {@code additions} already exists in
   *                                      {@code target}
   */
  public static void mergeWorkflowsStrict(Map<String, WorkflowDefinition> target,
      Map<String, WorkflowDefinition> additions,
      String additionsDescription) {
    if (additions.isEmpty()) {
      return;
    }
    List<String> duplicateLines = additions.keySet().stream()
        .filter(target::containsKey)
        .map(id -> "workflow id '%s' is already registered; %s conflicts with existing entry"
            .formatted(id, additionsDescription))
        .toList();
    Validate.isTrue(duplicateLines.isEmpty(),
        () -> new DuplicateWorkflowIdException(duplicateLines));
    target.putAll(additions);
  }

  private void runValidation(String name, Runnable action) {
    LOG.log(System.Logger.Level.INFO, "Validating {0} - start", name);
    try {
      action.run();
      LOG.log(System.Logger.Level.INFO, "Validating {0} - complete", name);
    } catch (RuntimeException ex) {
      LOG.log(System.Logger.Level.ERROR, "Validation failed validation={0}, message={1}", name,
          ex.getMessage());
      throw ex;
    }
  }


  /**
   * Loads configured agents and workflows from the provided sources and validates
   * cross-references.
   *
   * @param agentsDir               presence opts in to filesystem agent loading; the directory is
   *                                determined by the injected {@link AgentLoader}
   * @param workflowsDir            optional filesystem directory containing workflow bundles
   * @param classpathAgentLoader    optional loader for shipped agents
   * @param classpathWorkflowLoader optional loader for shipped workflows
   * @return loaded and validated configuration snapshot
   * @throws RuntimeException when loading fails, duplicate ids are found, or validation fails
   */
  public LoadedConfiguration load(Optional<Path> agentsDir,
      Optional<Path> workflowsDir,
      Optional<ClasspathAgentLoader> classpathAgentLoader,
      Optional<ClasspathWorkflowLoader> classpathWorkflowLoader) {
    Map<String, AgentDefinition> agents = new LinkedHashMap<>();
    Map<String, WorkflowDefinition> workflows = new LinkedHashMap<>();

    loadAgentDir(agentsDir, agents);
    loadWorkflowDir(workflowsDir, agents, workflows);
    loadAgentsFromClasspath(classpathAgentLoader, agents);
    loadWorkflowsFromClasspath(classpathWorkflowLoader, agents, workflows);

    LOG.log(System.Logger.Level.INFO, "Loaded configuration agents={0}, workflows={1}",
        agents.size(), workflows.size());
    validate(workflows, agents);
    return new LoadedConfiguration(Map.copyOf(agents), Map.copyOf(workflows));
  }

  private void loadWorkflowDir(Optional<Path> workflowsDir, Map<String, AgentDefinition> agents,
      Map<String, WorkflowDefinition> workflows) {
    workflowsDir.ifPresent(dir -> {
      WorkflowDirectoryLoad fromDir = workflowDirectoryLoader.loadWorkflows(dir);
      mergeBundledAgentsStrict(agents, fromDir.bundledAgents(),
          "workflow bundles under: %s".formatted(dir));
      mergeWorkflowsStrict(workflows, fromDir.workflows(),
          "workflow bundles under: %s".formatted(dir));
    });
  }

  private void loadAgentDir(Optional<Path> agentsDir, Map<String, AgentDefinition> agents) {
    // agentsDir presence only opts in; the scan root is fixed on the injected AgentLoader.
    agentsDir.ifPresent(agentsDirPresent -> mergeBundledAgentsStrict(agents, loadAgents(),
        "agents directory"));
  }

  /**
   * Runs the full workflow validation suite.
   *
   * @param workflows workflows to validate
   * @param agents    agents available to workflow steps
   * @throws RuntimeException when any validation rule fails
   */
  public void validate(Map<String, WorkflowDefinition> workflows,
      Map<String, AgentDefinition> agents) {
    WorkflowValidator validator = new WorkflowValidator();
    runValidation("workflow refs", () -> validator.validateWorkflowRefs(workflows));
    runValidation("blueprint refs", () -> validator.validateBlueprintRefs(workflows));
    runValidation("agent refs", () -> validator.validateAgentRefs(workflows, agents));
    runValidation("artifact refs", () -> validator.validateArtifactRefs(workflows));
    runValidation("circular refs", () -> validator.validateCircularRefs(workflows));
    runValidation("reachable step ids", () -> validator.validateReachableStepIdUniqueness(workflows));
    runValidation("retry refs", () -> validator.validateRetryStepRefs(workflows));
    runValidation("requirement refs", () -> validator.validateRequirements(workflows));
    runValidation("validate contracts", () -> validator.validateValidateBehaviourContracts(workflows));
  }

  /**
   * Loads agent definitions from a filesystem directory.
   *
   * @return loaded agents keyed by id
   */
  public Map<String, AgentDefinition> loadAgents() {
    return agentLoader.loadAgents();
  }

  /**
   * Loads workflow definitions from a filesystem directory.
   *
   * @param dir directory containing workflow bundles
   * @return loaded workflows keyed by id
   */
  public Map<String, WorkflowDefinition> loadWorkflows(Path dir) {
    return loadWorkflowDirectory(dir).workflows();
  }

  /**
   * Loads workflows and workflow-bundled agents from a filesystem directory.
   *
   * @param dir directory containing workflow bundles
   * @return loaded workflows together with bundled agents
   */
  public WorkflowDirectoryLoad loadWorkflowDirectory(Path dir) {
    Validate.notNull(dir, "dir must not be null");
    return workflowDirectoryLoader.loadWorkflows(dir);
  }
}
