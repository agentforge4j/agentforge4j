// SPDX-License-Identifier: Apache-2.0
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MIRRORED_BRAND_ASSETS, findBrandAssetProblems } from './lint-brand-assets.mjs';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

const PAIR = [{ canonical: 'a/one.png', copy: 'b/two.png', why: 'test pair' }];

function fixtureRepo({ canonical, copy }) {
  const root = mkdtempSync(join(tmpdir(), 'brand-assets-'));
  for (const [relPath, bytes] of [
    ['a/one.png', canonical],
    ['b/two.png', copy],
  ]) {
    if (bytes === null) {
      continue;
    }
    mkdirSync(join(root, dirname(relPath)), { recursive: true });
    writeFileSync(join(root, relPath), bytes);
  }
  return root;
}

test('the real committed brand assets are byte-identical — this is the gate itself, run against production files', () => {
  assert.deepEqual(findBrandAssetProblems(REPO_ROOT), []);
  // Non-vacuity: an empty declaration list would make the assertion above trivially true.
  assert.ok(MIRRORED_BRAND_ASSETS.length > 0, 'expected at least one declared mirrored brand asset');
});

test('identical bytes pass', () => {
  const root = fixtureRepo({ canonical: Buffer.from([1, 2, 3]), copy: Buffer.from([1, 2, 3]) });
  assert.deepEqual(findBrandAssetProblems(root, PAIR), []);
});

test('NEGATIVE CONTROL — a copy that drifted by a single byte is reported, not waved through on same-size/same-name', () => {
  const root = fixtureRepo({ canonical: Buffer.from([1, 2, 3]), copy: Buffer.from([1, 2, 4]) });
  const problems = findBrandAssetProblems(root, PAIR);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /b\/two\.png is no longer byte-identical to a\/one\.png/);
});

test('a missing published copy is reported rather than silently skipped', () => {
  const root = fixtureRepo({ canonical: Buffer.from([1, 2, 3]), copy: null });
  assert.match(findBrandAssetProblems(root, PAIR)[0], /missing published copy of a\/one\.png/);
});

test('a missing canonical is reported rather than silently skipped', () => {
  const root = fixtureRepo({ canonical: null, copy: Buffer.from([1, 2, 3]) });
  assert.match(findBrandAssetProblems(root, PAIR)[0], /missing canonical brand asset/);
});
