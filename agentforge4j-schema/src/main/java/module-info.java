// SPDX-License-Identifier: Apache-2.0
/**
 * JSON Schema assets, framework version/compatibility metadata, and validation entry points aligned
 * with core workflow and agent contracts.
 *
 * <p>Bundled JSON Schema and validation helpers aligned with {@code agentforge4j.core} workflow and agent
 * contracts, plus the framework version metadata ({@code FrameworkVersion},
 * {@code WorkflowSchemaVersion}) the catalog compatibility gate compares against. Bundled schema
 * resources are opened for reflective access; the exported package is the supported API for loading
 * and applying those contracts.
 *
 * <p>Consumers include tests, tooling, and applications that need strict contract validation; other
 * modules may use it as a separate dependency when they choose to wire schema checks explicitly.
 */
module agentforge4j.schema {
  requires agentforge4j.util;
  requires static lombok;
  exports com.agentforge4j.schema;
  opens schema;
  opens schema.ledger;
}
