// SPDX-License-Identifier: Apache-2.0
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildDocsEntry, resolveDocsEntryUrl } from './build-docs-entry.mjs';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DOCS_MODULE_ROOT = join(MODULE_ROOT, '..', 'agentforge4j-docs');

test('resolves the newest supported stable version once one exists', () => {
  assert.equal(resolveDocsEntryUrl({ versions: ['0.1.0'], lts: [] }), '/docs/0.1.0/');
});

test('follows a release automatically — a newer version becomes the entry point with no code change', () => {
  // The whole reason this is derived rather than written down: a hardcoded /docs/0.1.0/ would keep
  // pointing at a live-but-superseded tree the day 0.2.0 shipped, and nothing would fail.
  assert.equal(resolveDocsEntryUrl({ versions: ['0.2.0', '0.1.0'], lts: [] }), '/docs/0.2.0/');
});

test('an LTS designation does not hijack the entry point — `latest` is still the newest stable', () => {
  assert.equal(resolveDocsEntryUrl({ versions: ['0.3.0', '0.2.0', '0.1.0'], lts: ['0.1.0'] }), '/docs/0.3.0/');
});

test('pre-first-release (no released versions) resolves to /docs/next/, matching the docs redirect config', () => {
  assert.equal(resolveDocsEntryUrl({ versions: [], lts: [] }), '/docs/next/');
});

test('always the trailing-slash form GitHub Pages serves without a redirect', () => {
  for (const versions of [[], ['0.1.0'], ['9.9.9', '0.1.0']]) {
    assert.match(resolveDocsEntryUrl({ versions, lts: [] }), /\/$/);
  }
});

test('the resolved URL agrees with the docs module\'s own committed version lists — one lifecycle, not two', () => {
  const versions = JSON.parse(readFileSync(join(DOCS_MODULE_ROOT, 'versions.json'), 'utf8'));
  const expected = versions.length > 0 ? `/docs/${versions[0]}/` : '/docs/next/';
  assert.equal(resolveDocsEntryUrl(), expected);
});

test('NEGATIVE CONTROL — the resolved URL is never the bare /docs/ stub the site used to link', () => {
  // /docs/ is not a page: it is the client-redirect stub, and linking it was the defect.
  for (const versions of [[], ['0.1.0'], ['2.0.0', '1.0.0']]) {
    assert.notEqual(resolveDocsEntryUrl({ versions, lts: [] }), '/docs/');
  }
});

test('buildDocsEntry writes the resolved URL where nav.ts imports it from', () => {
  const outPath = join(mkdtempSync(join(tmpdir(), 'docs-entry-')), 'generated', 'docs-entry.json');
  const url = buildDocsEntry({ outPath });
  assert.deepEqual(JSON.parse(readFileSync(outPath, 'utf8')), { url });
  assert.match(url, /^\/docs\/.+\/$/);
});

// --- The version segment becomes a site-wide URL, so it is held to the same fail-closed standard
// assemble-site.mjs applies to its own committed manifests (`isSafeManifestPath`). ---

test('NEGATIVE CONTROL — a traversal segment is refused rather than spliced into every page\'s href', () => {
  assert.throws(() => resolveDocsEntryUrl({ versions: ['../evil'], lts: [] }), /refusing to build a docs entry URL/);
});

test('NEGATIVE CONTROL — a non-string entry is refused rather than stringified into the URL', () => {
  for (const bogus of [{ version: '0.1.0' }, 42, ['0.1.0']]) {
    assert.throws(() => resolveDocsEntryUrl({ versions: [bogus], lts: [] }), /refusing to build a docs entry URL/);
  }
});

test('a FALSY entry degrades to /docs/next/ rather than throwing — deliberately, so the two sides still agree', () => {
  // `docsEntryPath` is `(window && window.latest) || 'next'`, and it is the same function
  // docusaurus.config.ts drives its own `/` and `/latest` redirects from. A `null`/`""` entry
  // therefore makes the DOCS BUILD point at `next` too. Refusing it here would be the one outcome
  // this script exists to prevent: the site and the docs build disagreeing about where the current
  // documentation lives. The corrupt file still fails, in the docs build, where it is authoritative.
  for (const falsy of [null, '', 0]) {
    assert.equal(resolveDocsEntryUrl({ versions: [falsy], lts: [] }), '/docs/next/');
  }
});

test('a pre-release version string is accepted — the shape versions.json really can hold', () => {
  assert.equal(resolveDocsEntryUrl({ versions: ['1.0.0-rc.1'], lts: [] }), '/docs/1.0.0-rc.1/');
});
