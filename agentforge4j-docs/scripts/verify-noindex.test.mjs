// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for verify-noindex.mjs's own inventory-driven matching logic, against fixture
// HTML/sitemap files standing in for real docusaurus build output — no real build required. This
// does not (and cannot) prove the theme swizzles themselves render correctly; that requires a
// real `docusaurus build`, which package.json's own build script runs this script against
// directly.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
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

function writePage(buildDir, relativePath, tag = '') {
  const full = join(buildDir, ...relativePath.split('/'));
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, htmlWithRobots(tag));
}

/**
 * @param {{nextPages?: string[], searchPages?: string[], versions?: Record<string, string[]>, noindexTag?: string}} [options]
 */
function fixture({
  nextPages = ['index.html', 'concepts/workflows/index.html'],
  searchPages = ['index.html'],
  versions = { '0.1.0': ['index.html', 'concepts/workflows/index.html'] },
  noindexTag = NOINDEX_TAG,
} = {}) {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-'));
  const buildDir = join(root, 'build');

  for (const page of nextPages) {
    writePage(buildDir, `next/${page}`, noindexTag);
  }
  for (const page of searchPages) {
    writePage(buildDir, `search/${page}`, noindexTag);
  }
  for (const [version, pages] of Object.entries(versions)) {
    for (const page of pages) {
      writePage(buildDir, `${version}/${page}`);
    }
  }
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `<urlset>${Object.keys(versions)
      .map((version) => `<url><loc>https://agentforge4j.org/docs/${version}/</loc></url>`)
      .join('')}</urlset>`,
  );
  return buildDir;
}

test('passes with multiple /next pages, /search, one active version, and a correct sitemap', () => {
  const buildDir = fixture();
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }));
});

test('passes with an arbitrary, differently-named nested /next page (not hardcoded to concepts/workflows)', () => {
  const buildDir = fixture({
    nextPages: ['index.html', 'tutorials/a-brand-new-guide/index.html', 'reference/renamed-page.html'],
    versions: { '0.1.0': ['index.html', 'some/other/nested/page.html'] },
  });
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }));
});

test('passes with multiple active released versions simultaneously', () => {
  const buildDir = fixture({
    versions: {
      '0.1.0': ['index.html', 'concepts/workflows/index.html'],
      '1.0.0': ['index.html', 'concepts/workflows/index.html'],
      '2.3.1': ['index.html'],
    },
  });
  assert.doesNotThrow(() =>
    verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0', '1.0.0', '2.3.1'] }),
  );
});

test('also matches a fully-quoted robots tag (quote-agnostic, not tied to one exact rendering)', () => {
  const buildDir = fixture({ noindexTag: NOINDEX_TAG_QUOTED });
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }));
});

test('fails closed when any /next page is missing noindex,follow, however it is named', () => {
  const buildDir = fixture({
    nextPages: ['index.html', 'how-to/some-renamed-topic/index.html'],
  });
  writeFileSync(join(buildDir, 'next', 'how-to', 'some-renamed-topic', 'index.html'), htmlWithRobots());
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }),
    /expected .*how-to.*some-renamed-topic.*index\.html to contain/,
  );
});

test('fails closed when the search page is missing noindex,follow', () => {
  const buildDir = fixture();
  writeFileSync(join(buildDir, 'search', 'index.html'), htmlWithRobots());
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }),
    /expected .*search.*index\.html to contain/,
  );
});

test('fails closed when an active released version page is accidentally noindex', () => {
  const buildDir = fixture({
    versions: { '0.1.0': ['index.html'], '1.0.0': ['index.html', 'concepts/workflows/index.html'] },
  });
  writeFileSync(join(buildDir, '1.0.0', 'concepts', 'workflows', 'index.html'), htmlWithRobots(NOINDEX_TAG));
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0', '1.0.0'] }),
    /expected .*1\.0\.0.*concepts.*workflows.*index\.html to NOT be noindex/,
  );
});

test('fails clearly when an active version listed as active has no matching build output directory at all', () => {
  const buildDir = fixture({ versions: { '0.1.0': ['index.html'] } });
  // '9.9.9' is "active" per this call's activeVersions but was never actually built.
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0', '9.9.9'] }),
    /expected directory does not exist.*9\.9\.9/,
  );
});

test('fails closed (vacuous-pass guard) when /next exists but has no generated HTML pages under it', () => {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-empty-next-'));
  const buildDir = join(root, 'build');
  mkdirSync(join(buildDir, 'next'), { recursive: true }); // exists, but empty
  writePage(buildDir, 'search/index.html', NOINDEX_TAG);
  writePage(buildDir, '0.1.0/index.html');
  writeFileSync(join(buildDir, 'sitemap.xml'), '<urlset></urlset>');
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }),
    /no generated HTML pages found/,
  );
});

test('fails closed when the sitemap unexpectedly includes /docs/next/', () => {
  const buildDir = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/next/</loc></url></urlset>',
  );
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }),
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
    () => verifyNoindex({ buildDir, archiveVersion: null, activeVersions: ['0.1.0'] }),
    /unexpectedly present in the generated sitemap/,
  );
});

test('throws when the build directory does not exist', () => {
  const missing = join(mkdtempSync(join(tmpdir(), 'verify-noindex-missing-')), 'build');
  assert.throws(() => verifyNoindex({ buildDir: missing, archiveVersion: null }), /does not exist/);
});

test('skips all checks in archive mode, regardless of /next or version contents', () => {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-archive-'));
  const buildDir = join(root, 'build');
  mkdirSync(buildDir, { recursive: true });
  writeFileSync(join(buildDir, 'index.html'), htmlWithRobots());
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: '1.0.0', activeVersions: ['0.1.0'] }));
});

test('derives active versions from a real versions.json file by default, not just from an explicit override', () => {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-versionsjson-'));
  const buildDir = join(root, 'build');
  writeFileSync(join(root, 'versions.json'), JSON.stringify(['0.1.0']));
  writePage(buildDir, 'next/index.html', NOINDEX_TAG);
  writePage(buildDir, 'search/index.html', NOINDEX_TAG);
  writePage(buildDir, '0.1.0/index.html');
  writeFileSync(join(buildDir, 'sitemap.xml'), '<urlset></urlset>');
  // No `activeVersions` override, and `moduleRoot` points at this fixture's own root — proves the
  // versions are actually read from *this* versions.json, not merely accepted as a passed-in list.
  assert.doesNotThrow(() => verifyNoindex({ buildDir, archiveVersion: null, moduleRoot: root }));
});

test('fails closed when versions.json lists a version whose build output was never produced, using the real versions.json-derived default', () => {
  const root = mkdtempSync(join(tmpdir(), 'verify-noindex-versionsjson-missing-'));
  const buildDir = join(root, 'build');
  writeFileSync(join(root, 'versions.json'), JSON.stringify(['0.1.0', '2.0.0']));
  writePage(buildDir, 'next/index.html', NOINDEX_TAG);
  writePage(buildDir, 'search/index.html', NOINDEX_TAG);
  writePage(buildDir, '0.1.0/index.html');
  // '2.0.0' deliberately not built.
  writeFileSync(join(buildDir, 'sitemap.xml'), '<urlset></urlset>');
  assert.throws(
    () => verifyNoindex({ buildDir, archiveVersion: null, moduleRoot: root }),
    /expected directory does not exist.*2\.0\.0/,
  );
});
