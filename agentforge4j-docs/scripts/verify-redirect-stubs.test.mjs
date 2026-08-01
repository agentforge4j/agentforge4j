// SPDX-License-Identifier: Apache-2.0
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { verifyRedirectStubs } from './verify-redirect-stubs.mjs';

/** The plugin's real stub shape, as captured in redirect-stub-seo.test.mjs. */
const STUB = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8">
    <meta http-equiv="refresh" content="0; url=/docs/0.1.0/">
    <link rel="canonical" href="/docs/0.1.0/" />
  </head>
</html>
`;

const ORDINARY = '<!DOCTYPE html><html><head><title>Get started</title></head><body><h1>Get started</h1></body></html>';

function buildFixture(files) {
  const buildDir = mkdtempSync(join(tmpdir(), 'verify-stubs-'));
  for (const [relPath, content] of Object.entries(files)) {
    const segments = relPath.split('/');
    mkdirSync(join(buildDir, ...segments.slice(0, -1)), { recursive: true });
    writeFileSync(join(buildDir, ...segments), content, 'utf8');
  }
  return buildDir;
}

test('recognises the / and /latest stubs the redirects plugin always emits', () => {
  const buildDir = buildFixture({
    'index.html': STUB,
    'latest/index.html': STUB,
    '0.1.0/index.html': ORDINARY,
  });
  assert.deepEqual(verifyRedirectStubs({ buildDir }), ['/docs/0.1.0/', '/docs/0.1.0/']);
});

test('NEGATIVE CONTROL — a build whose stubs no longer match the recognition rule fails the BUILD, not the deploy', () => {
  // This is the whole point of the gate: assemble-site.mjs (which does the labelling) runs only in
  // the deploy workflow, so without this the same drift lands on main and breaks the site publish.
  const drifted = STUB
    .replace('<meta http-equiv="refresh" content="0; url=/docs/0.1.0/">', "<meta http-equiv='refresh' content='0; url=/docs/0.1.0/'>");
  const buildDir = buildFixture({ 'index.html': drifted, '0.1.0/index.html': ORDINARY });
  assert.throws(() => verifyRedirectStubs({ buildDir }), /recognised no client-redirect stubs/);
});

test('NEGATIVE CONTROL — attribute reordering is drift too, and is caught', () => {
  const reordered = STUB.replace(
    '<meta http-equiv="refresh" content="0; url=/docs/0.1.0/">',
    '<meta content="0; url=/docs/0.1.0/" http-equiv="refresh">',
  );
  const buildDir = buildFixture({ 'index.html': reordered, '0.1.0/index.html': ORDINARY });
  assert.throws(() => verifyRedirectStubs({ buildDir }), /recognised no client-redirect stubs/);
});

test('an ordinary docs build with no stubs at all is a failure, not a pass', () => {
  const buildDir = buildFixture({ '0.1.0/index.html': ORDINARY });
  assert.throws(() => verifyRedirectStubs({ buildDir }), /recognised no client-redirect stubs/);
});

test('archive-mode builds are skipped — docusaurus.config.ts drops the redirects plugin entirely there', () => {
  const buildDir = buildFixture({ 'index.html': ORDINARY });
  assert.deepEqual(verifyRedirectStubs({ buildDir, archiveVersion: '0.1.0' }), []);
});

test('a missing build directory is reported as such rather than read as "no stubs"', () => {
  assert.throws(
    () => verifyRedirectStubs({ buildDir: join(tmpdir(), 'verify-stubs-absent-directory') }),
    /does not exist — run "docusaurus build" first/,
  );
});
