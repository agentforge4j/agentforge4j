// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for the Pages artifact assembly. Fixture build/ + javadoc directories under a
// temp root stand in for a real Docusaurus build and a real Javadoc surface — no build required.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {assembleSite} from './assemble-site.mjs';

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'assemble-'));
  const buildDir = join(root, 'build');
  const javadocDir = join(root, 'javadoc-next');
  mkdirSync(buildDir, {recursive: true});
  writeFileSync(join(buildDir, 'index.html'), '<html>docs</html>');
  mkdirSync(javadocDir, {recursive: true});
  writeFileSync(join(javadocDir, 'index.html'), '<html>javadoc</html>');
  return {root, buildDir, javadocDir, archiveDir: join(root, 'archive-absent'), siteDir: join(root, '_site')};
}

test('composes the _site layout: docs/, javadoc/next, javadoc/latest', () => {
  const {buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/next/', customDomain: null});
  assert.ok(existsSync(join(siteDir, 'docs', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'next', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'latest', 'index.html')));
});

test('writes the root redirect and .nojekyll', () => {
  const {buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/1.0.0/', customDomain: null});
  assert.ok(existsSync(join(siteDir, '.nojekyll')));
  const index = readFileSync(join(siteDir, 'index.html'), 'utf8');
  assert.match(index, /url=\/docs\/1\.0\.0\//);
});

test('carries the archive forward when archive/ exists', () => {
  const {root, buildDir, javadocDir, siteDir} = fixture();
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived</html>');
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/next/', customDomain: null});
  assert.ok(existsSync(join(siteDir, 'docs', 'archive', '1.0.0', 'index.html')));
});

test('does not create a docs/archive/ when no archive/ exists', () => {
  const {buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/next/', customDomain: null});
  assert.ok(!existsSync(join(siteDir, 'docs', 'archive')));
});

test('CNAME is absent unless a custom domain is passed', () => {
  const {buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/next/', customDomain: null});
  assert.ok(!existsSync(join(siteDir, 'CNAME')));
});

test('CNAME is written when a custom domain is passed', () => {
  const {buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/next/', customDomain: 'agentforge4j.org'});
  assert.equal(readFileSync(join(siteDir, 'CNAME'), 'utf8'), 'agentforge4j.org\n');
});
