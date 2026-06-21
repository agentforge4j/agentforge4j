// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static com.agentforge4j.schema.WorkflowSchemaVersion.SUPPORTED_WORKFLOW_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.config.loader.catalog.CatalogCompatibilityException;
import com.agentforge4j.config.loader.catalog.CatalogCompatibilityGate;
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
 * Compatibility coverage for the real shipped catalog manifest.
 *
 * <p>The accept cases run the gate against this module's <em>real</em> {@code agentforge4j-catalog.json}
 * — proving the genuine shipped catalog loads under the running framework — including the A1 case
 * (a {@code 0.0.1-SNAPSHOT} framework satisfies the catalog's {@code 0.0.1} minimum). The reject
 * cases drive catalog-shaped content with a deliberately incompatible manifest. (The gate mechanism
 * itself is also unit-tested in {@code agentforge4j-config-loader}.)
 */
class CatalogManifestCompatibilityTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ClassLoader CATALOG_LOADER =
      CatalogManifestCompatibilityTest.class.getClassLoader();

  @Test
  void realCatalogIsAcceptedByRunningFramework() {
    assertThatCode(() -> CatalogCompatibilityGate.defaults().enforce())
        .doesNotThrowAnyException();
  }

  @Test
  void snapshotFrameworkAcceptsRealCatalogMinimum() {
    // A1: running 0.0.1-SNAPSHOT accepts the real catalog's minimumAgentForge4jVersion = 0.0.1.
    assertThatCode(() -> new CatalogCompatibilityGate(
        CATALOG_LOADER, "0.0.1-SNAPSHOT", SUPPORTED_WORKFLOW_SCHEMA_VERSION, MAPPER).enforce())
        .doesNotThrowAnyException();
  }

  @Test
  void manifestAbsentWhileContentPresentIsRejected(@TempDir Path root) throws IOException {
    writeIndex(root);
    assertThatThrownBy(() -> gate(root, "0.0.1-SNAPSHOT").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("manifest");
  }

  @Test
  void minimumVersionTooHighIsRejected(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.2.0", null, 1);
    assertThatThrownBy(() -> gate(root, "0.1.0").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining(">=");
  }

  @Test
  void workflowSchemaMismatchIsRejected(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.0.1", null, SUPPORTED_WORKFLOW_SCHEMA_VERSION + 1);
    assertThatThrownBy(() -> gate(root, "0.0.1-SNAPSHOT").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("schema version");
  }

  @Test
  void maximumVersionExceededIsRejected(@TempDir Path root) throws IOException {
    writeCatalog(root, "0.0.1", "0.1.0", 1);
    assertThatThrownBy(() -> gate(root, "0.2.0").enforce())
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("<=");
  }

  private CatalogCompatibilityGate gate(Path catalogRoot, String frameworkVersion)
      throws IOException {
    URL[] urls = {catalogRoot.toUri().toURL()};
    return new CatalogCompatibilityGate(
        new URLClassLoader(urls, null), frameworkVersion, SUPPORTED_WORKFLOW_SCHEMA_VERSION, MAPPER);
  }

  private void writeCatalog(Path root, String min, String max, int schema) throws IOException {
    writeIndex(root);
    String maxField = max == null ? "null" : "\"" + max + "\"";
    Path dir = root.resolve("shipped-workflows");
    Files.writeString(dir.resolve("agentforge4j-catalog.json"),
        ("{\"catalogVersion\":\"1.0.0\",\"minimumAgentForge4jVersion\":\"%s\","
            + "\"maximumAgentForge4jVersion\":%s,\"workflowSchemaVersion\":%d}")
            .formatted(min, maxField, schema),
        StandardCharsets.UTF_8);
  }

  private void writeIndex(Path root) throws IOException {
    Path dir = Files.createDirectories(root.resolve("shipped-workflows"));
    Files.writeString(dir.resolve("index"), "sample\n", StandardCharsets.UTF_8);
  }
}
