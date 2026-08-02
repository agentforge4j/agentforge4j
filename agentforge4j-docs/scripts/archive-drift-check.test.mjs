// SPDX-License-Identifier: Apache-2.0
//
// Tests for the archive-drift gate's pure comparison function.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {archiveDrift} from './archive-drift-check.mjs';

test('pre-release: no versions, no drift', () => {
  assert.deepEqual(archiveDrift([], [], []), []);
});

test('only the supported window is active: no drift', () => {
  assert.deepEqual(archiveDrift(['1.2.0', '1.1.0'], [], []), []);
});

test('a version outside the support window that has not been archived yet is drift', () => {
  assert.deepEqual(archiveDrift(['1.2.0', '1.1.0', '1.0.0'], [], []), ['1.0.0']);
});

test('a version outside the support window that is already under archive/ is not drift', () => {
  assert.deepEqual(archiveDrift(['1.2.0', '1.1.0', '1.0.0'], [], ['1.0.0']), []);
});

test('an LTS version stays supported and is never reported as drift', () => {
  assert.deepEqual(archiveDrift(['1.2.0', '1.1.0', '1.0.0', '0.9.0'], ['1.0.0'], []), ['0.9.0']);
});

test('multiple aged-out versions are all reported, newest-first', () => {
  assert.deepEqual(archiveDrift(['1.3.0', '1.2.0', '1.1.0', '1.0.0'], [], []), ['1.1.0', '1.0.0']);
});

test('archive/ entries unrelated to any currently-active version are ignored (already fully archived)', () => {
  assert.deepEqual(archiveDrift(['1.2.0', '1.1.0'], [], ['1.0.0', '0.9.0']), []);
});
