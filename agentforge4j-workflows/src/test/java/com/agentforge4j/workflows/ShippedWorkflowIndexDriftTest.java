// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.workflows;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drift guard: fails the build when a committed shipped-workflow {@code index} no longer matches the canonical content
 * derived from the resource tree. Runs in the normal {@code test} phase (part of {@code mvn verify}/{@code install}),
 * so a hand-edited bundle that adds or removes a resource without updating its manifest breaks the build.
 */
class ShippedWorkflowIndexDriftTest {

  @Test
  void committedIndexesAreCanonical() {
    Path resourceDir = ShippedWorkflowIndexGenerator.resolveResourceDir();
    assertThat(Files.isDirectory(resourceDir))
        .as("shipped-workflows resource dir not found at %s "
            + "(run from the agentforge4j-workflows module directory)", resourceDir)
        .isTrue();

    List<String> drifts = ShippedWorkflowIndexGenerator.check(resourceDir);

    assertThat(drifts)
        .as("Shipped-workflow index manifests are out of date — regenerate with "
            + "`mvn -pl agentforge4j-workflows test -Dshipped.index.regenerate=true` "
            + "(or run ShippedWorkflowIndexGenerator.main) and commit the result:%n%s",
            String.join(System.lineSeparator(), drifts))
        .isEmpty();
  }

  /**
   * Regeneration entry point for developers: run with {@code -Dshipped.index.regenerate=true} to rewrite every
   * committed manifest in canonical form. Disabled by default so the normal build never mutates sources.
   */
  @Test
  @EnabledIfSystemProperty(named = "shipped.index.regenerate", matches = "true")
  void regenerateIndexes() {
    ShippedWorkflowIndexGenerator.write(ShippedWorkflowIndexGenerator.resolveResourceDir());
  }

  @Test
  void writeRewritesDriftedManifestsToCanonical(@TempDir Path temp) {
    Path resourceDir = ShippedWorkflowIndexGenerator.resolveResourceDir();
    Path copy = temp.resolve("shipped-workflows");
    copyTree(resourceDir, copy);
    // Corrupt a committed manifest: drop a real entry so it no longer matches the tree.
    Path bundleIndex = copy.resolve("agent-creator.workflow").resolve("index");
    writeFile(bundleIndex, "agents/agent-creator-agent.agent\n");
    assertThat(ShippedWorkflowIndexGenerator.check(copy)).isNotEmpty();

    ShippedWorkflowIndexGenerator.write(copy);

    assertThat(ShippedWorkflowIndexGenerator.check(copy)).isEmpty();
  }

  @Test
  void checkDetectsUnindexedAndStaleEntries(@TempDir Path temp) {
    Path resourceDir = temp.resolve("shipped-workflows");
    writeFile(resourceDir.resolve("index"), "demo\n");
    Path bundle = resourceDir.resolve("demo.workflow");
    writeFile(bundle.resolve("workflow.json"), "{}");
    writeFile(bundle.resolve("brief.artifact.json"), "{}");

    // Manifest omits the present artifact (unindexed) and lists a blueprint that is not on disk (stale).
    writeFile(bundle.resolve("index"), "ghost.blueprint.json\n");

    List<String> drifts = ShippedWorkflowIndexGenerator.check(resourceDir);

    assertThat(drifts).hasSize(1);
    assertThat(drifts.get(0)).contains("demo.workflow index");
    // Canonical content mirrors the tree: the present artifact is indexed, the stale blueprint is not,
    // and workflow.json / index are excluded.
    assertThat(ShippedWorkflowIndexGenerator.bundleIndex(bundle))
        .containsExactly("brief.artifact.json");
  }

  private static void copyTree(Path source, Path target) {
    try (Stream<Path> stream = Files.walk(source)) {
      stream.forEach(path -> {
        Path destination = target.resolve(source.relativize(path).toString());
        try {
          if (Files.isDirectory(path)) {
            Files.createDirectories(destination);
          } else {
            Files.createDirectories(destination.getParent());
            Files.copy(path, destination);
          }
        } catch (IOException e) {
          throw new UncheckedIOException("Failed to copy %s".formatted(path), e);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk %s".formatted(source), e);
    }
  }

  private static void writeFile(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write %s".formatted(file), e);
    }
  }
}
