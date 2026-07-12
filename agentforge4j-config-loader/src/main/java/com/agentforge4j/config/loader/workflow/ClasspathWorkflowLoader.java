// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.config.loader.WorkflowLoader;
import com.agentforge4j.config.loader.agent.ClasspathAgentLoader;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Loads shipped workflow bundles from classpath resources.
 */
public final class ClasspathWorkflowLoader extends BaseWorkflowBundleLoader implements WorkflowLoader {

  private static final System.Logger LOG = System.getLogger(
      ClasspathWorkflowLoader.class.getName());

  public ClasspathWorkflowLoader(ObjectMapper workflowMapper) {
    super(workflowMapper, "ClasspathWorkflowLoader", WorkflowSource.SHIPPED);
  }

  @Override
  public WorkflowDirectoryLoad loadWorkflows() {
    return loadWorkflows(WorkflowBundleLoadContext.classpath());
  }

  @Override
  protected WorkflowDefinition retrieveWorkflowDefinition(WorkflowBundleLoadContext context,
      String workflowId,
      Map<String, AgentDefinition> bundledAgents) {
    URL jsonUrl = WorkflowBundleLocator.locateWorkflowJson(workflowId);
    try (InputStream stream = jsonUrl.openStream()) {
      return toWorkflowDefinition(workflowMapper.readTree(stream), jsonUrl.toString());
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read shipped workflow: " + jsonUrl, e);
    }
  }

  @Override
  protected Map<String, AgentDefinition> loadBundledAgentsFromBundle(
      WorkflowBundleLoadContext context, String workflowId,
      List<String> bundleFiles) {
    ClasspathAgentLoader agentLoader = new ClasspathAgentLoader(workflowMapper,
        WorkflowBundleLocator.workflowPath(workflowId) + "agents/"
    );
    return agentLoader.loadAgents(bundleFiles);
  }

  @Override
  protected String loadRawContent(WorkflowBundleLoadContext context, String classpathPath,
      String workflowId) {
    try (InputStream stream = WorkflowBundleLocator.openBundleResource(
        WorkflowBundleLocator.workflowPath(workflowId) + classpathPath, workflowId)) {
      return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read resource '%s' in shipped workflow '%s'"
              .formatted(classpathPath, workflowId), e);
    }
  }

  @Override
  protected void log(Level level, String message, Object... args) {
    LOG.log(level, message, args);
  }

  @Override
  protected List<String> getWorkflowIdList(WorkflowBundleLoadContext context) {
    return WorkflowBundleLocator.shippedWorkflowIds().stream()
        .map(id -> id + WORKFLOW_DIR_SUFFIX)
        .toList();
  }

  @Override
  protected List<String> retrieveFilesFromIndex(WorkflowBundleLoadContext context,
      String workflowId) {
    return WorkflowBundleLocator.retrieveWorkflowBundleFiles(workflowId);
  }

  @Override
  protected <T> T loadItem(WorkflowBundleLoadContext context, String classpathPath,
      String workflowId, String derivedId, String type,
      Class<T> clazz) {
    try (InputStream stream = WorkflowBundleLocator.openBundleResource(
        WorkflowBundleLocator.workflowPath(workflowId) + classpathPath, workflowId)) {
      return workflowMapper.readValue(stream, clazz);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read %s '%s' in shipped workflow '%s'"
              .formatted(type, classpathPath, workflowId), e);
    }
  }
}
