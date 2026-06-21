// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves shipped workflow bundle resources from the classpath.
 *
 * <p>Resources are resolved through {@link ClassLoader#getResource(String)} on this class's own
 * loader, not {@link Class#getResource(String)}: the shipped catalog ships as a separate
 * module/jar (the independently-versioned workflow catalog), so its resources are not in this
 * loader's own module. The {@code shipped-workflows} root contains a hyphen, so it does not map to
 * a Java package and is therefore not JPMS-encapsulated — it is discoverable across the class path
 * and the module path. When no catalog is present on the classpath the shipped index is simply
 * absent and {@link #shippedWorkflowIds()} is empty.
 */
public final class WorkflowBundleLocator {

  private static final String SHIPPED_WORKFLOWS_PATH = "shipped-workflows/";
  private static final ClassLoader LOADER = WorkflowBundleLocator.class.getClassLoader();
  private static final List<String> SHIPPED_WORKFLOW_IDS = loadShippedWorkflowIds();

  private WorkflowBundleLocator() {
  }

  /**
   * Returns workflow ids listed in the shipped workflow index.
   *
   * @return immutable list of shipped workflow ids, empty when no catalog is present
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
    return Validate.notNull(LOADER.getResource(path),
        () -> new IllegalStateException("Missing shipped workflow resource: %s".formatted(path)));
  }

  /**
   * Returns bundle entries listed in a shipped workflow bundle index.
   *
   * @param workflowId workflow id listed in the shipped workflow index
   * @return classpath-relative bundle entry paths, or an empty list when no index is present
   * @throws IllegalStateException when the bundle index cannot be read
   */
  public static List<String> retrieveWorkflowBundleFiles(String workflowId) {
    String indexPath = workflowPath(workflowId) + "index";
    URL resource = LOADER.getResource(indexPath);
    if (resource == null) {
      return List.of();
    }
    try (InputStream stream = resource.openStream()) {
      return readNonBlankLines(stream);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read shipped workflow index: %s".formatted(indexPath), e);
    }
  }

  /**
   * Opens a shipped workflow bundle resource.
   *
   * @param classpathPath classpath path to a bundle entry (no leading slash)
   * @param workflowId    owning workflow id, used in error messages
   * @return open input stream for the requested resource
   * @throws IllegalStateException when the resource does not exist
   * @throws UncheckedIOException  when the resource exists but cannot be opened
   */
  public static InputStream openBundleResource(String classpathPath, String workflowId) {
    URL resource = LOADER.getResource(classpathPath);
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
   * Classpath prefix for the given shipped workflow id (trailing slash), under
   * {@code shipped-workflows/}. The prefix has no leading slash so it composes with
   * {@link ClassLoader#getResource(String)}. The id is validated against path traversal and
   * separators so a malicious or malformed catalog index cannot steer resolution outside the
   * {@code shipped-workflows/} root — mirroring the agent bundle-entry guard. (Not exploitable for a
   * trusted, build-time catalog, but the same surface admits third-party catalogs on the roadmap,
   * and {@code ClassLoader.getResource} can normalise {@code ..} on exploded-directory classpath
   * entries.)
   *
   * @param workflowId id from {@link #shippedWorkflowIds()}; must not be blank, contain {@code ..},
   *                   or contain path separators
   * @return path such as {@code shipped-workflows/my.workflow/}
   */
  public static String workflowPath(String workflowId) {
    validateWorkflowId(workflowId);
    return SHIPPED_WORKFLOWS_PATH + workflowId + ".workflow/";
  }

  private static void validateWorkflowId(String workflowId) {
    Validate.notBlank(workflowId, "Workflow id must not be blank");
    Validate.isTrue(!workflowId.contains(".."),
        "Workflow id must not contain path traversal: %s".formatted(workflowId));
    Validate.isTrue(!workflowId.contains("/") && !workflowId.contains("\\"),
        "Workflow id must not contain path separators: %s".formatted(workflowId));
  }

  private static List<String> loadShippedWorkflowIds() {
    URL resource = LOADER.getResource(SHIPPED_WORKFLOWS_PATH + "index");
    if (resource == null) {
      return List.of();
    }
    try (InputStream stream = resource.openStream()) {
      return readNonBlankLines(stream);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read shipped workflow index resource: %s".formatted(resource), e);
    }
  }

  private static List<String> readNonBlankLines(InputStream stream) throws IOException {
    String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      String trimmed = line.trim();
      if (!trimmed.isBlank()) {
        result.add(trimmed);
      }
    }
    return result;
  }
}
