// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.catalog;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Compatibility metadata declared by a shipped workflow catalog, read from
 * {@code shipped-workflows/agentforge4j-catalog.json}.
 *
 * <p>The framework loads a catalog only when this manifest's version bounds admit the running
 * {@link com.agentforge4j.schema.FrameworkVersion} and its declared {@code workflowSchemaVersion}
 * matches {@link com.agentforge4j.schema.WorkflowSchemaVersion#SUPPORTED_WORKFLOW_SCHEMA_VERSION}.
 *
 * @param catalogVersion             the catalog's own (independent) version, for diagnostics
 * @param minimumAgentForge4jVersion lowest framework version the catalog supports (inclusive)
 * @param maximumAgentForge4jVersion highest framework version the catalog supports (inclusive), or
 *                                   {@code null} for unbounded
 * @param workflowSchemaVersion      the workflow schema version the catalog was authored against
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogManifest(
    String catalogVersion,
    String minimumAgentForge4jVersion,
    String maximumAgentForge4jVersion,
    int workflowSchemaVersion) {

  /**
   * Validates the manifest. {@code maximumAgentForge4jVersion} stays {@code null} when unbounded.
   */
  public CatalogManifest {
    Validate.notBlank(catalogVersion, "catalogVersion must not be blank");
    Validate.notBlank(minimumAgentForge4jVersion, "minimumAgentForge4jVersion must not be blank");
    Validate.isGreaterThanZero(workflowSchemaVersion, "workflowSchemaVersion must be greater than 0");
  }
}
