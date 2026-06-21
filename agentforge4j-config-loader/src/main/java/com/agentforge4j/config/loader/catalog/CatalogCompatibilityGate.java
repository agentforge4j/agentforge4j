// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.catalog;

import com.agentforge4j.config.loader.workflow.WorkflowBundleLocator;
import com.agentforge4j.schema.FrameworkVersion;
import com.agentforge4j.schema.WorkflowSchemaVersion;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Enforces compatibility between a shipped workflow catalog and the running framework, before the
 * catalog's workflows are loaded.
 *
 * <p>Content-present predicate: a catalog is considered present when {@code shipped-workflows/index}
 * resolves on the classpath and contains at least one non-blank entry. When it is absent or empty
 * the gate is a no-op — a pure framework with no catalog jar loads zero shipped workflows, which is
 * valid, not an error. When a catalog <em>is</em> present its
 * {@code shipped-workflows/agentforge4j-catalog.json} manifest is required: a missing, unparseable,
 * or out-of-range manifest fails fast with {@link CatalogCompatibilityException}. Test fixtures
 * loaded from other roots (e.g. {@code /test-workflows/}) never trip this gate.
 *
 * <p>Resources are resolved through the same loader the locators use
 * ({@code WorkflowBundleLocator.class.getClassLoader()}); version bounds are compared numerically by
 * {@link NumericVersion} so a {@code 0.0.1-SNAPSHOT} framework satisfies a {@code 0.0.1} minimum.
 */
public final class CatalogCompatibilityGate {

  private static final String CATALOG_ROOT = "shipped-workflows/";
  private static final String INDEX_RESOURCE = CATALOG_ROOT + "index";
  private static final String MANIFEST_RESOURCE = CATALOG_ROOT + "agentforge4j-catalog.json";

  private final ClassLoader loader;
  private final String frameworkVersion;
  private final int supportedSchemaVersion;
  private final ObjectMapper mapper;

  /**
   * Creates a gate. Prefer {@link #defaults()} for production wiring; this constructor lets tests
   * supply a classloader over a controlled catalog layout and a fixed framework version.
   *
   * @param loader                 classloader over which catalog resources are resolved
   * @param frameworkVersion       the running framework version
   * @param supportedSchemaVersion the workflow schema version this framework supports
   * @param mapper                 JSON mapper for the manifest
   */
  public CatalogCompatibilityGate(ClassLoader loader, String frameworkVersion,
      int supportedSchemaVersion, ObjectMapper mapper) {
    this.loader = Validate.notNull(loader, "loader must not be null");
    this.frameworkVersion =
        Validate.notBlank(frameworkVersion, "frameworkVersion must not be blank");
    this.supportedSchemaVersion = supportedSchemaVersion;
    this.mapper = Validate.notNull(mapper, "mapper must not be null");
  }

  /**
   * The production gate: resolves catalog resources on the locator's classloader, against the
   * running {@link FrameworkVersion} and the supported
   * {@link WorkflowSchemaVersion#SUPPORTED_WORKFLOW_SCHEMA_VERSION}.
   *
   * @return a gate wired for the running framework
   */
  public static CatalogCompatibilityGate defaults() {
    return new CatalogCompatibilityGate(
        WorkflowBundleLocator.class.getClassLoader(),
        FrameworkVersion.current(),
        WorkflowSchemaVersion.SUPPORTED_WORKFLOW_SCHEMA_VERSION,
        new ObjectMapper());
  }

  /**
   * Verifies catalog/framework compatibility, throwing when a present catalog is incompatible.
   *
   * @throws CatalogCompatibilityException when a catalog is present but its manifest is missing,
   *                                       unparseable, version-incompatible, or declares an
   *                                       unsupported workflow schema version
   */
  public void enforce() {
    if (!catalogContentPresent()) {
      return;
    }
    CatalogManifest manifest = readManifest();
    checkVersionBounds(manifest);
    checkSchemaVersion(manifest);
  }

  private boolean catalogContentPresent() {
    URL index = loader.getResource(INDEX_RESOURCE);
    if (index == null) {
      return false;
    }
    try (InputStream stream = index.openStream()) {
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return content.lines().anyMatch(line -> !line.isBlank());
    } catch (IOException e) {
      throw new CatalogCompatibilityException(
          "Failed to read shipped catalog index: %s".formatted(INDEX_RESOURCE), e);
    }
  }

  private CatalogManifest readManifest() {
    URL manifestUrl = loader.getResource(MANIFEST_RESOURCE);
    if (manifestUrl == null) {
      throw new CatalogCompatibilityException(
          ("A shipped workflow catalog is present (%s lists workflows) but its compatibility "
              + "manifest %s is missing; every catalog must declare one")
              .formatted(INDEX_RESOURCE, MANIFEST_RESOURCE));
    }
    try (InputStream stream = manifestUrl.openStream()) {
      return mapper.readValue(stream, CatalogManifest.class);
    } catch (IOException e) {
      throw new CatalogCompatibilityException(
          "Missing, unparseable or invalid shipped catalog manifest: %s".formatted(MANIFEST_RESOURCE),
          e);
    }
  }

  private void checkVersionBounds(CatalogManifest manifest) {
    if (NumericVersion.compare(frameworkVersion, manifest.minimumAgentForge4jVersion()) < 0) {
      throw new CatalogCompatibilityException(
          ("Shipped catalog '%s' requires AgentForge4j >= %s but the running framework is %s")
              .formatted(manifest.catalogVersion(), manifest.minimumAgentForge4jVersion(),
                  frameworkVersion));
    }
    String maximum = manifest.maximumAgentForge4jVersion();
    if (maximum != null && NumericVersion.compare(frameworkVersion, maximum) > 0) {
      throw new CatalogCompatibilityException(
          ("Shipped catalog '%s' supports AgentForge4j <= %s but the running framework is %s")
              .formatted(manifest.catalogVersion(), maximum, frameworkVersion));
    }
  }

  private void checkSchemaVersion(CatalogManifest manifest) {
    if (manifest.workflowSchemaVersion() != supportedSchemaVersion) {
      throw new CatalogCompatibilityException(
          ("Shipped catalog '%s' declares workflow schema version %d but this framework supports %d")
              .formatted(manifest.catalogVersion(), manifest.workflowSchemaVersion(),
                  supportedSchemaVersion));
    }
  }
}
