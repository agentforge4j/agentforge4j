// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for verify-canonical.mjs against fixture build/ directories standing in for a real
// docusaurus build (mirrors verify-noindex.test.mjs's own fixture approach) — no real build required.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { verifyCanonicalTrailingSlash } from './verify-canonical.mjs';

/** A single-commit throwaway git repo, standing in for a real checkout's repo root — only the
 * repository's own shallow/full-ness matters to `verifyCanonicalTrailingSlash`'s `repoRoot` check,
 * never its content, so one commit is enough. */
function gitRepo() {
  const dir = mkdtempSync(join(tmpdir(), 'verify-canonical-git-'));
  execFileSync('git', ['init', '--quiet'], { cwd: dir });
  execFileSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: dir });
  execFileSync('git', ['config', 'user.name', 'Test'], { cwd: dir });
  writeFileSync(join(dir, 'a.txt'), 'a');
  execFileSync('git', ['add', '.'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'first'], { cwd: dir });
  return dir;
}

function sitemapFixtureWithLastmod(lastmod) {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>${lastmod}</lastmod></url></urlset>`,
  );
  return buildDir;
}

function pageWithCanonical(canonical) {
  return `<!doctype html><html><head><title>x</title><link data-rh=true rel=canonical href=${canonical} /></head><body></body></html>`;
}

function writePage(buildDir, relPath, canonical) {
  const full = join(buildDir, ...relPath.split('/'));
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, pageWithCanonical(canonical));
}

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'verify-canonical-'));
  return join(root, 'build');
}

test('passes clean when every canonical and every sitemap URL ends in a trailing slash, each with a valid lastmod', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writePage(buildDir, '0.1.0/get-started/quick-start/index.html', 'https://agentforge4j.org/docs/0.1.0/get-started/quick-start/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url>' +
      '<url><loc>https://agentforge4j.org/docs/0.1.0/get-started/quick-start/</loc><lastmod>2026-07-21</lastmod></url></urlset>',
  );
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir }));
});

test('fails closed on a non-trailing-slash canonical (the exact pre-fix defect: it 301-redirects on GitHub Pages)', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0'); // no trailing slash
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /does not end in '\/'/);
});

test('fails closed on a non-trailing-slash sitemap URL', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /sitemap URL .* does not end in/);
});

test('fails closed on a missing <lastmod> (the malformed/impossible-date variants are covered separately below)', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
});

test('fails closed on an impossible calendar date — day out of range for the month (2026-02-31)', () => {
  const buildDir = sitemapFixtureWithLastmod('2026-02-31');
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
});

test('fails closed on an impossible calendar date — month out of range (2026-13-01)', () => {
  const buildDir = sitemapFixtureWithLastmod('2026-13-01');
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
});

test('fails closed on an impossible calendar date — month zero (2026-00-10)', () => {
  const buildDir = sitemapFixtureWithLastmod('2026-00-10');
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
});

test('accepts a valid leap day (2024-02-29 — 2024 is a leap year)', () => {
  const buildDir = sitemapFixtureWithLastmod('2024-02-29');
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir }));
});

test('fails closed on an invalid leap day in a non-leap year (2026-02-29)', () => {
  const buildDir = sitemapFixtureWithLastmod('2026-02-29');
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
});

test('fails closed with a clear diagnostic when repoRoot is a shallow git clone', () => {
  const origin = gitRepo();
  writeFileSync(join(origin, 'b.txt'), 'b');
  execFileSync('git', ['add', '.'], { cwd: origin });
  execFileSync('git', ['commit', '--quiet', '-m', 'second'], { cwd: origin });

  const shallowParent = mkdtempSync(join(tmpdir(), 'verify-canonical-shallow-'));
  const shallowDir = join(shallowParent, 'clone');
  // A plain local-path clone silently ignores --depth ("local clones" are hardlinked, not
  // transport-fetched) — a file:// URL forces the real, transport-based (and thus genuinely
  // shallow) clone path, exercising the same code path a real CI/network clone would.
  execFileSync('git', ['clone', '--quiet', '--depth', '1', pathToFileURL(origin).href, shallowDir]);

  const buildDir = sitemapFixtureWithLastmod('2026-07-20');
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, repoRoot: shallowDir }),
    /is a shallow git clone/,
  );
});

test('does not fail closed on a full (non-shallow) git clone', () => {
  const repoRoot = gitRepo();
  const buildDir = sitemapFixtureWithLastmod('2026-07-20');
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir, repoRoot }));
});

test('archive-mode builds skip the shallow-repository check too (no git provenance requirement of their own)', () => {
  const shallowParent = mkdtempSync(join(tmpdir(), 'verify-canonical-archive-shallow-'));
  const origin = gitRepo();
  const shallowDir = join(shallowParent, 'clone');
  // A plain local-path clone silently ignores --depth ("local clones" are hardlinked, not
  // transport-fetched) — a file:// URL forces the real, transport-based (and thus genuinely
  // shallow) clone path, exercising the same code path a real CI/network clone would.
  execFileSync('git', ['clone', '--quiet', '--depth', '1', pathToFileURL(origin).href, shallowDir]);

  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  assert.doesNotThrow(() =>
    verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0', repoRoot: shallowDir }),
  );
});

test('fails closed when a <url> block does not match the expected shape (extra element after <loc>) — must not silently drop that entry from this check', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  // The <xhtml:link> element makes this block not match the strict per-<url> regex at all, even
  // though a real <loc> tag is plainly present — without the count cross-check this page would
  // silently drop out of the check instead of failing the build.
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc>' +
      '<xhtml:link rel="alternate" href="https://agentforge4j.org/docs/0.1.0/" /></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /has 1 <url> block\(s\) and 1 <loc> tag\(s\) but only 0 matched/);
});

test('fails closed when a <url> block has no <loc> at all (e.g. a typo\'d <location>) alongside one valid entry — a <loc>-only count comparison alone would miss this, since both sides would be zero for the malformed block', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writePage(buildDir, '0.1.0/other/index.html', 'https://agentforge4j.org/docs/0.1.0/other/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset>' +
      '<url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url>' +
      '<url><location>https://agentforge4j.org/docs/0.1.0/other/</location></url>' +
      '</urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /has 2 <url> block\(s\) and 1 <loc> tag\(s\) but only 1 matched/);
});

test('a page with no canonical tag at all, and no entry in the sitemap either (e.g. the search results page), is not itself a failure — only pages the sitemap actually advertises are checked', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  const searchPage = join(buildDir, 'search', 'index.html');
  mkdirSync(dirname(searchPage), { recursive: true });
  writeFileSync(searchPage, '<!doctype html><html><head><title>Search</title></head><body></body></html>');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir }));
});

test('fails closed when a sitemap-referenced page has no canonical tag at all (the exact bug this pass fixes: a regression removing canonicals from every page could previously pass silently)', () => {
  const buildDir = fixture();
  const page = join(buildDir, '0.1.0', 'index.html');
  mkdirSync(dirname(page), { recursive: true });
  writeFileSync(page, '<!doctype html><html><head><title>x</title></head><body></body></html>');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /has no canonical tag/);
});

test('fails closed when a sitemap-referenced page has more than one canonical tag', () => {
  const buildDir = fixture();
  const page = join(buildDir, '0.1.0', 'index.html');
  mkdirSync(dirname(page), { recursive: true });
  writeFileSync(
    page,
    '<!doctype html><html><head><title>x</title>' +
      '<link data-rh=true rel=canonical href=https://agentforge4j.org/docs/0.1.0/ />' +
      '<link data-rh=true rel=canonical href=https://agentforge4j.org/docs/0.1.0/other/ />' +
      '</head><body></body></html>',
  );
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /has 2 canonical tags — expected exactly one/);
});

test('fails closed when a sitemap-referenced page\'s canonical does not match its own sitemap URL at all (not just a trailing-slash difference)', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/wrong-page/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /does not match its own sitemap URL "https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/" exactly/);
});

test('fails closed when a sitemap URL has no generated page at all (a build integrity failure, not a page to silently skip)', () => {
  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /has no generated page at/);
});

test('archive-mode builds are skipped entirely (no moving alias / canonical policy of their own)', () => {
  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0' }));
});
