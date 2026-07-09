// SPDX-License-Identifier: Apache-2.0
//
// Source-backed JSON fixture gate. Validates real repo fixtures against the published
// Draft 2020-12 schemas with AJV, so a doc-included fixture that no longer matches its schema fails the
// build. Nothing is hand-authored in the docs module.
//
// Fixture sources (chosen so every validated document is a real, test-guarded repo artifact):
//   - agent      -> agentforge4j-schema/src/test/resources/fixtures/agent.valid.json  (the authoritative,
//                   test-guarded, real-provider fixture). The example agent.json files use the test-only
//                   `fake` provider for offline runnability and are NOT public schema-conformance fixtures
//                   (the agent schema's provider enum deliberately excludes `fake`; the agent loader does
//                   not enforce the enum), so they are intentionally not validated here.
//   - workflow   -> example */workflow.json + the schema fixture workflow.valid.json
//   - artifact   -> example *.artifact.json
//   - blueprint  -> example *.blueprint.json
//   - integration-> schema-only; no fixture exists anywhere, so none is validated (and the integration
//                   reference renders schema-only and still builds).
//
// Drift guard: the agent schema's provider enum must equal the registered non-fake public providers
// (the same set the docs provider matrix is generated from), so the schema and the provider list cannot
// drift apart. No runtime provider semantics are touched and `fake` is never added to the enum.

import {readdirSync, readFileSync, statSync, existsSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const here = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(here, '..', '..');
const SCHEMA_DIR = join(REPO_ROOT, 'agentforge4j-schema', 'src', 'main', 'resources', 'schema');
const SCHEMA_FIXTURES = join(REPO_ROOT, 'agentforge4j-schema', 'src', 'test', 'resources', 'fixtures');
const EXAMPLES_DIR = join(REPO_ROOT, 'agentforge4j-examples');
const PROVIDERS_JSON = join(here, 'emitter-output', 'providers.json');

const ajv = new Ajv2020({allErrors: true, strict: false});
addFormats(ajv);

const KINDS = ['workflow', 'agent', 'artifact', 'blueprint', 'integration'];
// No $id and cross-file refs by filename (e.g. blueprint refs workflow.schema.json#/$defs/...), so
// register every schema under its filename key before compiling.
for (const kind of KINDS) {
  ajv.addSchema(JSON.parse(readFileSync(join(SCHEMA_DIR, `${kind}.schema.json`), 'utf8')), `${kind}.schema.json`);
}
const validators = Object.fromEntries(KINDS.map((k) => [k, ajv.getSchema(`${k}.schema.json`)]));

/** Map an EXAMPLE resource file to the schema kind it must satisfy (example agent.json deliberately skipped). */
function exampleKindOf(path) {
  if (path.endsWith('.artifact.json')) {
    return 'artifact';
  }
  if (path.endsWith('.blueprint.json')) {
    return 'blueprint';
  }
  if (path.endsWith('workflow.json') && dirname(path).endsWith('.workflow')) {
    return 'workflow';
  }
  return null; // example agent.json (fake provider) and everything else: not a public schema fixture
}

function collectJson(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    if (entry === 'target') {
      continue;
    }
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...collectJson(full));
    } else if (entry.endsWith('.json')) {
      out.push(full);
    }
  }
  return out;
}

/** The fixtures to validate: authoritative schema fixtures + conforming example resources. */
function fixtureCases() {
  const cases = [
    {file: join(SCHEMA_FIXTURES, 'agent.valid.json'), kind: 'agent'},
    {file: join(SCHEMA_FIXTURES, 'workflow.valid.json'), kind: 'workflow'},
  ];
  for (const file of collectJson(EXAMPLES_DIR)) {
    const kind = exampleKindOf(file.split('\\').join('/'));
    if (kind !== null) {
      cases.push({file, kind});
    }
  }
  return cases;
}

let failures = 0;
const byKind = {};
for (const {file, kind} of fixtureCases()) {
  if (!existsSync(file)) {
    console.error(`✗ expected fixture missing: ${file.slice(REPO_ROOT.length + 1)}`);
    failures += 1;
    continue;
  }
  byKind[kind] = (byKind[kind] || 0) + 1;
  const data = JSON.parse(readFileSync(file, 'utf8'));
  if (!validators[kind](data)) {
    failures += 1;
    console.error(`✗ ${file.slice(REPO_ROOT.length + 1)} (${kind}):`);
    for (const err of validators[kind].errors) {
      console.error(`    ${err.instancePath || '/'} ${err.message}`);
    }
  }
}

// --- Drift guard: agent schema provider enum == registered non-fake public providers ---------------
function driftGuard() {
  if (!existsSync(PROVIDERS_JSON)) {
    console.error(`✗ drift guard: ${PROVIDERS_JSON.slice(REPO_ROOT.length + 1)} missing — run \`npm run generate\` (after the emitter) first.`);
    return 1;
  }
  const agentSchema = JSON.parse(readFileSync(join(SCHEMA_DIR, 'agent.schema.json'), 'utf8'));
  const enumValues = agentSchema?.$defs?.ProviderPreference?.properties?.provider?.enum
    ?? findProviderEnum(agentSchema);
  if (!Array.isArray(enumValues)) {
    console.error('✗ drift guard: could not locate the provider enum in agent.schema.json');
    return 1;
  }
  const registered = JSON.parse(readFileSync(PROVIDERS_JSON, 'utf8')).map((p) => p.name).sort();
  const schemaEnum = [...enumValues].sort();
  if (JSON.stringify(registered) !== JSON.stringify(schemaEnum)) {
    console.error('✗ drift guard: agent schema provider enum != registered non-fake providers.');
    console.error(`    schema enum:  ${schemaEnum.join(', ')}`);
    console.error(`    registered:   ${registered.join(', ')}`);
    return 1;
  }
  console.log(`drift guard: agent schema provider enum aligned with ${registered.length} registered non-fake providers.`);
  return 0;
}

/** Find a `provider` property `enum` anywhere in the schema (robust to the exact $defs path). */
function findProviderEnum(node) {
  if (!node || typeof node !== 'object') {
    return null;
  }
  if (node.properties?.provider?.enum) {
    return node.properties.provider.enum;
  }
  for (const value of Object.values(node)) {
    const found = findProviderEnum(value);
    if (found) {
      return found;
    }
  }
  return null;
}

failures += driftGuard();

if (failures > 0) {
  console.error(`\nfixture-validation: ${failures} failure(s).`);
  process.exit(1);
}
console.log(`fixture-validation: all fixtures valid ${JSON.stringify(byKind)}.`);
