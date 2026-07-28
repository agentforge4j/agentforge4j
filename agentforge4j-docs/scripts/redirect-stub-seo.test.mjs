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
import { injectRedirectStubSeo, redirectStubTarget } from './redirect-stub-seo.mjs';

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
