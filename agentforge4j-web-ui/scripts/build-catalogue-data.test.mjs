// SPDX-License-Identifier: Apache-2.0
//
// Fixture-directory unit tests for build-catalogue-data.mjs, mirroring the
// mkdtempSync-per-test fixture shape agentforge4j-docs/scripts/assemble-site.test.mjs already
// uses. Points at the REAL live schema and framework-constant source (the same files the site
// build itself reads) so the tests stay grounded against the actual contract, except where a
// test needs to simulate a violation of that contract (schema-version mismatch), which uses a
// throwaway stub source file instead of editing the real one.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  buildCatalogueData,
  readManifest,
  readIndexIds,
  crossCheckBundles,
  checkNoDuplicateIds,
  checkIdFormat,
  readSupportedWorkflowSchemaVersion,
  createWorkflowValidator,
} from './build-catalogue-data.mjs';
import { WORKFLOW_ID_PATTERN } from './workflow-id-contract.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(here, '..', '..');
const REAL_SCHEMA_PATH = join(
  REPO_ROOT,
  'agentforge4j-schema',
  'src',
  'main',
  'resources',
  'schema',
  'workflow.schema.json',
);
const REAL_JAVA_SCHEMA_VERSION_SOURCE = join(
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

function tempShippedWorkflowsDir() {
  const root = mkdtempSync(join(tmpdir(), 'catalogue-data-'));
  mkdirSync(root, { recursive: true });
  return root;
}

function writeManifest(dir, overrides = {}) {
  const manifest = {
    catalogVersion: '0.1.0-SNAPSHOT',
    minimumAgentForge4jVersion: '0.0.1',
    maximumAgentForge4jVersion: null,
    workflowSchemaVersion: 1,
    ...overrides,
  };
  writeFileSync(join(dir, 'agentforge4j-catalog.json'), JSON.stringify(manifest));
}

function writeIndex(dir, ids) {
  writeFileSync(join(dir, 'index'), ids.join('\n'));
}

function writeWorkflow(dir, id, overrides = {}) {
  const bundleDir = join(dir, `${id}.workflow`);
  mkdirSync(bundleDir, { recursive: true });
  const doc = {
    kind: 'WORKFLOW',
    schemaVersion: 1,
    id,
    name: `Workflow ${id}`,
    steps: [
      {
        kind: 'STEP',
        stepId: 'only-step',
        name: 'Only Step',
        behaviour: { type: 'FAIL', reason: 'fixture' },
      },
    ],
    ...overrides,
  };
  writeFileSync(join(bundleDir, 'workflow.json'), JSON.stringify(doc));
}

function defaultInputs(dir) {
  return {
    shippedWorkflowsDir: dir,
    schemaPath: REAL_SCHEMA_PATH,
    javaSchemaVersionSourcePath: REAL_JAVA_SCHEMA_VERSION_SOURCE,
  };
}

test('empty index yields a valid, deliberate zero-workflow catalogue', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, []);

  const data = buildCatalogueData(defaultInputs(dir));

  assert.deepEqual(data.workflows, []);
  assert.equal(data.catalogVersion, '0.1.0-SNAPSHOT');
  assert.equal(data.minimumAgentForge4jVersion, '0.0.1');
  assert.equal(data.maximumAgentForge4jVersion, null);
  assert.equal(data.workflowSchemaVersion, 1);
});

test('one workflow round-trips id/name/description/schemaVersion/steps', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, ['sample-workflow']);
  writeWorkflow(dir, 'sample-workflow', { description: 'A sample workflow.' });

  const data = buildCatalogueData(defaultInputs(dir));

  assert.equal(data.workflows.length, 1);
  const [workflow] = data.workflows;
  assert.equal(workflow.id, 'sample-workflow');
  assert.equal(workflow.name, 'Workflow sample-workflow');
  assert.equal(workflow.description, 'A sample workflow.');
  assert.equal(workflow.schemaVersion, 1);
  assert.equal(workflow.steps.length, 1);
});

