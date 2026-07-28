// SPDX-License-Identifier: Apache-2.0
//
// The fixture below is the REAL shape @docusaurus/plugin-client-redirects publishes — captured from
// the live site's own /docs/ on 2026-07-27, angle brackets and all. Testing against a tidied-up
// approximation would prove nothing about the page that actually ships: no <title>, no robots, a
// relative canonical, and no <body> element at all.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { applyRedirectStubSeo } from './assemble-site.mjs';
import { classifyRedirectTarget, injectRedirectStubSeo, redirectStubTarget } from './redirect-stub-seo.mjs';

const SITE_URL = 'https://agentforge4j.org';

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

const OPTIONS = {
  siteUrl: SITE_URL,
  title: 'Documentation — AgentForge4j',
  description: 'This address forwards to the current AgentForge4j documentation.',
  robots: 'noindex, follow',
};

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
  assert.match(html, /<meta name="description" content="This address forwards to the current AgentForge4j documentation\.">/);
});

test('the stub becomes explicitly non-indexable, with follow so the destination link still carries signal', () => {
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.match(html, /<meta name="robots" content="noindex, follow">/);
});

test("the plugin's RELATIVE canonical is replaced by an absolute one naming the destination — not supplemented", () => {
  assert.match(REAL_STUB, /<link rel="canonical" href="\/docs\/0\.1\.0\/" \/>/);
  const html = injectRedirectStubSeo(REAL_STUB, OPTIONS);
  assert.match(html, /<link rel="canonical" href="https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/">/);
  assert.equal((html.match(/rel="canonical"/g) ?? []).length, 1);
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

test('a stub whose destination is already absolute is not double-prefixed', () => {
  const absolute = REAL_STUB.replace('url=/docs/0.1.0/', 'url=https://agentforge4j.org/docs/0.1.0/');
  const html = injectRedirectStubSeo(absolute, OPTIONS);
  assert.match(html, /<link rel="canonical" href="https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/">/);
  assert.doesNotMatch(html, /agentforge4j\.org.*agentforge4j\.org/);
});

test('a non-stub page passed in by mistake fails loudly rather than being silently labelled', () => {
  assert.throws(() => injectRedirectStubSeo('<html><head></head><body>real page</body></html>', OPTIONS), /not a client-redirect stub/);
});

test('the destination is escaped into the canonical rather than trusted', () => {
  const nasty = REAL_STUB.replace('url=/docs/0.1.0/', 'url=/docs/a&b/');
  assert.match(injectRedirectStubSeo(nasty, OPTIONS), /href="https:\/\/agentforge4j\.org\/docs\/a&amp;b\/"/);
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
  const updated = applyRedirectStubSeo(siteDir, SITE_URL, () => {
    throw new Error('should not have exited');
  });
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
  applyRedirectStubSeo(siteDir, SITE_URL, (code) => exits.push(code));
  assert.deepEqual(exits, [1]);
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
  assert.match(html, /<title>Documentation — AgentForge4j<\/title>/);
  assert.doesNotMatch(html, /<title>AgentForge4j<\/title>/);
});

test('an archive stub keeps exactly one canonical, absolutised to the destination', () => {
  const html = injectRedirectStubSeo(archiveRedirectStub(), OPTIONS);
  assert.equal((html.match(/rel="canonical"/gi) ?? []).length, 1);
  assert.match(html, /<link rel="canonical" href="https:\/\/agentforge4j\.org\/docs\/archive\/0\.1\.0\/">/);
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

// --- Redirect-target classification. `startsWith('http')` called `httpfoo/bar` absolute and would
// have concatenated a site origin in front of `javascript:` and `data:` values. ---

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

test('a non-HTTP scheme is refused outright rather than prefixed with the site origin', () => {
  for (const value of ['javascript:alert(1)', 'data:text/html,x', 'mailto:a@b.example', 'file:///etc/passwd']) {
    assert.equal(classifyRedirectTarget(value), 'unusable', value);
  }
});

test('a relative target that is not site-rooted is refused rather than concatenated into nonsense', () => {
  assert.equal(classifyRedirectTarget('../elsewhere/'), 'unusable');
  assert.equal(classifyRedirectTarget('elsewhere/'), 'unusable');
});

test('injectRedirectStubSeo refuses an unusable target instead of emitting a malformed canonical', () => {
  const nasty = REAL_STUB.replace('url=/docs/0.1.0/', 'url=javascript:alert(1)');
  assert.throws(
    () => injectRedirectStubSeo(nasty, OPTIONS),
    /refusing a redirect target that is neither a site-relative path nor an absolute HTTP\(S\) URL/,
  );
});

test('an already-absolute HTTPS target is used as-is, not double-prefixed', () => {
  const absolute = REAL_STUB.replace('url=/docs/0.1.0/', 'url=https://agentforge4j.org/docs/0.1.0/');
  const html = injectRedirectStubSeo(absolute, OPTIONS);
  assert.match(html, /<link rel="canonical" href="https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/">/);
  assert.doesNotMatch(html, /agentforge4j\.org.*agentforge4j\.org/);
});

// --- The composed-artifact gate. ---

test('applyRedirectStubSeo labels an archive stub in place, leaving it with exactly one title', () => {
  const siteDir = mkdtempSync(join(tmpdir(), 'redirect-stub-archive-'));
  mkdirSync(join(siteDir, 'docs', 'archive', '0.1.0'), { recursive: true });
  mkdirSync(join(siteDir, 'docs', '0.1.0'), { recursive: true });
  // The plugin's own stub at /docs/, and an archive stub at the archived version's old address.
  writeFileSync(join(siteDir, 'docs', 'index.html'), REAL_STUB, 'utf8');
  writeFileSync(join(siteDir, 'docs', '0.1.0', 'index.html'), archiveRedirectStub(), 'utf8');

  const updated = applyRedirectStubSeo(siteDir, SITE_URL, () => {
    throw new Error('should not have exited');
  });
  assert.equal(updated, 2);
  for (const relPath of ['docs/index.html', 'docs/0.1.0/index.html']) {
    const html = readFileSync(join(siteDir, ...relPath.split('/')), 'utf8');
    assert.equal((html.match(/<title>/gi) ?? []).length, 1, `${relPath} must ship exactly one title`);
    assert.match(html, /<meta name="robots" content="noindex, follow">/);
  }
});
