// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

/**
 * The workflow schema version this framework build understands.
 *
 * <p>A shipped-workflow catalog declares the schema version it was authored against in its
 * {@code agentforge4j-catalog.json} manifest ({@code workflowSchemaVersion}). The catalog
 * compatibility gate rejects a catalog whose declared version does not match
 * {@link #SUPPORTED_WORKFLOW_SCHEMA_VERSION}, so a catalog built against a newer (or older)
 * workflow schema cannot be loaded by an incompatible framework. This single integer is the whole
 * schema-compatibility surface; there is no {@code schemaVersion} field on individual workflow
 * documents.
 */
public final class WorkflowSchemaVersion {

  /** The single workflow schema version supported by this framework build. */
  public static final int SUPPORTED_WORKFLOW_SCHEMA_VERSION = 1;

  private WorkflowSchemaVersion() {
  }
}
