// SPDX-License-Identifier: Apache-2.0
//
// The fixture below is the REAL shape @docusaurus/plugin-client-redirects publishes — captured from
// the live site's own /docs/ on 2026-07-27, angle brackets and all. Testing against a tidied-up
// approximation would prove nothing about the page that actually ships: no <title>, no robots, a
// relative canonical, and no <body> element at all.
//
// The recognition rule that fixture stands in for is separately asserted against the plugin's REAL
// emitted output by scripts/verify-redirect-stubs.mjs, which runs inside `npm run build` — a captured
// fixture cannot notice that the thing it was captured from has changed.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { applyRedirectStubSeo } from './assemble-site.mjs';
import {
  classifyRedirectTarget,
  injectRedirectStubSeo,
  redirectStubCopy,
  redirectStubTarget,
} from './redirect-stub-seo.mjs';

const REAL_STUB = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8">
    <meta http-equiv="refresh" content="0; url=/docs/0.1.0/">
    <link rel="canonical" href="/docs/0.1.0/" />
  </head>
  <script>
    window.location.href = '/docs/0.1.0/' + window.location.search + window.location.hash;
  </script>
</html>
`;

const OPTIONS = { robots: 'noindex, follow' };

test('redirectStubTarget reads the destination out of the real stub shape', () => {
  assert.equal(redirectStubTarget(REAL_STUB), '/docs/0.1.0/');
});

test('redirectStubTarget returns null for an ordinary page, so the walk cannot mistake one for a stub', () => {
  assert.equal(redirectStubTarget('<!DOCTYPE html><html><head><title>Real page</title></head><body>x</body></html>'), null);
});

test('the stub gains a real title and description — it shipped with neither', () => {
  assert.doesNotMatch(REAL_STUB, /<title>/);
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.match(html, /<title>Documentation — AgentForge4j<\/title>/);
  assert.match(html, /<meta name="description" content="This address forwards to the current AgentForge4j documentation\. Follow the link to the current version\.">/);
});

test('the stub becomes explicitly non-indexable, with follow so the destination link still carries signal', () => {
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.match(html, /<meta name="robots" content="noindex, follow">/);
});

test("the plugin's own canonical is left exactly as written — same policy javadoc-seo applies to ITS redirect shells", () => {
  // A site with two redirect-shell policies has no policy. javadoc-seo.mjs leaves the plugin
  // canonical on `overview-summary.html` untouched (relative `href="index.html"`), asserted in
  // assemble-site.test.mjs; this module must not do the opposite for structurally identical pages.
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.match(html, /<link rel="canonical" href="\/docs\/0\.1\.0\/" \/>/);
  assert.equal((html.match(/rel="canonical"/g) ?? []).length, 1);
});

test('NEGATIVE CONTROL — no absolute canonical is synthesised, and the site origin never appears', () => {
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.doesNotMatch(html, /https:\/\/agentforge4j\.org/);
});

test('the redirect behaviour itself is untouched — this labels the stub, it does not change where it goes', () => {
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.match(html, /<meta http-equiv="refresh" content="0; url=\/docs\/0\.1\.0\/">/);
  assert.match(html, /window\.location\.href = '\/docs\/0\.1\.0\/' \+ window\.location\.search \+ window\.location\.hash;/);
});

test('an already-labelled stub is returned untouched rather than gaining a second robots directive', () => {
  const once = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.equal(injectRedirectStubSeo(once, OPTIONS), once);
});

test('a non-stub page passed in by mistake fails loudly rather than being silently labelled', () => {
  assert.throws(() => injectRedirectStubSeo('<html><head></head><body>real page</body></html>', OPTIONS), /not a client-redirect stub/);
});

test('a destination containing an HTML entity is never re-emitted, so it cannot be escaped a second time', () => {
  // The earlier version built a canonical out of the target and escaped it, which turned an
  // already-escaped `/docs/a&amp;b/` into `/docs/a&amp;amp;b/`. Nothing read out of the stub is
  // written back now.
  const escaped = REAL_STUB.replace(/url=\/docs\/0\.1\.0\//, 'url=/docs/a&amp;b/');
  const html = injectRedirectStubSeo(escaped, OPTIONS);
  assert.doesNotMatch(html, /&amp;amp;/);
  assert.match(html, /<meta http-equiv="refresh" content="0; url=\/docs\/a&amp;b\/">/);
});

// --- Copy follows the destination. ---

test('redirectStubCopy: a stub to the live tree says it forwards to the current documentation', () => {
  const copy = redirectStubCopy('/docs/0.1.0/');
  assert.match(copy.title, /^Documentation —/);
  assert.match(copy.description, /current AgentForge4j documentation/);
});

test('redirectStubCopy: a stub to the archive says so, instead of claiming to forward to the current docs', () => {
  const copy = redirectStubCopy('/docs/archive/0.1.0/get-started/');
  assert.match(copy.title, /^Archived documentation —/);
  assert.match(copy.description, /has been archived/);
  assert.doesNotMatch(copy.description, /forwards to the current/);
});

test('NEGATIVE CONTROL — an archive stub does not receive the current-documentation copy', () => {
  const html = injectRedirectStubSeo(archiveRedirectStub('/docs/archive/0.1.0/'), OPTIONS);
  assert.match(html, /<title>Archived documentation — AgentForge4j<\/title>/);
  assert.doesNotMatch(html, /This address forwards to the current AgentForge4j documentation/);
});

test('a deep archive stub — not just the archived version root — gets the archive copy too', () => {
  // writeRedirectStubs emits one stub per PAGE ROUTE of the archived version, not one per version.
  const html = injectRedirectStubSeo(archiveRedirectStub('/docs/archive/0.1.0/reference/config/'), OPTIONS);
  assert.match(html, /<title>Archived documentation — AgentForge4j<\/title>/);
});

// --- Duplicate-tag invariant, for every tag written, not just the one that failed first. ---

test('a React-Helmet-shaped description tag is REPLACED, not duplicated', () => {
  // `<meta data-rh="true" name="description" …>` is the shape verify-noindex.mjs documents as what a
  // real generated tag looks like. A `name=`-first pattern misses it and appends a second tag.
  const helmet = REAL_STUB.replace(
    '<link rel="canonical"',
    '<meta data-rh="true" name="description" content="stale"><link rel="canonical"',
  );
  const html = injectRedirectStubSeo(helmet, OPTIONS);
  assert.equal((html.match(/<meta[^>]*\sname="description"[^>]*>/gi) ?? []).length, 1);
  assert.doesNotMatch(html, /stale/);
});

test('a React-Helmet-shaped robots tag is recognised as already-labelled, not duplicated', () => {
  const helmet = REAL_STUB.replace(
    '<link rel="canonical"',
    '<meta data-rh="true" name="robots" content="noindex,follow"><link rel="canonical"',
  );
  const html = injectRedirectStubSeo(helmet, OPTIONS);
  assert.equal(html, helmet, 'an already-noindexed stub must be left alone');
  assert.equal((html.match(/<meta[^>]*\sname="robots"[^>]*>/gi) ?? []).length, 1);
});

// --- The walk over a composed artifact. ---

function fixtureSite({ stubs = ['docs/index.html', 'docs/latest/index.html'], ordinaryPages = ['docs/0.1.0/index.html'] } = {}) {
  const siteDir = mkdtempSync(join(tmpdir(), 'redirect-stub-'));
  for (const relPath of stubs) {
    mkdirSync(join(siteDir, ...relPath.split('/').slice(0, -1)), { recursive: true });
    writeFileSync(join(siteDir, ...relPath.split('/')), REAL_STUB, 'utf8');
  }
  for (const relPath of ordinaryPages) {
    mkdirSync(join(siteDir, ...relPath.split('/').slice(0, -1)), { recursive: true });
    writeFileSync(
      join(siteDir, ...relPath.split('/')),
      '<!DOCTYPE html><html><head><title>Get started</title></head><body><h1>Get started</h1></body></html>',
      'utf8',
    );
  }
  return siteDir;
}

test('every stub in the composed artifact is labelled, and ordinary docs pages are left alone', () => {
  const siteDir = fixtureSite();
  const { recognised, updated } = applyRedirectStubSeo(siteDir, () => {
    throw new Error('should not have exited');
  });
  assert.equal(recognised, 2);
  assert.equal(updated, 2);
  for (const relPath of ['docs/index.html', 'docs/latest/index.html']) {
    const html = readFileSync(join(siteDir, ...relPath.split('/')), 'utf8');
    assert.match(html, /<meta name="robots" content="noindex, follow">/);
    assert.match(html, /<title>Documentation — AgentForge4j<\/title>/);
  }
  const ordinary = readFileSync(join(siteDir, 'docs', '0.1.0', 'index.html'), 'utf8');
  assert.equal(ordinary, '<!DOCTYPE html><html><head><title>Get started</title></head><body><h1>Get started</h1></body></html>');
});

test('NEGATIVE CONTROL — an artifact where no stub is recognized fails closed instead of silently labelling nothing', () => {
  // Exactly what a plugin template change would look like: the stubs are still there and still
  // shipping raw, but nothing matches them any more.
  const siteDir = fixtureSite({ stubs: [], ordinaryPages: ['docs/index.html'] });
  const exits = [];
  applyRedirectStubSeo(siteDir, (code) => exits.push(code));
  assert.deepEqual(exits, [1]);
});

test('NEGATIVE CONTROL — an already-labelled artifact is a no-op, NOT a recognition failure', () => {
  // The guard keys on recognised, not on rewritten. Keying it on rewritten made a second run over a
  // labelled artifact report that the recognition rule had broken — the one thing that had certainly
  // not happened, since it had just recognised every stub.
  const siteDir = fixtureSite();
  const bomb = () => {
    throw new Error('should not have exited');
  };
  applyRedirectStubSeo(siteDir, bomb);
  const second = applyRedirectStubSeo(siteDir, bomb);
  assert.equal(second.recognised, 2, 'both stubs are still recognised on the second pass');
  assert.equal(second.updated, 0, 'and neither needs rewriting');
});

// --- Archive redirect stubs. assemble-site.mjs's own `writeRedirectStubs` (step 4) emits stubs for
// every archived version's old address, into the SAME /docs/ tree this pass walks in step 8 — and
// unlike the redirects plugin's stubs, those already carry a <title>. An unconditional append gave
// each of them two. The fixture below is byte-for-byte `redirectHtml()`'s output. ---

/** Byte-for-byte the output of assemble-site.mjs's `redirectHtml(to)`, which `writeRedirectStubs`
 * writes for every archived version's old active address. Reproduced here rather than imported
 * because `redirectHtml` is module-private — if it changes shape, `redirectStubTarget` below stops
 * recognising this and the first assertion fails, which is the signal we want. */
function archiveRedirectStub(to = '/docs/archive/0.1.0/') {
  return (
    `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">` +
    `<meta http-equiv="refresh" content="0; url=${to}">` +
    `<link rel="canonical" href="${to}"><title>AgentForge4j</title></head>` +
    `<body><a href="${to}">Continue to the documentation</a></body></html>\n`
  );
}

