// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for the Pages artifact assembly. Fixture spa/build/javadoc directories under a
// temp root stand in for a real SPA build, a real Docusaurus build, and a real Javadoc surface —
// no build required.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {assembleSite} from './assemble-site.mjs';

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'assemble-'));
  const spaDir = join(root, 'spa');
  const buildDir = join(root, 'build');
  const javadocDir = join(root, 'javadoc-next');
  mkdirSync(spaDir, {recursive: true});
  writeFileSync(join(spaDir, 'index.html'), '<html>spa</html>');
  // The real SPA build ships dist/404.html byte-identical to dist/index.html (copy-404.mjs).
  writeFileSync(join(spaDir, '404.html'), '<html>spa</html>');
  mkdirSync(buildDir, {recursive: true});
  writeFileSync(join(buildDir, 'index.html'), '<html>docs</html>');
  mkdirSync(javadocDir, {recursive: true});
  writeFileSync(join(javadocDir, 'index.html'), '<html>javadoc</html>');
  return {root, spaDir, buildDir, javadocDir, archiveDir: join(root, 'archive-absent'), siteDir: join(root, '_site')};
}

test('composes the _site layout: docs/, javadoc/next, javadoc/latest', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(existsSync(join(siteDir, 'docs', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'next', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'latest', 'index.html')));
});

test('copies the SPA build to the site root, including its own index.html/404.html', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.equal(readFileSync(join(siteDir, 'index.html'), 'utf8'), '<html>spa</html>');
  assert.equal(readFileSync(join(siteDir, '404.html'), 'utf8'), '<html>spa</html>');
  assert.ok(existsSync(join(siteDir, '.nojekyll')));
});

test('SPA files coexist with docs/ and javadoc/ without collision', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(existsSync(join(siteDir, 'index.html')));
  assert.ok(existsSync(join(siteDir, 'docs', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'next', 'index.html')));
});

test('the composed docs/index.html is the real Docusaurus homepage, not the SPA shell or a placeholder', () => {
  // Regression guard for the /docs route-collision bug (agentforge4j-web-ui/src/pages/DocsPage.tsx,
  // removed): the SPA used to own a client-side route at /docs rendering a placeholder ("Continue
  // to the documentation ...") instead of ever letting a real request reach the composed
  // Docusaurus build mounted at this exact path. The composed docs/index.html must be the real
  // Docusaurus output — distinct from the SPA's own index.html — and must not carry the removed
  // placeholder's exact wording.
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const spaIndex = readFileSync(join(siteDir, 'index.html'), 'utf8');
  const docsIndex = readFileSync(join(siteDir, 'docs', 'index.html'), 'utf8');
  assert.notEqual(docsIndex, spaIndex);
  assert.doesNotMatch(docsIndex, /Continue to the documentation/);
});

test('fails closed when spaDir does not exist', () => {
  const {buildDir, javadocDir, archiveDir, siteDir} = fixture();
  const exitCodes = [];
  const originalExit = process.exit;
  process.exit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  try {
    assert.throws(
      () =>
        assembleSite({
          spaDir: join(archiveDir, 'does-not-exist'),
          buildDir,
          javadocDir,
          archiveDir,
          siteDir,
          customDomain: null,
        }),
      /exit\(1\)/,
    );
  } finally {
    process.exit = originalExit;
  }
  assert.deepEqual(exitCodes, [1]);
});

test('carries the archive forward when archive/ exists', () => {
  const {root, spaDir, buildDir, javadocDir, siteDir} = fixture();
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived</html>');
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(existsSync(join(siteDir, 'docs', 'archive', '1.0.0', 'index.html')));
});

test('does not create a docs/archive/ when no archive/ exists', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(!existsSync(join(siteDir, 'docs', 'archive')));
});

test('CNAME is absent unless a custom domain is passed', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(!existsSync(join(siteDir, 'CNAME')));
});

test('CNAME is written when a custom domain is passed', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: 'agentforge4j.org'});
  assert.equal(readFileSync(join(siteDir, 'CNAME'), 'utf8'), 'agentforge4j.org\n');
});

