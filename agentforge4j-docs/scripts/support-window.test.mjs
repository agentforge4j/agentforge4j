// SPDX-License-Identifier: Apache-2.0
//
// Tests for the supported-version window and the redirect toggle it drives.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {supportWindow} from './support-window.mjs';
import {redirectConfig, docsEntryPath} from './redirect-config.mjs';

test('pre-release: empty version list has no latest and nothing archived', () => {
  assert.deepEqual(supportWindow([]), {latest: null, supported: [], archived: []});
});

test('one released version is the latest and the only supported', () => {
  assert.deepEqual(supportWindow(['1.0.0']), {
    latest: '1.0.0',
    supported: ['1.0.0'],
    archived: [],
  });
});

test('newest plus one prior are supported; older are archived', () => {
  assert.deepEqual(supportWindow(['1.2.0', '1.1.0', '1.0.0', '0.9.0']), {
    latest: '1.2.0',
    supported: ['1.2.0', '1.1.0'],
    archived: ['1.0.0', '0.9.0'],
  });
});

test('a designated LTS stays supported even when older', () => {
  assert.deepEqual(supportWindow(['1.2.0', '1.1.0', '1.0.0', '0.9.0'], ['1.0.0']), {
    latest: '1.2.0',
    supported: ['1.2.0', '1.1.0', '1.0.0'],
    archived: ['0.9.0'],
  });
});

test('an LTS entry that is not a released version is ignored', () => {
  assert.deepEqual(supportWindow(['1.1.0', '1.0.0'], ['9.9.9']), {
    latest: '1.1.0',
    supported: ['1.1.0', '1.0.0'],
    archived: [],
  });
});

test('output preserves newest-first ordering', () => {
  const {supported, archived} = supportWindow(['3.0.0', '2.0.0', '1.0.0'], ['1.0.0']);
  assert.deepEqual(supported, ['3.0.0', '2.0.0', '1.0.0']);
  assert.deepEqual(archived, []);
});

test('rejects non-array inputs', () => {
  assert.throws(() => supportWindow(null), /`versions` must be an array/);
  assert.throws(() => supportWindow([], 'x'), /`lts` must be an array/);
});

test('redirect toggle: pre-release routes / and /latest to /next/ (inert, matches Phase 1)', () => {
  assert.deepEqual(redirectConfig(supportWindow([])), [
    {from: '/', to: '/next/'},
    {from: '/latest', to: '/next/'},
  ]);
});

test('redirect toggle: post-release routes / -> /latest -> newest stable', () => {
  assert.deepEqual(redirectConfig(supportWindow(['1.2.0', '1.1.0'])), [
    {from: '/', to: '/latest'},
    {from: '/latest', to: '/1.2.0/'},
  ]);
});

test('docsEntryPath: pre-release resolves to next (drives navbar/footer targets, inert today)', () => {
  assert.equal(docsEntryPath(supportWindow([])), 'next');
});

test('docsEntryPath: post-release resolves to the newest supported stable version', () => {
  assert.equal(docsEntryPath(supportWindow(['1.2.0', '1.1.0'])), '1.2.0');
});
