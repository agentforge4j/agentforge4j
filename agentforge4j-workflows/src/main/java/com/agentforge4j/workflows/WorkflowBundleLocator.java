package com.agentforge4j.workflows;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WorkflowBundleLocator {

  private static final String SHIPPED_WORKFLOWS_PATH = "/shipped-workflows/";
  private static final List<String> SHIPPED_WORKFLOW_IDS = new ArrayList<>();

  static {
    loadShippedWorkflowIds();
  }

  private WorkflowBundleLocator() {
  }

  public static List<String> shippedWorkflowIds() {
    return SHIPPED_WORKFLOW_IDS;
  }

  public static URL locateWorkflowJson(String workflowId) {
    String path = workflowPath(workflowId) + "workflow.json";
    return Validate.notNull(WorkflowBundleLocator.class.getResource(path),
        () -> new IllegalStateException(
            "Missing shipped workflow resource: %s".formatted(path)));
  }

  public static List<String> retrieveWorkflowBundleFiles(String workflowId) {
    String bundlePath = workflowPath(workflowId);
    String indexPath = bundlePath + "index";
    URL resource = WorkflowBundleLocator.class.getResource(indexPath);
    if (resource == null) {
      return List.of();
    }
    try (InputStream stream = resource.openStream()) {
      String[] lines = new String(stream.readAllBytes()).split("\\R");
      return Arrays.stream(lines)
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .map(filename -> bundlePath + filename)
          .toList();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to read shipped workflow index: %s".formatted(indexPath), e);
    }
  }

  public static InputStream openBundleResource(String classpathPath) {
    URL resource = WorkflowBundleLocator.class.getResource(classpathPath);
    if (resource == null) {
      throw new IllegalStateException(
          "Missing shipped workflow bundle resource: " + classpathPath);
    }
    try {
      return resource.openStream();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to open shipped workflow bundle resource: " + classpathPath, e);
    }
  }

  private static void loadShippedWorkflowIds() {
    URL resource = WorkflowBundleLocator.class.getResource(SHIPPED_WORKFLOWS_PATH + "index");
    if (resource != null) {
      try (InputStream stream = resource.openStream()) {
        String[] workflowIds = new String(stream.readAllBytes()).split("\\R");
        SHIPPED_WORKFLOW_IDS.addAll(Arrays.stream(workflowIds).toList().stream()
            .filter(id -> !id.isBlank()).toList());
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to read shipped workflow index resource: %s".formatted(resource), e);
      }
    }
  }

  private static String workflowPath(String workflowId) {
    return SHIPPED_WORKFLOWS_PATH + workflowId + ".workflow/";
  }
}
