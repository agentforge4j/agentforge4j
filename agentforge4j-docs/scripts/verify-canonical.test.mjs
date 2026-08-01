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

test("the missing-<lastmod> diagnostic names the two causes that are NOT a plugin regression: a route with no source file of its own (a category generated-index or tag page, which can never have a git-derived date), and a source file that exists but is not committed yet (a freshly cut versioned_docs snapshot)", () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc></url></urlset>',
  );
  // Docusaurus attaches `sourceFilePath` only to real doc routes (plugin-content-docs' own
  // createDocRouteMetadata), so these two route kinds legitimately produce no <lastmod> at all —
  // an author hitting this must be pointed at the route, not sent hunting through git history.
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /generated-index/);
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /tag page/);
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /ignorePatterns/);
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /not committed yet/);
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /commit the cut before building/);
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

test('fails closed on a real calendar day that lies in the FUTURE — a commit cannot be newer than the build reading it, so this is a skewed clock or an overridden commit date, not a lastmod', () => {
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const buildDir = sitemapFixtureWithLastmod(tomorrow);
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
  // Computed rather than hardcoded: a fixed far-future date would stop testing anything the day it
  // arrives, and one day out is the smallest violation the check must still catch.
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /lies in the future/);
});

test("accepts today's own date — the boundary is 'not in the future', not 'strictly in the past': a page whose source was committed earlier today is entirely normal", () => {
  const buildDir = sitemapFixtureWithLastmod(new Date().toISOString().slice(0, 10));
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir }));
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

test('fails closed naming the actual precondition when the tree is not a git checkout at all (an extracted tarball, a .git-less container context) — not with a shallow-clone diagnostic the reader cannot act on', () => {
  // A bare temp directory: real, and genuinely outside any repository, so `git rev-parse` fails for
  // a reason that has nothing to do with clone depth. The build has no fallback for this — every
  // <lastmod> comes from git history — so it must say so in its own terms.
  const notARepo = mkdtempSync(join(tmpdir(), 'verify-canonical-not-a-repo-'));
  const buildDir = sitemapFixtureWithLastmod('2026-07-20');
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, repoRoot: notARepo }),
    /is not inside a git repository/,
  );
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, repoRoot: notARepo }),
    /real git checkout/,
  );
});

test('archive-mode builds still fail closed on a shallow git clone — archive-mode config derives <lastmod> from git history exactly like the live site does, and the archive is frozen forever once committed', () => {
  const shallowParent = mkdtempSync(join(tmpdir(), 'verify-canonical-archive-shallow-'));
  const origin = gitRepo();
  const shallowDir = join(shallowParent, 'clone');
  // A plain local-path clone silently ignores --depth ("local clones" are hardlinked, not
  // transport-fetched) — a file:// URL forces the real, transport-based (and thus genuinely
  // shallow) clone path, exercising the same code path a real CI/network clone would.
  execFileSync('git', ['clone', '--quiet', '--depth', '1', pathToFileURL(origin).href, shallowDir]);

  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0', repoRoot: shallowDir }),
    /is a shallow git clone/,
  );
});

test('archive-mode builds still validate lastmod against the real generated sitemap.xml — passes when every entry has a valid lastmod', () => {
  const repoRoot = gitRepo();
  // No writePage()/canonical HTML at all: proves the canonical-tag/page-existence checks are
  // genuinely skipped for archive mode (this fixture has no pages, only a sitemap.xml) — the
  // *lastmod* check below still ran, since that's exactly what must not be skipped.
  const buildDir = sitemapFixtureWithLastmod('2026-07-20');
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0', repoRoot }));
});

test('archive-mode builds fail closed on a missing <lastmod> in the real generated sitemap.xml, even with full git history (a shallow-history precondition alone is not sufficient — Docusaurus can still legitimately omit lastmod for one untracked file)', () => {
  const repoRoot = gitRepo();
  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/archive/1.0.0/</loc></url></urlset>',
  );
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0', repoRoot }),
    /no valid <lastmod>/,
  );
});

test('archive-mode builds fail closed on an impossible calendar date in the real generated sitemap.xml, even with full git history', () => {
  const repoRoot = gitRepo();
  const buildDir = sitemapFixtureWithLastmod('2026-02-31');
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0', repoRoot }),
    /no valid <lastmod>/,
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

test('archive-mode builds skip the CANONICAL-TAG/page-existence checks (an archive owns no moving alias or canonical policy of its own) when run against this real, non-shallow repo, with a valid generated sitemap.xml', () => {
  const buildDir = sitemapFixtureWithLastmod('2026-07-20');
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0' }));
});

test("archive-mode builds do NOT skip the sitemap-URL trailing-slash check — that check runs before the archive-mode short-circuit, so `trailingSlash: true` is enforced on an archive's own sitemap exactly as on the live site's", () => {
  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/archive/1.0.0/get-started</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(
    () => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0' }),
    /does not end in '\/'/,
  );
});
