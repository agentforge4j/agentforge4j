// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.workflows;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates and verifies the {@code index} manifest files that enumerate shipped-workflow bundle resources.
 *
 * <p>The classpath cannot list directory contents inside a packaged jar, so each shipped-workflow bundle ships a
 * plain-text {@code index} that {@link WorkflowBundleLocator} reads to discover the bundle's resources. Those manifests
 * were previously hand-maintained, which let them drift from the actual resource tree (a new file silently not loaded,
 * a stale entry failing the load). This type is the single source of truth for the canonical manifest content: it both
 * {@link #write(Path) writes} the manifests and {@link #check(Path) verifies} that the committed manifests match the
 * tree, so a drift guard test can fail the build when they diverge.
 *
 * <p>Manifest contract (mirrors the loader): a bundle {@code index} lists the bundle-root resource files ending in
 * {@code .artifact.json}, {@code .blueprint.json}, or {@code .step.prompt.md}, plus an {@code agents/<name>.agent}
 * entry for each agent directory. It excludes {@code workflow.json} (resolved directly), the {@code index} file
 * itself, and the files inside each {@code .agent} directory (resolved from the directory entry). The root
 * {@code index} lists each {@code <id>.workflow} directory as {@code <id>}. All entries are sorted ascending by their
 * full string so the output is deterministic and order is never hand-authored.
 *
 * <p>This is build/test tooling and lives in test scope: it is never shipped in the runtime jar.
 */
final class ShippedWorkflowIndexGenerator {

  static final String DEFAULT_RESOURCE_DIR = "src/main/resources/shipped-workflows";
  static final String RESOURCE_DIR_PROPERTY = "shipped.workflows.dir";

  private static final String WORKFLOW_DIR_SUFFIX = ".workflow";
  private static final String AGENTS_DIR = "agents";
  private static final String AGENT_DIR_SUFFIX = ".agent";
  private static final String INDEX_FILE = "index";
  private static final List<String> BUNDLE_ROOT_SUFFIXES =
      List.of(".artifact.json", ".blueprint.json", ".step.prompt.md");

  private ShippedWorkflowIndexGenerator() {
  }

  /**
   * Regenerates every shipped-workflow {@code index} under the resource directory. Resolves the directory from the
   * first argument, else the {@code shipped.workflows.dir} system property, else {@link #DEFAULT_RESOURCE_DIR}.
   *
   * @param args optional single argument naming the {@code shipped-workflows} directory
   */
  public static void main(String[] args) {
    Path dir = args.length > 0 ? Path.of(args[0]) : resolveResourceDir();
    write(dir);
    System.out.println("Regenerated shipped-workflow indexes under " + dir.toAbsolutePath());
  }

  /**
   * Resolves the {@code shipped-workflows} resource directory from the {@code shipped.workflows.dir} system property,
   * defaulting to {@link #DEFAULT_RESOURCE_DIR} (relative to the module base directory).
   *
   * @return the resource directory path
   */
  static Path resolveResourceDir() {
    return Path.of(System.getProperty(RESOURCE_DIR_PROPERTY, DEFAULT_RESOURCE_DIR));
  }

  /**
   * Computes the canonical root index lines: each {@code <id>.workflow} directory mapped to {@code <id>}, sorted
   * ascending.
   *
   * @param resourceDir the {@code shipped-workflows} directory
   * @return sorted bundle ids
   */
  static List<String> rootIndex(Path resourceDir) {
    Validate.requireDirectory(resourceDir, "shipped-workflows directory must exist: %s".formatted(resourceDir));
    return listDirectories(resourceDir).stream()
        .map(path -> path.getFileName().toString())
        .filter(name -> name.endsWith(WORKFLOW_DIR_SUFFIX))
        .map(name -> name.substring(0, name.length() - WORKFLOW_DIR_SUFFIX.length()))
        .sorted()
        .toList();
  }

  /**
   * Computes the canonical bundle index lines for one {@code <id>.workflow} directory: bundle-root resource files
   * matching the indexed suffixes plus {@code agents/<name>.agent} directory entries, all sorted ascending by their
   * full string.
   *
   * @param bundleDir the {@code <id>.workflow} directory
   * @return sorted bundle entry paths
   */
  static List<String> bundleIndex(Path bundleDir) {
    Validate.requireDirectory(bundleDir, "workflow bundle directory must exist: %s".formatted(bundleDir));
    List<String> entries = new ArrayList<>();
    for (Path file : listRegularFiles(bundleDir)) {
      String name = file.getFileName().toString();
      if (BUNDLE_ROOT_SUFFIXES.stream().anyMatch(name::endsWith)) {
        entries.add(name);
      }
    }
    Path agentsDir = bundleDir.resolve(AGENTS_DIR);
    if (Files.isDirectory(agentsDir)) {
      for (Path agentDir : listDirectories(agentsDir)) {
        String name = agentDir.getFileName().toString();
        if (name.endsWith(AGENT_DIR_SUFFIX)) {
          entries.add(AGENTS_DIR + "/" + name);
        }
      }
    }
    return entries.stream().sorted().toList();
  }

  /**
   * Serialises index lines to manifest text: LF-separated with a single trailing newline, or an empty string when
   * there are no entries.
   *
   * @param lines index lines
   * @return manifest text
   */
  static String serialize(List<String> lines) {
    if (lines.isEmpty()) {
      return "";
    }
    return String.join("\n", lines) + "\n";
  }

  /**
   * Verifies every committed {@code index} against the canonical content derived from the tree.
   *
   * @param resourceDir the {@code shipped-workflows} directory
   * @return one human-readable drift description per manifest that differs from canonical; empty when all match
   */
  static List<String> check(Path resourceDir) {
    List<String> drifts = new ArrayList<>();
    checkOne(resourceDir.resolve(INDEX_FILE), serialize(rootIndex(resourceDir)), "root index", drifts);
    for (String workflowId : rootIndex(resourceDir)) {
      Path bundleDir = resourceDir.resolve(workflowId + WORKFLOW_DIR_SUFFIX);
      checkOne(bundleDir.resolve(INDEX_FILE), serialize(bundleIndex(bundleDir)),
          "%s%s index".formatted(workflowId, WORKFLOW_DIR_SUFFIX), drifts);
    }
    return drifts;
  }

  /**
   * (Re)writes every {@code index} under the resource directory with its canonical content.
   *
   * @param resourceDir the {@code shipped-workflows} directory
   */
  static void write(Path resourceDir) {
    writeOne(resourceDir.resolve(INDEX_FILE), serialize(rootIndex(resourceDir)));
    for (String workflowId : rootIndex(resourceDir)) {
      Path bundleDir = resourceDir.resolve(workflowId + WORKFLOW_DIR_SUFFIX);
      writeOne(bundleDir.resolve(INDEX_FILE), serialize(bundleIndex(bundleDir)));
    }
  }

  private static void checkOne(Path indexFile, String expected, String label, List<String> drifts) {
    String actual = readNormalised(indexFile);
    if (!expected.equals(actual)) {
      drifts.add("%s is out of date:%n--- committed ---%n%s--- expected ---%n%s"
          .formatted(label, actual, expected));
    }
  }

  private static String readNormalised(Path indexFile) {
    if (!Files.exists(indexFile)) {
      return "";
    }
    try {
      return Files.readString(indexFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read index: %s".formatted(indexFile), e);
    }
  }

  private static void writeOne(Path indexFile, String content) {
    try {
      Files.writeString(indexFile, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write index: %s".formatted(indexFile), e);
    }
  }

  private static List<Path> listDirectories(Path dir) {
    try (Stream<Path> stream = Files.list(dir)) {
      return stream.filter(Files::isDirectory).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list directory: %s".formatted(dir), e);
    }
  }

  private static List<Path> listRegularFiles(Path dir) {
    try (Stream<Path> stream = Files.list(dir)) {
      return stream.filter(Files::isRegularFile).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list directory: %s".formatted(dir), e);
    }
  }
}
