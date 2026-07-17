// SPDX-License-Identifier: Apache-2.0
//
// Integration test proving the cross-directory relative import from
// agentforge4j-docs/scripts/ resolves correctly from this module — the actual matcher
// behaviour is exhaustively tested by attribution-terms.test.mjs and product-name.test.mjs
// in agentforge4j-docs itself; this only guards the relative-path wiring, plus that the
// gate's EXTRA_FILES allowlist (README, HTML shell, deploy config, local Dockerfile) is
// actually present and actually scanned by the running script.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {statSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {findProductNameLeaks} from '../../agentforge4j-docs/scripts/product-name.mjs';
import {findAttributionLeaks} from '../../agentforge4j-docs/scripts/attribution-terms.mjs';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const EXTRA_FILES = ['README.md', 'index.html', 'nginx.conf', 'Dockerfile.local', 'public/robots.txt'];

test('resolves the shared product-name matcher from agentforge4j-docs', () => {
  assert.ok(findProductNameLeaks('Uses `agentforge4j-platform-engine`.').length > 0);
});

test('resolves the shared attribution-terms matcher from agentforge4j-docs', () => {
  assert.ok(findAttributionLeaks('Co-Authored-By: Claude').length > 0);
});

test('does not flag legitimate provider mentions via the shared matcher', () => {
  assert.deepEqual(findAttributionLeaks('AgentForge4j supports Claude as a pluggable provider.'), []);
});

test('every extra committed-content file the gate depends on actually exists', () => {
  for (const file of EXTRA_FILES) {
    assert.ok(statSync(join(MODULE_ROOT, file)).isFile(), `${file} should exist for the content gate to scan`);
  }
});

// Requires `npm run catalogue:build` to have already run (the `check` script's own ordering) —
// the generated file carries shipped workflows' own author/contact/description text, which the
// gate must scan the same as any other committed prose surface.
test('the generated catalogue-data.json the gate also scans actually exists', () => {
  assert.ok(
    statSync(join(MODULE_ROOT, 'src/generated/catalogue-data.json')).isFile(),
    'src/generated/catalogue-data.json should exist — run `npm run catalogue:build` first',
  );
});

test('running the content gate scans the extra files and passes on real repository content', () => {
  const result = spawnSync(process.execPath, ['scripts/lint-content-gate.mjs'], {
    cwd: MODULE_ROOT,
    encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /content-gate: \d+ file\(s\) clean/);
});
