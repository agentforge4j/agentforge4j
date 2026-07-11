// SPDX-License-Identifier: Apache-2.0
//
// Proves the AJV fixture gate is real: the authoritative valid fixture passes and the repo's
// negative fixture (agent.invalid.json) is rejected. Guards against the gate silently accepting
// anything. Reads the committed schemas + fixtures (stable source).

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const here = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(here, '..', '..');
const SCHEMA_DIR = join(REPO_ROOT, 'agentforge4j-schema', 'src', 'main', 'resources', 'schema');
const FIXTURES = join(REPO_ROOT, 'agentforge4j-schema', 'src', 'test', 'resources', 'fixtures');

const ajv = new Ajv2020({allErrors: true, strict: false});
addFormats(ajv);
for (const kind of ['workflow', 'agent', 'artifact', 'blueprint', 'integration']) {
  ajv.addSchema(JSON.parse(readFileSync(join(SCHEMA_DIR, `${kind}.schema.json`), 'utf8')), `${kind}.schema.json`);
}
const agent = ajv.getSchema('agent.schema.json');

function read(name) {
  return JSON.parse(readFileSync(join(FIXTURES, name), 'utf8'));
}

test('the authoritative agent.valid.json passes the agent schema', () => {
  assert.equal(agent(read('agent.valid.json')), true);
});

test('agent.invalid.json is rejected by the agent schema', () => {
  assert.equal(agent(read('agent.invalid.json')), false);
});

test('agent.valid.json uses a real (non-fake) provider', () => {
  const fixture = read('agent.valid.json');
  const providers = fixture.providerPreferences.map((p) => p.provider);
  assert.ok(!providers.includes('fake'), 'the public agent fixture must not use the test-only fake provider');
});
