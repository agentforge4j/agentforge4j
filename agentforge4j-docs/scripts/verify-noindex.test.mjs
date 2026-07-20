// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for verify-noindex.mjs's own matching logic, against fixture HTML/sitemap files
// standing in for real docusaurus build output — no real build required. This does not (and
// cannot) prove the theme swizzles themselves render correctly; that requires a real
// `docusaurus build`, which package.json's own build script runs this script against directly.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { verifyNoindex } from './verify-noindex.mjs';

// The real generated tag (confirmed against a real `docusaurus build`): Docusaurus's head-tag
// renderer does not quote attribute values with no whitespace, and stamps a `data-rh` marker on
// every tag it dedupes — this is the actual shape verify-noindex.mjs must match, not the literal
// quoted JSX text the swizzle source writes.
const NOINDEX_TAG = '<meta data-rh=true name=robots content=noindex,follow />';
// A second, differently-quoted equivalent form, to prove the matcher is quote-agnostic rather than
// coincidentally tied to one exact rendering.
const NOINDEX_TAG_QUOTED = '<meta name="robots" content="noindex,follow" />';

function htmlWithRobots(tag = '') {
  return `<!doctype html><html><head><title>x</title>${tag}</head><body></body></html>`;
}

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-'));
  const buildDir = join(root, 'build');
  mkdirSync(join(buildDir, 'next', 'concepts', 'workflows'), { recursive: true });
  mkdirSync(join(buildDir, 'search'), { recursive: true });
  mkdirSync(join(buildDir, '0.1.0', 'concepts', 'workflows'), { recursive: true });

  writeFileSync(join(buildDir, 'next', 'index.html'), htmlWithRobots(NOINDEX_TAG));
  writeFileSync(join(buildDir, 'next', 'concepts', 'workflows', 'index.html'), htmlWithRobots(NOINDEX_TAG));
  writeFileSync(join(buildDir, 'search', 'index.html'), htmlWithRobots(NOINDEX_TAG));
  writeFileSync(join(buildDir, '0.1.0', 'index.html'), htmlWithRobots());
  writeFileSync(join(buildDir, '0.1.0', 'concepts', 'workflows', 'index.html'), htmlWithRobots());
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc></url></urlset>',
  );
  return buildDir;
}

test('passes when /next and /search are noindex,follow, /0.1.0 is not, and the sitemap excludes /next and /search', () => {
  const buildDir = fixture();
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: null }));
});

test('also matches a fully-quoted robots tag (quote-agnostic, not tied to one exact rendering)', () => {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-quoted-'));
  const buildDir = join(root, 'build');
  mkdirSync(join(buildDir, 'next', 'concepts', 'workflows'), { recursive: true });
  mkdirSync(join(buildDir, 'search'), { recursive: true });
  mkdirSync(join(buildDir, '0.1.0', 'concepts', 'workflows'), { recursive: true });
  writeFileSync(join(buildDir, 'next', 'index.html'), htmlWithRobots(NOINDEX_TAG_QUOTED));
  writeFileSync(join(buildDir, 'next', 'concepts', 'workflows', 'index.html'), htmlWithRobots(NOINDEX_TAG_QUOTED));
  writeFileSync(join(buildDir, 'search', 'index.html'), htmlWithRobots(NOINDEX_TAG_QUOTED));
  writeFileSync(join(buildDir, '0.1.0', 'index.html'), htmlWithRobots());
  writeFileSync(join(buildDir, '0.1.0', 'concepts', 'workflows', 'index.html'), htmlWithRobots());
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc></url></urlset>',
  );
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: null }));
});

test('fails closed when the /next landing page is missing the noindex tag', () => {
  const buildDir = fixture();
  writeFileSync(join(buildDir, 'next', 'index.html'), htmlWithRobots());
  assert.throws(() => verifyNoindex({ buildDir, archiveVersion: null }), /expected next\/index\.html to contain/);
});

test('fails closed when a nested /next/** page is missing the noindex tag', () => {
  const buildDir = fixture();
  writeFileSync(join(buildDir, 'next', 'concepts', 'workflows', 'index.html'), htmlWithRobots());
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null }),
    /expected next\/concepts\/workflows\/index\.html to contain/,
  );
});

test('fails closed when the search page is missing the noindex tag', () => {
  const buildDir = fixture();
  writeFileSync(join(buildDir, 'search', 'index.html'), htmlWithRobots());
  assert.throws(() => verifyNoindex({ buildDir, archiveVersion: null }), /expected search\/index\.html to contain/);
});

test('fails closed when the stable 0.1.0 landing page is incorrectly noindex', () => {
  const buildDir = fixture();
  writeFileSync(join(buildDir, '0.1.0', 'index.html'), htmlWithRobots(NOINDEX_TAG));
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null }),
    /expected 0\.1\.0\/index\.html to NOT be noindex/,
  );
});

test('fails closed when a nested stable 0.1.0 page is incorrectly noindex', () => {
  const buildDir = fixture();
  writeFileSync(join(buildDir, '0.1.0', 'concepts', 'workflows', 'index.html'), htmlWithRobots(NOINDEX_TAG));
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null }),
    /expected 0\.1\.0\/concepts\/workflows\/index\.html to NOT be noindex/,
  );
});

test('fails closed when the sitemap unexpectedly includes /docs/next/', () => {
  const buildDir = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/next/</loc></url></urlset>',
  );
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null }),
    /unexpectedly present in the generated sitemap/,
  );
});

test('fails closed when the sitemap unexpectedly includes /docs/search', () => {
  const buildDir = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/search</loc></url></urlset>',
  );
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null }),
    /unexpectedly present in the generated sitemap/,
  );
});

test('throws when the build directory does not exist', () => {
  const missing = join(mkdtempSync(join(tmpdir(), 'verify-noindex-missing-')), 'build');
  assert.throws(() => verifyNoindex({ buildDir: missing, archiveVersion: null }), /does not exist/);
});

test('skips all checks in archive mode, where /next and /0.1.0 do not exist', () => {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-archive-'));
  const buildDir = join(root, 'build');
  mkdirSync(buildDir, { recursive: true });
  writeFileSync(join(buildDir, 'index.html'), htmlWithRobots());
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: '1.0.0' }));
});
