// SPDX-License-Identifier: Apache-2.0
//
// Reads the shipped-workflow catalogue directly from agentforge4j-workflows-catalog's source
// resources (no Maven invocation, no JVM — same-repo, same-commit read; see the catalogue-track
// implementation plan §1 for the accepted trade-off against the module's own README, which tells
// external consumers to pin a versioned artifact instead) and writes one consolidated
// src/generated/catalogue-data.json for the site build to import statically.
//
// Fails closed (non-zero exit, explicit stderr message) on any of four violations, each mirroring
// a real Java-side failure mode (see agentforge4j-config-loader's ClasspathWorkflowLoader and
// CatalogManifest, and agentforge4j-schema's WorkflowSchemaVersion):
//   1. missing bundle    - an id listed in `index` with no matching <id>.workflow/workflow.json
//   2. orphaned entry    - a <id>.workflow/ directory on disk not listed in `index`
//   3. invalid schema    - a workflow.json failing ajv validation against workflow.schema.json
//   4. unsupported data  - the manifest's workflowSchemaVersion not matching the framework's
//                          WorkflowSchemaVersion.SUPPORTED_WORKFLOW_SCHEMA_VERSION constant
//
// Never emits a partial/empty catalogue silently — a zero-workflow `index` is the one legitimate
// "valid, deliberate empty default" (the catalogue module's own documented contract); every other
// zero-workflow outcome here is a thrown error, not a quiet fallback.

import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');

const SHIPPED_WORKFLOWS_DIR = join(
  REPO_ROOT,
  'agentforge4j-workflows-catalog',
  'src',
  'main',
  'resources',
  'shipped-workflows',
);
const SCHEMA_PATH = join(
  REPO_ROOT,
  'agentforge4j-schema',
  'src',
  'main',
  'resources',
  'schema',
  'workflow.schema.json',
);
// The single source of truth for the supported workflow schema version is the Java constant
// below — never hardcode the number here, so a future bump on the Java side is caught (this
// script fails closed) instead of silently accepted.
// agentforge4j-schema/src/main/java/com/agentforge4j/schema/WorkflowSchemaVersion.java
const JAVA_SCHEMA_VERSION_SOURCE = join(
  REPO_ROOT,
  'agentforge4j-schema',
  'src',
  'main',
  'java',
  'com',
  'agentforge4j',
  'schema',
  'WorkflowSchemaVersion.java',
);
const OUTPUT_PATH = join(MODULE_ROOT, 'src', 'generated', 'catalogue-data.json');

const WORKFLOW_DIR_SUFFIX = '.workflow';

/** Reads and validates `agentforge4j-catalog.json`, mirroring CatalogManifest's compact constructor
 * (catalogVersion/minimumAgentForge4jVersion non-blank, workflowSchemaVersion > 0). */
export function readManifest(shippedWorkflowsDir) {
  const manifestPath = join(shippedWorkflowsDir, 'agentforge4j-catalog.json');
  if (!existsSync(manifestPath)) {
    throw new Error(`missing manifest: expected ${manifestPath}`);
  }
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  if (typeof manifest.catalogVersion !== 'string' || manifest.catalogVersion.trim() === '') {
    throw new Error(`invalid manifest: catalogVersion must not be blank (${manifestPath})`);
  }
  if (
    typeof manifest.minimumAgentForge4jVersion !== 'string' ||
    manifest.minimumAgentForge4jVersion.trim() === ''
  ) {
    throw new Error(`invalid manifest: minimumAgentForge4jVersion must not be blank (${manifestPath})`);
  }
  if (typeof manifest.workflowSchemaVersion !== 'number' || manifest.workflowSchemaVersion <= 0) {
    throw new Error(
      `invalid manifest: workflowSchemaVersion must be greater than 0 (${manifestPath})`,
    );
  }
  return {
    catalogVersion: manifest.catalogVersion,
    minimumAgentForge4jVersion: manifest.minimumAgentForge4jVersion,
    maximumAgentForge4jVersion: manifest.maximumAgentForge4jVersion ?? null,
    workflowSchemaVersion: manifest.workflowSchemaVersion,
  };
}