test('copies one version-pinned Javadoc surface per released version; /javadoc/latest mirrors the newest', () => {
  const {root, spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  const javadocVersionsDir = join(root, 'javadoc-versions');
  for (const version of ['1.1.0', '1.0.0']) {
    mkdirSync(join(javadocVersionsDir, version), {recursive: true});
    writeFileSync(join(javadocVersionsDir, version, 'index.html'), `<html>javadoc ${version}</html>`);
  }
  assembleSite({
    spaDir,
    buildDir,
    javadocDir,
    javadocVersionsDir,
    releasedVersions: ['1.1.0', '1.0.0'],
    archiveDir,
    siteDir,
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
  const {root, spaDir, buildDir, javadocDir, siteDir} = fixture();
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
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});

  const rootStub = readFileSync(join(siteDir, 'docs', '1.0.0', 'index.html'), 'utf8');
  assert.match(rootStub, /url=\/docs\/archive\/1\.0\.0\//);
  const nestedStub = readFileSync(
    join(siteDir, 'docs', '1.0.0', 'reference', 'config', 'index.html'),
    'utf8',
  );
  assert.match(nestedStub, /url=\/docs\/archive\/1\.0\.0\/reference\/config\//);
});

test('redirect stub collision guard fails closed instead of overwriting a live page', () => {
  const {root, spaDir, buildDir, javadocDir, siteDir} = fixture();
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
        spaDir,
        buildDir,
        javadocDir,
        archiveDir,
        siteDir,
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
  const {root, spaDir, buildDir, javadocDir, siteDir} = fixture();
  const javadocVersionsDir = join(root, 'javadoc-versions');
  for (const version of ['1.1.0', '1.0.0']) {
    mkdirSync(join(javadocVersionsDir, version), {recursive: true});
    writeFileSync(join(javadocVersionsDir, version, 'index.html'), `<html>javadoc ${version}</html>`);
  }
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived docs 1.0.0</html>');

  assembleSite({
    spaDir,
    buildDir,
    javadocDir,
    javadocVersionsDir,
    releasedVersions: ['1.1.0', '1.0.0'],
    archiveDir,
    siteDir,
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
  const {root, spaDir, buildDir, javadocDir, siteDir} = fixture();
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
        spaDir,
        buildDir,
        javadocDir,
        archiveDir,
        siteDir,
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
  // The traversal segment must never be resolved into a real write outside siteDir.
  assert.ok(!existsSync(join(root, 'etc')));
});

test('writeRedirectStubs fails closed on a manifest entry with an embedded backslash traversal segment', () => {
  const {root, spaDir, buildDir, javadocDir, siteDir} = fixture();
  const archiveDir = join(root, 'archive');
  mkdirSync(join(archiveDir, '1.0.0'), {recursive: true});
  writeFileSync(join(archiveDir, '1.0.0', 'index.html'), '<html>archived</html>');
  // A backslash-delimited segment must be rejected on its own terms — independent of whether the
  // host OS's path.join would actually resolve it outside siteDir (it only would on Windows; CI
  // runs Ubuntu, where `\` is not a separator) — the guard's own stated purpose is defense-in-depth
  // against a corrupted manifest, not just against what the current host happens to interpret.
  writeFileSync(
    join(archiveDir, '1.0.0.redirects.json'),
    JSON.stringify([{from: '/docs/1.0.0\\..\\..\\etc\\passwd', to: '/docs/archive/1.0.0'}]),
  );

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () =>
      assembleSite({
        spaDir,
        buildDir,
        javadocDir,
        archiveDir,
        siteDir,
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('verifyComposedArtifact fails closed when the composed output is missing an expected entry', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  // Simulate a copy step "succeeding" but producing a wrong/empty javadoc surface — an empty
  // directory, not a missing one, so the input-side requireDir(javadocDir, ...) check does not
  // catch it; only the output-side verification below can.
  mkdirSync(javadocDir.replace('javadoc-next', 'javadoc-next-empty'), {recursive: true});
  const emptyJavadocDir = javadocDir.replace('javadoc-next', 'javadoc-next-empty');

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () =>
      assembleSite({
        spaDir,
        buildDir,
        javadocDir: emptyJavadocDir,
        archiveDir,
        siteDir,
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('verifyComposedArtifact fails closed when an expected entry file exists but is empty', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  // Unlike the missing-entry case above, the file genuinely exists (requireDir/existsSync both
  // see it) — a copy step that silently wrote a truncated/zero-byte file would pass every check
  // except this one. Overwrite the fixture's javadoc index.html with empty content before
  // assembly: cpSync carries the zero-byte file straight through to the composed output.
  writeFileSync(join(javadocDir, 'index.html'), '');

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () =>
      assembleSite({
        spaDir,
        buildDir,
        javadocDir,
        archiveDir,
        siteDir,
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});
