// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the gate over real classloaders built on temp directories (the exploded layout), across
 * the content-present predicate, the version bounds, and the schema-version check. Each case uses an
 * isolated {@link URLClassLoader} (null parent) so the test's own {@code shipped-workflows/} fixture
 * never bleeds in.
 */
class CatalogCompatibilityGateTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int SUPPORTED = 1;

  @Test
  void noCatalogPresent_isNoOp(@TempDir Path root) throws IOException {
    assertThatCode(() -> gate(root, "0.0.1-SNAPSHOT").enforce()).doesNotThrowAnyException();
  }

  @Test
  void emptyIndex_isNoOp(@TempDir Path root) throws IOException {
    writeIndex(root, "\n   \n");
    assertThatCode(() -> gate(root, "0.0.1-SNAPSHOT").enforce()).doesNotThrowAnyException();
  }

  @Test
  void contentPresentButManifestMissing_failsFast(@TempDir Path root) throws IOException {
    writeIndex(root, "sample\n");
    assertThatThrownBy(() -> gate(root, "0.0.1-SNAPSHOT").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("manifest");
  }

  @Test
  void snapshotFrameworkSatisfiesReleaseMinimum(@TempDir Path root) throws IOException {
    // A1 named case: running 0.0.1-SNAPSHOT accepts a manifest whose minimumAgentForge4jVersion = 0.0.1.
    writeCatalog(root, "0.0.1", null, 1);
    assertThatCode(() -> gate(root, "0.0.1-SNAPSHOT").enforce()).doesNotThrowAnyException();
  }

  @Test
  void frameworkBelowMinimum_failsFast(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.2.0", null, 1);
    assertThatThrownBy(() -> gate(root, "0.1.0").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining(">=");
  }

  @Test
  void frameworkAboveMaximum_failsFast(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.0.1", "0.1.0", 1);
    assertThatThrownBy(() -> gate(root, "0.2.0").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("<=");
  }

  @Test
  void unboundedMaximum_accepts(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.0.1", null, 1);
    assertThatCode(() -> gate(root, "9.9.9").enforce()).doesNotThrowAnyException();
  }

  @Test
  void unsupportedSchemaVersion_failsFast(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.0.1", null, 2);
    assertThatThrownBy(() -> gate(root, "0.0.1-SNAPSHOT").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("schema version");
  }

  private CatalogCompatibilityGate gate(Path catalogRoot, String frameworkVersion)
      throws IOException {
    URL[] urls = {catalogRoot.toUri().toURL()};
    return new CatalogCompatibilityGate(
        new URLClassLoader(urls, null), frameworkVersion, SUPPORTED, MAPPER);
  }

  private void writeCatalog(Path root, String min, String max, int schema) throws IOException {
    writeIndex(root, "sample\n");
    String maxField = max == null ? "null" : "\"" + max + "\"";
    writeManifest(root, ("{\"catalogVersion\":\"1.0.0\",\"minimumAgentForge4jVersion\":\"%s\","
        + "\"maximumAgentForge4jVersion\":%s,\"workflowSchemaVersion\":%d}")
        .formatted(min, maxField, schema));
  }

  private void writeIndex(Path root, String content) throws IOException {
    Path dir = Files.createDirectories(root.resolve("shipped-workflows"));
    Files.writeString(dir.resolve("index"), content, StandardCharsets.UTF_8);
  }

  private void writeManifest(Path root, String json) throws IOException {
    Path dir = Files.createDirectories(root.resolve("shipped-workflows"));
    Files.writeString(dir.resolve("agentforge4j-catalog.json"), json, StandardCharsets.UTF_8);
  }
}