/** Reads `index` (one shipped workflow id per line), in file order. An empty file is a valid,
 * deliberate empty catalogue, not an error. */
export function readIndexIds(shippedWorkflowsDir) {
  const indexPath = join(shippedWorkflowsDir, 'index');
  if (!existsSync(indexPath)) {
    throw new Error(`missing index: expected ${indexPath}`);
  }
  return readFileSync(indexPath, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

/** Lists `<id>.workflow` directories actually present on disk. The one legitimate use of a
 * directory scan in this script: a cross-check against `index`, never the primary source of
 * which workflows are "in" the catalogue. */
export function listOnDiskWorkflowIds(shippedWorkflowsDir) {
  return readdirSync(shippedWorkflowsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.endsWith(WORKFLOW_DIR_SUFFIX))
    .map((entry) => entry.name.slice(0, -WORKFLOW_DIR_SUFFIX.length));
}

/** Fail-closed rules 1 and 2: missing bundle, orphaned entry. */
export function crossCheckBundles(shippedWorkflowsDir, indexIds) {
  for (const id of indexIds) {
    const workflowJsonPath = join(shippedWorkflowsDir, `${id}${WORKFLOW_DIR_SUFFIX}`, 'workflow.json');
    if (!existsSync(workflowJsonPath)) {
      throw new Error(
        `missing bundle: index lists '${id}' but no workflow.json exists at ${workflowJsonPath}`,
      );
    }
  }
  const indexSet = new Set(indexIds);
  for (const id of listOnDiskWorkflowIds(shippedWorkflowsDir)) {
    if (!indexSet.has(id)) {
      throw new Error(
        `orphaned entry: '${id}${WORKFLOW_DIR_SUFFIX}' exists on disk but is not listed in ` +
          `${join(shippedWorkflowsDir, 'index')}`,
      );
    }
  }
}

/** Parses `SUPPORTED_WORKFLOW_SCHEMA_VERSION` out of the Java source rather than hardcoding it,
 * so a future bump on the Java side is caught here instead of silently accepted. */
export function readSupportedWorkflowSchemaVersion(javaSourcePath) {
  if (!existsSync(javaSourcePath)) {
    throw new Error(`missing framework constant source: expected ${javaSourcePath}`);
  }
  const source = readFileSync(javaSourcePath, 'utf8');
  const match = source.match(/SUPPORTED_WORKFLOW_SCHEMA_VERSION\s*=\s*(\d+)\s*;/);
  if (!match) {
    throw new Error(
      `could not parse SUPPORTED_WORKFLOW_SCHEMA_VERSION out of ${javaSourcePath} — its ` +
        'declaration shape changed; update this script to match',
    );
  }
  return Number(match[1]);
}

/** Fail-closed rule 4: unsupported data. */
export function validateManifestSchemaVersion(manifest, supportedVersion) {
  if (manifest.workflowSchemaVersion !== supportedVersion) {
    throw new Error(
      `unsupported data: catalog manifest declares workflowSchemaVersion ` +
        `${manifest.workflowSchemaVersion}, but this framework build only supports ` +
        `${supportedVersion} (WorkflowSchemaVersion.SUPPORTED_WORKFLOW_SCHEMA_VERSION)`,
    );
  }
}

/** Compiles the real workflow.json schema with ajv, matching the strict/allErrors shape the
 * builder package's own schemaValidator.ts already uses against the same schema family. */
export function createWorkflowValidator(schemaPath) {
  if (!existsSync(schemaPath)) {
    throw new Error(`missing schema: expected ${schemaPath}`);
  }
  const schema = JSON.parse(readFileSync(schemaPath, 'utf8'));
  // validateSchema: false — the schema declares itself against the 2020-12 meta-schema, which
  // ajv doesn't ship by default; skipping meta-validation (not document validation) matches
  // agentforge4j-workflow-builder/src/validation/schemaValidator.ts's own ajv setup against this
  // same schema family.
  const ajv = new Ajv({ strict: false, allErrors: true, validateSchema: false });
  addFormats(ajv);
  return ajv.compile(schema);
}

/** Fail-closed rule 3: invalid schema. Reads `<id>.workflow/workflow.json`, validates it, and
 * projects it to the site's output shape. `author`/`contact`/`version`/`source`/`description` are
 * emitted as `null` when absent — never fabricated or defaulted (that defaulting, e.g.
 * `WorkflowDefinition`'s own `source` default, is a Java-runtime construction concern that must
 * not leak into what this script reports about the raw shipped document). */
export function loadWorkflowEntry(shippedWorkflowsDir, id, validate) {
  const workflowJsonPath = join(shippedWorkflowsDir, `${id}${WORKFLOW_DIR_SUFFIX}`, 'workflow.json');
  const raw = JSON.parse(readFileSync(workflowJsonPath, 'utf8'));
  const valid = validate(raw);
  if (!valid) {
    const details = (validate.errors ?? [])
      .map((error) => `${error.instancePath || '/'} ${error.message}`)
      .join('; ');
    throw new Error(`invalid schema: ${workflowJsonPath} failed workflow.schema.json validation: ${details}`);
  }
  return {
    id: raw.id,
    name: raw.name,
    description: raw.description ?? null,
    author: raw.author ?? null,
    contact: raw.contact ?? null,
    version: raw.version ?? null,
    source: raw.source ?? null,
    schemaVersion: raw.schemaVersion,
    steps: raw.steps,
  };
}

/** Orchestrates the full read-validate-project pipeline. Pure with respect to its inputs (no
 * process.exit, no console output) so tests can call it against fixture directories. */
export function buildCatalogueData({ shippedWorkflowsDir, schemaPath, javaSchemaVersionSourcePath }) {
  const manifest = readManifest(shippedWorkflowsDir);
  const indexIds = readIndexIds(shippedWorkflowsDir);
  crossCheckBundles(shippedWorkflowsDir, indexIds);

  const supportedVersion = readSupportedWorkflowSchemaVersion(javaSchemaVersionSourcePath);
  validateManifestSchemaVersion(manifest, supportedVersion);

  const validate = createWorkflowValidator(schemaPath);
  const workflows = indexIds.map((id) => loadWorkflowEntry(shippedWorkflowsDir, id, validate));

  return {
    workflows,
    catalogVersion: manifest.catalogVersion,
    minimumAgentForge4jVersion: manifest.minimumAgentForge4jVersion,
    maximumAgentForge4jVersion: manifest.maximumAgentForge4jVersion,
    workflowSchemaVersion: manifest.workflowSchemaVersion,
  };
}

function main() {
  try {
    const data = buildCatalogueData({
      shippedWorkflowsDir: SHIPPED_WORKFLOWS_DIR,
      schemaPath: SCHEMA_PATH,
      javaSchemaVersionSourcePath: JAVA_SCHEMA_VERSION_SOURCE,
    });
    mkdirSync(dirname(OUTPUT_PATH), { recursive: true });
    writeFileSync(OUTPUT_PATH, `${JSON.stringify(data, null, 2)}\n`);
    console.log(
      `build-catalogue-data: wrote ${data.workflows.length} workflow(s) to ${OUTPUT_PATH}`,
    );
  } catch (error) {
    console.error(`build-catalogue-data: ${error.message}`);
    process.exit(1);
  }
}

// CLI entry. Guarded so the pure exports above can be unit-tested against fixture directories
// without touching the real repo paths (mirrors agentforge4j-docs/scripts/generate-references.mjs).
if (process.argv[1]?.endsWith('build-catalogue-data.mjs')) {
  main();
}
