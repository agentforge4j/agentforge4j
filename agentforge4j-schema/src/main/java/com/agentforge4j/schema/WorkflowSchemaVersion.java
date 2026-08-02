// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

/**
 * The workflow schema version this framework build understands.
 *
 * <p>This single integer is the whole schema-compatibility surface, enforced at two points.
 * Every workflow document ({@code workflow.json}) must declare the format version it is authored
 * against in its required {@code schemaVersion} field; the workflow bundle loaders reject a
 * document whose declared version does not match {@link #SUPPORTED_WORKFLOW_SCHEMA_VERSION},
 * naming both versions. A shipped-workflow catalog additionally declares the schema version it was
 * authored against in its {@code agentforge4j-catalog.json} manifest
 * ({@code workflowSchemaVersion}); the catalog compatibility gate rejects a catalog whose declared
 * version does not match, so a catalog built against a newer (or older) workflow schema cannot be
 * loaded by an incompatible framework. Nested documents of a bundle (blueprints, agents,
 * artifacts) carry no version field of their own — the workflow document's {@code schemaVersion}
 * names the wire format of the whole bundle.
 */
public final class WorkflowSchemaVersion {

  /** The single workflow schema version supported by this framework build. */
  public static final int SUPPORTED_WORKFLOW_SCHEMA_VERSION = 1;

  private WorkflowSchemaVersion() {
  }
}