test('an archive redirect stub is recognised by the same rule as the plugin stubs — it is one too', () => {
  assert.equal(redirectStubTarget(archiveRedirectStub()), '/docs/archive/0.1.0/');
});

test('NEGATIVE CONTROL — an archive stub that ALREADY has a <title> ends up with exactly one, not two', () => {
  const raw = archiveRedirectStub();
  assert.equal((raw.match(/<title>/gi) ?? []).length, 1, 'the fixture must start with one title');
  const html = injectRedirectStubSeo(raw, OPTIONS);
  assert.equal((html.match(/<title>/gi) ?? []).length, 1);
  // The existing title is REPLACED by the intentional one, not left beside it.
  assert.match(html, /<title>Archived documentation — AgentForge4j<\/title>/);
  assert.doesNotMatch(html, /<title>AgentForge4j<\/title>/);
});

test('an archive stub keeps its own canonical exactly as redirectHtml wrote it', () => {
  const html = injectRedirectStubSeo(archiveRedirectStub(), OPTIONS);
  assert.equal((html.match(/rel="canonical"/gi) ?? []).length, 1);
  assert.match(html, /<link rel="canonical" href="\/docs\/archive\/0\.1\.0\/">/);
});

test('an archive stub keeps its own redirect behaviour and body untouched', () => {
  const html = injectRedirectStubSeo(archiveRedirectStub(), OPTIONS);
  assert.match(html, /<meta http-equiv="refresh" content="0; url=\/docs\/archive\/0\.1\.0\/">/);
  assert.match(html, /<a href="\/docs\/archive\/0\.1\.0\/">Continue to the documentation<\/a>/);
});

