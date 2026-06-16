// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.workflows;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves shipped workflow bundle resources from the classpath.
 */
public final class WorkflowBundleLocator {

  private static final String SHIPPED_WORKFLOWS_PATH = "/shipped-workflows/";
  private static final List<String> SHIPPED_WORKFLOW_IDS = new ArrayList<>();

  static {
    loadShippedWorkflowIds();
  }

  private WorkflowBundleLocator() {
  }

  /**
   * Returns workflow ids listed in the shipped workflow index.
   *
   * @return immutable list of shipped workflow ids
   */
  public static List<String> shippedWorkflowIds() {
    return List.copyOf(SHIPPED_WORKFLOW_IDS);
  }

  /**
   * Resolves the {@code workflow.json} resource for a shipped workflow id.
   *
   * @param workflowId workflow id listed in the shipped workflow index
   * @return classpath URL of the workflow definition
   * @throws IllegalStateException when the workflow resource is not present
   */
  public static URL locateWorkflowJson(String workflowId) {
    String path = workflowPath(workflowId) + "workflow.json";
    return Validate.notNull(WorkflowBundleLocator.class.getResource(path),
        () -> new IllegalStateException(
            "Missing shipped workflow resource: %s".formatted(path)));
  }

  /**
   * Returns bundle entries listed in a shipped workflow bundle index.
   *
   * @param workflowId workflow id listed in the shipped workflow index
   * @return classpath-relative bundle entry paths, or an empty list when no index is present
   * @throws IllegalStateException when the bundle index cannot be read
   */
  public static List<String> retrieveWorkflowBundleFiles(String workflowId) {
    String bundlePath = workflowPath(workflowId);
    String indexPath = bundlePath + "index";
    URL resource = WorkflowBundleLocator.class.getResource(indexPath);
    if (resource == null) {
      return List.of();
    }
    try (InputStream stream = resource.openStream()) {
      String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
      return Arrays.stream(lines)
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .toList();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to read shipped workflow index: %s".formatted(indexPath), e);
    }
  }

  /**
   * Opens a shipped workflow bundle resource.
   *
   * @param classpathPath classpath path to a bundle entry
   * @return open input stream for the requested resource
   * @throws IllegalStateException when the resource does not exist
   * @throws UncheckedIOException  when the resource exists but cannot be opened
   */
  public static InputStream openBundleResource(String classpathPath, String workflowId) {
    URL resource = WorkflowBundleLocator.class.getResource(classpathPath);
    if (resource == null) {
      throw new IllegalStateException(
          "Missing shipped workflow bundle resource: %s in %s".formatted(classpathPath,
              workflowId));
    }
    try {
      return resource.openStream();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to open shipped workflow bundle resource: %s in %s".formatted(classpathPath,
              workflowId), e);
    }
  }

  /**
   * Classpath prefix for the given shipped workflow id (trailing slash), under {@code /shipped-workflows/}.
   *
   * @param workflowId id from {@link #shippedWorkflowIds()}
   * @return path such as {@code /shipped-workflows/my.workflow/}
   */
  public static String workflowPath(String workflowId) {
    return SHIPPED_WORKFLOWS_PATH + workflowId + ".workflow/";
  }

  private static void loadShippedWorkflowIds() {
    URL resource = WorkflowBundleLocator.class.getResource(SHIPPED_WORKFLOWS_PATH + "index");
    if (resource != null) {
      try (InputStream stream = resource.openStream()) {
        String[] workflowIds = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
        SHIPPED_WORKFLOW_IDS.addAll(Arrays.stream(workflowIds).toList().stream()
            .filter(id -> !id.isBlank()).toList());
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to read shipped workflow index resource: %s".formatted(resource), e);
      }
    }
  }
}