test('a workflow missing every optional field emits null, never a fabricated default', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, ['bare-workflow']);
  // No description on this fixture's workflow.json at all.
  writeWorkflow(dir, 'bare-workflow');

  const data = buildCatalogueData(defaultInputs(dir));

  const [workflow] = data.workflows;
  assert.equal(workflow.description, null);
  assert.equal(workflow.author, null);
  assert.equal(workflow.contact, null);
  assert.equal(workflow.version, null);
  assert.equal(workflow.source, null);
});

test('fail-closed: missing bundle — index lists an id with no matching workflow.json', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, ['ghost-workflow']);
  // Deliberately no ghost-workflow.workflow/ directory written.

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /missing bundle.*ghost-workflow/s);
});

test('fail-closed: orphaned entry — a workflow directory exists but is not in index', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, []);
  writeWorkflow(dir, 'stray-workflow');

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /orphaned entry.*stray-workflow/s);
});

test('fail-closed: invalid schema — workflow.json fails ajv validation', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, ['broken-workflow']);
  // 'steps' is required and must have minItems 1; omit it entirely.
  const bundleDir = join(dir, 'broken-workflow.workflow');
  mkdirSync(bundleDir, { recursive: true });
  writeFileSync(
    join(bundleDir, 'workflow.json'),
    JSON.stringify({ kind: 'WORKFLOW', schemaVersion: 1, id: 'broken-workflow', name: 'Broken' }),
  );

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /invalid schema.*broken-workflow/s);
});

test('fail-closed: duplicate index entry — the same id listed twice', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, ['dup-workflow', 'dup-workflow']);
  writeWorkflow(dir, 'dup-workflow');

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /duplicate index entry.*dup-workflow/s);
});

test('checkNoDuplicateIds passes through unique ids without throwing', () => {
  assert.doesNotThrow(() => checkNoDuplicateIds(['a', 'b', 'c']));
});

test('fail-closed: identity mismatch — workflow.json id does not match its index/bundle id', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  writeIndex(dir, ['bundle-name']);
  writeWorkflow(dir, 'bundle-name', { id: 'different-declared-id' });

  assert.throws(
    () => buildCatalogueData(defaultInputs(dir)),
    /identity mismatch.*bundle-name.*different-declared-id/s,
  );
});

test('checkIdFormat accepts every currently shipped real catalogue workflow id unchanged', () => {
  const realIds = readIndexIds(
    join(REPO_ROOT, 'agentforge4j-workflows-catalog', 'src', 'main', 'resources', 'shipped-workflows'),
  );
  assert.ok(realIds.length > 0, 'expected at least one real shipped id to check');
  assert.doesNotThrow(() => checkIdFormat(realIds));
  for (const id of realIds) {
    assert.match(id, WORKFLOW_ID_PATTERN, `real shipped id ${JSON.stringify(id)} must satisfy the contract`);
  }
});

test('checkIdFormat fails closed on an empty id (defense in depth: unreachable via a real index file, whose blank lines are already filtered by readIndexIds, but not unreachable for any future direct caller)', () => {
  assert.throws(() => checkIdFormat(['']), /unsafe id/);
});

test('fail-closed: unsafe id — every id shape the slug contract must reject', () => {
  const invalidIds = [
    '.',
    '..',
    '/etc/passwd',
    'a\\b',
    'Bad-Id', // uppercase
    '-leading-hyphen',
    'trailing-hyphen-',
    'double--hyphen',
    'has space',
    'has_underscore',
    'has.dot',
  ];
  for (const id of invalidIds) {
    const dir = tempShippedWorkflowsDir();
    writeManifest(dir);
    writeIndex(dir, [id]);
    writeWorkflow(dir, id);
    assert.throws(
      () => buildCatalogueData(defaultInputs(dir)),
      /unsafe id.*catalogue route segment/s,
      `expected id ${JSON.stringify(id)} to be rejected`,
    );
  }
});