test('a stub with an existing description has it replaced rather than duplicated too', () => {
  const withDescription = archiveRedirectStub().replace(
    '<title>AgentForge4j</title>',
    '<meta name="description" content="an older description"><title>AgentForge4j</title>',
  );
  const html = injectRedirectStubSeo(withDescription, OPTIONS);
  assert.equal((html.match(/name="description"/gi) ?? []).length, 1);
  assert.doesNotMatch(html, /an older description/);
});

test('the plugin stub path is unchanged — no title to replace, so one is inserted', () => {
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.equal((html.match(/<title>/gi) ?? []).length, 1);
});

// --- Redirect-target classification. `startsWith('http')` called `httpfoo/bar` absolute, and the
// archive-vs-current copy decision below reads the destination as a PATH, so what counts as one
// still has to be decided by parsing rather than by prefix. ---

test('classifyRedirectTarget: site-relative paths', () => {
  for (const value of ['/docs/0.1.0/', '/docs/archive/0.1.0/', '/']) {
    assert.equal(classifyRedirectTarget(value), 'site-relative', value);
  }
});

test('classifyRedirectTarget: absolute HTTP(S) URLs', () => {
  for (const value of ['https://agentforge4j.org/docs/0.1.0/', 'http://example.com/x']) {
    assert.equal(classifyRedirectTarget(value), 'absolute-http', value);
  }
});

