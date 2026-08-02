// SPDX-License-Identifier: Apache-2.0
//
// Tests for the pure halves of the archive transition (design §7/§12): route derivation from an
// export's file listing, and the old-address -> archive-address redirect manifest. The full
// mechanism (isolated export, frozen artifact, versions.json removal, net-zero revert) is proven
// end-to-end by `npm run docs:archive-scratch`.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdtempSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {archiveTransition, pageRoutes, redirectManifest} from './archive-transition.mjs';

function gitRepo() {
  const dir = mkdtempSync(join(tmpdir(), 'archive-transition-git-'));
  execFileSync('git', ['init', '--quiet'], {cwd: dir});
  execFileSync('git', ['config', 'user.email', 'test@example.invalid'], {cwd: dir});
  execFileSync('git', ['config', 'user.name', 'Test'], {cwd: dir});
  writeFileSync(join(dir, 'a.txt'), 'a');
  execFileSync('git', ['add', '.'], {cwd: dir});
  execFileSync('git', ['commit', '--quiet', '-m', 'first'], {cwd: dir});
  return dir;
}

function shallowClone() {
  const origin = gitRepo();
  writeFileSync(join(origin, 'b.txt'), 'b');
  execFileSync('git', ['add', '.'], {cwd: origin});
  execFileSync('git', ['commit', '--quiet', '-m', 'second'], {cwd: origin});
  const parent = mkdtempSync(join(tmpdir(), 'archive-transition-shallow-'));
  const shallowDir = join(parent, 'clone');
  // file:// forces a real, transport-based (and thus genuinely shallow) clone — a plain local-path
  // clone silently ignores --depth (see verify-canonical.test.mjs's own note on this).
  execFileSync('git', ['clone', '--quiet', '--depth', '1', pathToFileURL(origin).href, shallowDir]);
  return shallowDir;
}

test('archiveTransition refuses to run (docs:archive / docs:archive-scratch) against a shallow git clone — the archive-mode config derives <lastmod> from git history exactly like the live site does, and the frozen artifact can never be re-derived later', () => {
  assert.throws(() => archiveTransition('1.0.0', shallowClone()), /is a shallow git clone/);
});

test('archiveTransition\'s shallow-history precondition does not fire against a full-history repo (falls through to the next, unrelated precondition instead)', () => {
  // A nonexistent version reaches the "no versioned snapshot" check ONLY if the shallow-history
  // precondition above it did not throw — proving the full-history case is not itself rejected.
  assert.throws(() => archiveTransition('9.9.9-no-such-version', gitRepo()), /no versioned snapshot/);
});

test('pageRoutes: every index.html directory is a route; assets and 404 are not', () => {
  const files = [
    '404.html',
    'assets/css/styles.css',
    'assets/js/main.js',
    'get-started/evaluating/index.html',
    'index.html',
    'reference/config/index.html',
    'sitemap.xml',
  ];
  assert.deepEqual(pageRoutes(files), ['', 'get-started/evaluating', 'reference/config']);
});

test('pageRoutes: empty export has no routes', () => {
  assert.deepEqual(pageRoutes(['404.html', 'assets/css/styles.css']), []);
});

test('redirectManifest: maps each old active address to the same route under the archive mount', () => {
  assert.deepEqual(redirectManifest('1.0.0', ['', 'reference/config']), [
    {from: '/docs/1.0.0', to: '/docs/archive/1.0.0'},
    {from: '/docs/1.0.0/reference/config', to: '/docs/archive/1.0.0/reference/config'},
  ]);
});
