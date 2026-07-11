// SPDX-License-Identifier: Apache-2.0
//
// Tests for the version-pinned Javadoc orchestration (design §7/§12) with a stub builder — the real
// per-version build needs a release tag plus a full reactor install, neither of which exists
// pre-`0.1.0`. What IS provable now: the release-tag naming, the per-version fan-out and output
// placement, the pre-release no-op, input validation, and the MAVEN_OPTS isolation ordering.
// The tag checkout/cleanup lifecycle (withTagWorktree) is proven for real against a manufactured
// tag by `npm run docs:javadoc-versions-scratch` — it needs a real git tag, so it does not belong
// in this fast, tag-free suite.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {
  buildJavadocVersions,
  isolatedMavenOpts,
  javadocBuildVersions,
  releaseTag,
} from './build-javadoc-versions.mjs';

test('release tags are v-prefixed versions', () => {
  assert.equal(releaseTag('1.2.0'), 'v1.2.0');
});

test('builds one surface per released version, newest first, into the output root', () => {
  const outRoot = mkdtempSync(join(tmpdir(), 'jdv-'));
  const calls = [];
  const built = buildJavadocVersions(['1.1.0', '1.0.0'], {
    outRoot,
    builder: (version, outDir) => calls.push([version, outDir]),
  });
  assert.deepEqual(built, ['1.1.0', '1.0.0']);
  assert.deepEqual(calls, [
    ['1.1.0', join(outRoot, '1.1.0')],
    ['1.0.0', join(outRoot, '1.0.0')],
  ]);
  // The per-version output directories were created for the builder.
  assert.ok(existsSync(join(outRoot, '1.1.0')));
  assert.ok(existsSync(join(outRoot, '1.0.0')));
});

test('pre-first-release (no released versions) is a no-op', () => {
  const outRoot = mkdtempSync(join(tmpdir(), 'jdv-'));
  const built = buildJavadocVersions([], {
    outRoot,
    builder: () => {
      throw new Error('must not be called');
    },
  });
  assert.deepEqual(built, []);
});

test('rejects non-array input and unsafe version strings', () => {
  assert.throws(() => buildJavadocVersions(null), /`versions` must be an array/);
  const outRoot = mkdtempSync(join(tmpdir(), 'jdv-'));
  assert.throws(
    () => buildJavadocVersions(['../evil'], {outRoot, builder: () => {}}),
    /invalid version/,
  );
});

test('isolatedMavenOpts: the isolated repo wins even when inherited MAVEN_OPTS sets its own -Dmaven.repo.local', () => {
  assert.equal(
    isolatedMavenOpts('-Dmaven.repo.local=/somewhere/else -Dfoo=bar', '/isolated/m2'),
    '-Dmaven.repo.local=/isolated/m2 -Dfoo=bar',
  );
});

test('isolatedMavenOpts: no inherited MAVEN_OPTS', () => {
  assert.equal(isolatedMavenOpts(undefined, '/isolated/m2'), '-Dmaven.repo.local=/isolated/m2');
  assert.equal(isolatedMavenOpts('', '/isolated/m2'), '-Dmaven.repo.local=/isolated/m2');
});

test('isolatedMavenOpts: preserves unrelated flags and collapses whitespace', () => {
  assert.equal(
    isolatedMavenOpts('  -Xmx2g   -Dmaven.repo.local=/old  ', '/isolated/m2'),
    '-Dmaven.repo.local=/isolated/m2 -Xmx2g',
  );
});

test('isolatedMavenOpts: strips an inherited -Dmaven.repo.local whose VALUE contains a space, with no stray fragment left behind', () => {
  // A realistic Windows path (this repo's own checkout path has a space in it too).
  const inherited = String.raw`-Dmaven.repo.local=C:\Users\dev\my repo\.m2\repository -Dfoo=bar`;
  assert.equal(
    isolatedMavenOpts(inherited, '/isolated/m2'),
    '-Dmaven.repo.local=/isolated/m2 -Dfoo=bar',
  );
});

test('javadocBuildVersions: no archived versions returns the active list unchanged', () => {
  assert.deepEqual(javadocBuildVersions(['1.1.0', '1.0.0'], []), ['1.1.0', '1.0.0']);
});

test('javadocBuildVersions: appends archived versions not already active, active-first', () => {
  assert.deepEqual(
    javadocBuildVersions(['1.1.0'], ['1.0.0', '0.9.0']),
    ['1.1.0', '1.0.0', '0.9.0'],
  );
});

test('javadocBuildVersions: does not duplicate a version present in both lists', () => {
  assert.deepEqual(javadocBuildVersions(['1.1.0', '1.0.0'], ['1.0.0']), ['1.1.0', '1.0.0']);
});

test('javadocBuildVersions: pre-first-release (no active versions) still publishes archived ones', () => {
  assert.deepEqual(javadocBuildVersions([], ['0.9.0']), ['0.9.0']);
});