test('NEGATIVE CONTROL — a relative path merely BEGINNING with "http" is not absolute', () => {
  // The exact input the old `startsWith('http')` test misclassified.
  assert.equal(classifyRedirectTarget('httpfoo/bar'), 'unusable');
  assert.equal(classifyRedirectTarget('/httpfoo/bar'), 'site-relative');
});

test('a non-HTTP scheme is refused outright rather than treated as a path', () => {
  for (const value of ['javascript:alert(1)', 'data:text/html,x', 'mailto:a@b.example', 'file:///etc/passwd']) {
    assert.equal(classifyRedirectTarget(value), 'unusable', value);
  }
});

test('a relative target that is not site-rooted is refused rather than classified as a path', () => {
  assert.equal(classifyRedirectTarget('../elsewhere/'), 'unusable');
  assert.equal(classifyRedirectTarget('elsewhere/'), 'unusable');
});

test('injectRedirectStubSeo refuses an unusable target instead of labelling a page it cannot classify', () => {
  const nasty = REAL_STUB.replace('url=/docs/0.1.0/', 'url=javascript:alert(1)');
  assert.throws(
    () => injectRedirectStubSeo(nasty, OPTIONS),
    /refusing a redirect target that is neither a site-relative path nor an absolute HTTP\(S\) URL/,
  );
});

// --- The composed-artifact gate. ---

test('applyRedirectStubSeo labels an archive stub in place, leaving it with exactly one title', () => {
  const siteDir = mkdtempSync(join(tmpdir(), 'redirect-stub-archive-'));
  mkdirSync(join(siteDir, 'docs', 'archive', '0.1.0'), { recursive: true });
  mkdirSync(join(siteDir, 'docs', '0.1.0'), { recursive: true });
  // The plugin's own stub at /docs/, and an archive stub at the archived version's old address.
  writeFileSync(join(siteDir, 'docs', 'index.html'), REAL_STUB, 'utf8');
  writeFileSync(join(siteDir, 'docs', '0.1.0', 'index.html'), archiveRedirectStub(), 'utf8');

  const { recognised, updated } = applyRedirectStubSeo(siteDir, () => {
    throw new Error('should not have exited');
  });
  assert.equal(recognised, 2);
  assert.equal(updated, 2);
  for (const relPath of ['docs/index.html', 'docs/0.1.0/index.html']) {
    const html = readFileSync(join(siteDir, ...relPath.split('/')), 'utf8');
    assert.equal((html.match(/<title>/gi) ?? []).length, 1, `${relPath} must ship exactly one title`);
    assert.match(html, /<meta name="robots" content="noindex, follow">/);
  }
  // And each carries the copy for where IT goes, not one shared string.
  assert.match(readFileSync(join(siteDir, 'docs', 'index.html'), 'utf8'), /<title>Documentation — AgentForge4j<\/title>/);
  assert.match(
    readFileSync(join(siteDir, 'docs', '0.1.0', 'index.html'), 'utf8'),
    /<title>Archived documentation — AgentForge4j<\/title>/,
  );
});