test('fail-closed: unsafe id runs before the bundle cross-check, so a malformed id fails with its own specific message rather than an incidental "missing bundle"', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  // No workflow.json bundle written at all — if checkIdFormat did not run first, this would fail
  // with "missing bundle" instead, a confusing diagnostic for what is really an id-format problem.
  writeIndex(dir, ['/etc/passwd']);

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /unsafe id/);
});

test('createWorkflowValidator uses the 2020-12 dialect: a prefixItems tuple constraint is enforced, not silently ignored', () => {
  const dir = tempShippedWorkflowsDir();
  const schemaPath = join(dir, 'tuple.schema.json');
  writeFileSync(
    schemaPath,
    JSON.stringify({
      $schema: 'https://json-schema.org/draft/2020-12/schema',
      type: 'array',
      prefixItems: [{ type: 'string' }, { type: 'number' }],
      items: false,
    }),
  );
  const validate = createWorkflowValidator(schemaPath);

  assert.equal(validate(['a', 1]), true);
  // A third element is disallowed by `items: false` once the prefixItems tuple is exhausted — a
  // 2020-12-only interaction. Draft-07 (ajv's default dialect) has no `prefixItems` keyword at
  // all, so under the old setup this would have been silently ignored instead of rejected.
  assert.equal(validate(['a', 1, 'unexpected']), false);
});

test('fail-closed: unsupported data — manifest workflowSchemaVersion does not match the framework constant', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir, { workflowSchemaVersion: 999 });
  writeIndex(dir, []);

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /unsupported data.*999/s);
});

test('fail-closed: invalid manifest — blank catalogVersion (mirrors CatalogManifest\'s compact constructor)', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir, { catalogVersion: '  ' });
  writeIndex(dir, []);

  assert.throws(() => buildCatalogueData(defaultInputs(dir)), /invalid manifest.*catalogVersion/s);
});

test('readSupportedWorkflowSchemaVersion parses the real live framework constant', () => {
  const version = readSupportedWorkflowSchemaVersion(REAL_JAVA_SCHEMA_VERSION_SOURCE);
  assert.equal(typeof version, 'number');
  assert.ok(version > 0);
});

test('readSupportedWorkflowSchemaVersion fails closed on an unparseable source', () => {
  const dir = tempShippedWorkflowsDir();
  const stub = join(dir, 'Stub.java');
  writeFileSync(stub, 'public final class Stub {}\n');

  assert.throws(() => readSupportedWorkflowSchemaVersion(stub), /could not parse/);
});

test('readManifest and readIndexIds compose against the same directory independently', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir, { catalogVersion: '9.9.9' });
  writeIndex(dir, ['a', 'b']);
  writeWorkflow(dir, 'a');
  writeWorkflow(dir, 'b');

  assert.equal(readManifest(dir).catalogVersion, '9.9.9');
  assert.deepEqual(readIndexIds(dir), ['a', 'b']);
  assert.doesNotThrow(() => crossCheckBundles(dir, ['a', 'b']));
});

test('the real live catalogue builds cleanly end to end (regression guard against schema/data drift)', () => {
  const data = buildCatalogueData({
    shippedWorkflowsDir: join(
      REPO_ROOT,
      'agentforge4j-workflows-catalog',
      'src',
      'main',
      'resources',
      'shipped-workflows',
    ),
    schemaPath: REAL_SCHEMA_PATH,
    javaSchemaVersionSourcePath: REAL_JAVA_SCHEMA_VERSION_SOURCE,
  });

  assert.ok(data.workflows.length >= 1);
  assert.ok(data.workflows.some((workflow) => workflow.id === 'workflow-execution-estimator'));
});

// Sanity-check the fixture writer itself resolves to real, readable files (guards against a typo
// silently producing a false pass above).
test('fixture helper writes files that exist and parse as JSON', () => {
  const dir = tempShippedWorkflowsDir();
  writeManifest(dir);
  const manifestRaw = readFileSync(join(dir, 'agentforge4j-catalog.json'), 'utf8');
  assert.doesNotThrow(() => JSON.parse(manifestRaw));
});
