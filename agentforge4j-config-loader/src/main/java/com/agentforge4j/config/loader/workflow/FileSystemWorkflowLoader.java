// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.config.loader.agent.FileSystemAgentLoader;
import com.agentforge4j.config.loader.prompt.FileSystemAgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads workflows from a workflows root directory: each immediate child directory must be named
 * {@code <workflowId>.workflow} and must contain {@code workflow.json}.
 */
public final class FileSystemWorkflowLoader extends BaseWorkflowBundleLoader
    implements WorkflowDirectoryLoader {

  private static final System.Logger LOG = System.getLogger(
      FileSystemWorkflowLoader.class.getName());

  public FileSystemWorkflowLoader(ObjectMapper workflowMapper) {
    super(workflowMapper, "FileSystemWorkflowLoader", WorkflowSource.CUSTOM);
  }

  @Override
  public WorkflowDirectoryLoad loadWorkflows(Path root) {
    return super.loadWorkflows(validateDirectory(root));
  }

  @Override
  protected void log(System.Logger.Level level, String message, Object... args) {
    LOG.log(level, message, args);
  }

  @Override
  protected List<String> getWorkflowIdList(WorkflowBundleLoadContext context) {
    Path workflowsRoot = context.requireWorkflowRoot();
    try (Stream<Path> entries = Files.list(workflowsRoot)) {
      return entries
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(WORKFLOW_DIR_SUFFIX))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read workflows directory: " + workflowsRoot, e);
    }
  }

  @Override
  protected WorkflowDefinition retrieveWorkflowDefinition(WorkflowBundleLoadContext context,
      String workflowId,
      Map<String, AgentDefinition> bundledAgents) {
    Path workflowBundleDir = resolveWorkflowBundleDir(context.requireWorkflowRoot(), workflowId);
    Path jsonFile = Validate.requireWithinBase(workflowBundleDir, WORKFLOW_DEFINITION_FILE,
        "Path escapes base directory: %s".formatted(WORKFLOW_DEFINITION_FILE));
    try {
      return workflowMapper.readValue(jsonFile.toFile(), WorkflowDefinition.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse workflow file: %s".formatted(jsonFile), e);
    }
  }

  @Override
  protected List<String> retrieveFilesFromIndex(WorkflowBundleLoadContext context,
      String workflowId) {
    Path workflowBundleDir = resolveWorkflowBundleDir(context.requireWorkflowRoot(), workflowId);
    try (Stream<Path> entries = Files.list(workflowBundleDir)) {
      return entries
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .sorted(Comparator.naturalOrder())
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to list workflow files in: %s".formatted(workflowBundleDir), e);
    }
  }

  @Override
  protected <T> T loadItem(WorkflowBundleLoadContext context, String path, String workflowId,
      String derivedId, String type,
      Class<T> clazz) {
    Path workflowBundleDir = resolveWorkflowBundleDir(context.requireWorkflowRoot(), workflowId);
    Path resolved = Validate.requireWithinBase(workflowBundleDir, path,
        "Path escapes workflow bundle directory: %s".formatted(path));
    try {
      return workflowMapper.readValue(resolved.toFile(), clazz);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to parse %s file '%s' in workflow '%s'"
              .formatted(type, path, workflowId), e);
    }
  }

  @Override
  protected String loadRawContent(WorkflowBundleLoadContext context, String filePath,
      String workflowId) {
    Path workflowBundleDir = resolveWorkflowBundleDir(context.requireWorkflowRoot(), workflowId);
    return new PromptLoader().loadPrompt(workflowBundleDir, filePath);
  }

  @Override
  protected Map<String, AgentDefinition> loadBundledAgentsFromBundle(
      WorkflowBundleLoadContext context, String workflowId,
      List<String> bundleFiles) {
    Validate.notBlank(workflowId, "Current workflow id must be set before loading agents");
    Path agentsRoot = resolveWorkflowBundleDir(context.requireWorkflowRoot(), workflowId)
        .resolve(AGENTS_DIR).normalize();
    if (!Files.isDirectory(agentsRoot)) {
      return Map.of();
    }
    FileSystemAgentLoader localLoader = new FileSystemAgentLoader(
        workflowMapper,
        new FileSystemAgentPromptResolver(new PromptLoader()),
        agentsRoot);
    return localLoader.loadAgents();
  }

  private Path resolveWorkflowBundleDir(Path workflowsRoot, String workflowId) {
    return Validate.requireWithinBase(workflowsRoot, workflowId + WORKFLOW_DIR_SUFFIX,
        "Path escapes workflows directory: %s".formatted(workflowId + WORKFLOW_DIR_SUFFIX));
  }

  private static Path validateDirectory(Path directory) {
    LOG.log(System.Logger.Level.DEBUG, "Scanning workflows directory path={0}", directory);
    try {
      if (!Files.exists(directory)) {
        Files.createDirectories(directory);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to create workflows directory: %s".formatted(directory), e);
    }
    return Validate.requireDirectory(directory,
        "Workflows directory does not exist: %s".formatted(directory));
  }
}
