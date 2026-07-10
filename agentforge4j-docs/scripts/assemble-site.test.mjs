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

test('copies one version-pinned Javadoc surface per released version; /javadoc/latest mirrors the newest', () => {
  const {root, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  const javadocVersionsDir = join(root, 'javadoc-versions');
  for (const version of ['1.1.0', '1.0.0']) {
    mkdirSync(join(javadocVersionsDir, version), {recursive: true});
    writeFileSync(join(javadocVersionsDir, version, 'index.html'), `<html>javadoc ${version}</html>`);
  }
  assembleSite({
    buildDir,
    javadocDir,
    javadocVersionsDir,
    releasedVersions: ['1.1.0', '1.0.0'],
    archiveDir,
    siteDir,
    docsEntry: '/docs/1.1.0/',
    customDomain: null,
  });
  assert.equal(
    readFileSync(join(siteDir, 'javadoc', '1.1.0', 'index.html'), 'utf8'),
    '<html>javadoc 1.1.0</html>',
  );
  assert.equal(
    readFileSync(join(siteDir, 'javadoc', '1.0.0', 'index.html'), 'utf8'),
    '<html>javadoc 1.0.0</html>',
  );
  // releasedVersions[0] (newest, per versions.json's newest-first convention) is the /latest source.
  assert.equal(
    readFileSync(join(siteDir, 'javadoc', 'latest', 'index.html'), 'utf8'),
    '<html>javadoc 1.1.0</html>',
  );
});

test('publishes an archived version\'s redirect manifest as static stubs at its old active address', () => {
  const {root, buildDir, javadocDir, siteDir} = fixture();
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived</html>');
  writeFileSync(
    join(archiveDir, '1.0.0.redirects.json'),
    JSON.stringify([
      {from: '/docs/1.0.0', to: '/docs/archive/1.0.0'},
      {from: '/docs/1.0.0/reference/config', to: '/docs/archive/1.0.0/reference/config'},
    ]),
  );
  assembleSite({buildDir, javadocDir, archiveDir, siteDir, docsEntry: '/docs/next/', customDomain: null});

  const rootStub = readFileSync(join(siteDir, 'docs', '1.0.0', 'index.html'), 'utf8');
  assert.match(rootStub, /url=\/docs\/archive\/1\.0\.0\//);
  const nestedStub = readFileSync(
    join(siteDir, 'docs', '1.0.0', 'reference', 'config', 'index.html'),
    'utf8',
  );
  assert.match(nestedStub, /url=\/docs\/archive\/1\.0\.0\/reference\/config\//);
});

test('redirect stub collision guard fails closed instead of overwriting a live page', () => {
  const {root, buildDir, javadocDir, siteDir} = fixture();
  // A live page already occupies the exact address the stub would claim — archive/ and
  // versions.json disagree (the scenario writeRedirectStubs exists to catch).
  mkdirSync(join(buildDir, '1.0.0'), {recursive: true});
  writeFileSync(join(buildDir, '1.0.0', 'index.html'), '<html>still live</html>');

  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived</html>');
  writeFileSync(
    join(archiveDir, '1.0.0.redirects.json'),
    JSON.stringify([{from: '/docs/1.0.0', to: '/docs/archive/1.0.0'}]),
  );

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () =>
      assembleSite({
        buildDir,
        javadocDir,
        archiveDir,
        siteDir,
        docsEntry: '/docs/next/',
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
  // The live page must survive untouched — the guard's whole purpose is to never overwrite it.
  assert.equal(
    readFileSync(join(siteDir, 'docs', '1.0.0', 'index.html'), 'utf8'),
    '<html>still live</html>',
  );
});

test('an archived version carried in releasedVersions still publishes its /javadoc/<v>/ surface, without becoming the /latest source', () => {
  // Mirrors what main() does in production: releasedVersions is the union of active AND archived
  // versions (via build-javadoc-versions.mjs's javadocBuildVersions), active-first — 1.0.0 has left
  // versions.json (archived) but its docs archive is carried forward, so its Javadoc must be too.
  const {root, buildDir, javadocDir, siteDir} = fixture();
  const javadocVersionsDir = join(root, 'javadoc-versions');
  for (const version of ['1.1.0', '1.0.0']) {
    mkdirSync(join(javadocVersionsDir, version), {recursive: true});
    writeFileSync(join(javadocVersionsDir, version, 'index.html'), `<html>javadoc ${version}</html>`);
  }
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived docs 1.0.0</html>');

  assembleSite({
    buildDir,
    javadocDir,
    javadocVersionsDir,
    releasedVersions: ['1.1.0', '1.0.0'],
    archiveDir,
    siteDir,
    docsEntry: '/docs/1.1.0/',
    customDomain: null,
  });

  assert.equal(
    readFileSync(join(siteDir, 'javadoc', '1.0.0', 'index.html'), 'utf8'),
    '<html>javadoc 1.0.0</html>',
  );
  assert.equal(
    readFileSync(join(siteDir, 'docs', 'archive', '1.0.0', 'index.html'), 'utf8'),
    '<html>archived docs 1.0.0</html>',
  );
  // Only releasedVersions[0] (the true newest ACTIVE version) sources /latest — an archived version
  // must never become the alias target.
  assert.equal(
    readFileSync(join(siteDir, 'javadoc', 'latest', 'index.html'), 'utf8'),
    '<html>javadoc 1.1.0</html>',
  );
});

test('writeRedirectStubs fails closed on a manifest entry that is not rooted at /docs/ or contains a `..` segment', () => {
  const {root, buildDir, javadocDir, siteDir} = fixture();
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived</html>');
  writeFileSync(
    join(archiveDir, '1.0.0.redirects.json'),
    JSON.stringify([{from: '/docs/1.0.0/../../../etc/passwd', to: '/docs/archive/1.0.0'}]),
  );

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () =>
      assembleSite({
        buildDir,
        javadocDir,
        archiveDir,
        siteDir,
        docsEntry: '/docs/next/',
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
  // The traversal segment must never be resolved into a real write outside siteDir.
  assert.ok(!existsSync(join(root, 'etc')));
});
