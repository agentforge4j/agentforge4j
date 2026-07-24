// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for the Pages artifact assembly. Fixture spa/build/javadoc directories under a
// temp root stand in for a real SPA build, a real Docusaurus build, and a real Javadoc surface —
// no build required.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import sax from 'sax';
import {SitemapStream, streamToPromise} from 'sitemap';
import {
  assembleSite,
  scanComposedHtmlForForbiddenContent,
  verifyComposedJavadocLinks,
  verifyComposedAnchorLinks,
} from './assemble-site.mjs';

function sitemapXmlFixture(urls) {
  const body = urls.map((url) => `  <url>\n    <loc>${url}</loc>\n  </url>`).join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    `${body}\n</urlset>\n`
  );
}

function sitemapXmlFixtureWithLastmod(entries) {
  const body = entries
    .map(({url, lastmod}) => `  <url>\n    <loc>${url}</loc>\n    <lastmod>${lastmod}</lastmod>\n  </url>`)
    .join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    `${body}\n</urlset>\n`
  );
}

// Mirrors the real raw maven-javadoc-plugin overview-page shape closely enough for
// javadoc-seo.mjs's applyJavadocSeo (run by assembleSite itself, against every composed javadoc/**
// surface) to recognise and post-process it, the same way it must against a real Javadoc build —
// `marker` stands in for whatever distinguishes one version's real content from another's.
function realisticJavadocHtml(marker) {
  return (
    '<!DOCTYPE HTML>\n<html lang>\n<head>\n<title>Overview</title>\n' +
    '<meta name="description" content="module index">\n</head>\n' +
    `<body>${marker}</body>\n</html>\n`
  );
}

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'assemble-'));
  const spaDir = join(root, 'spa');
  const buildDir = join(root, 'build');
  const javadocDir = join(root, 'javadoc-next');
  mkdirSync(spaDir, {recursive: true});
  writeFileSync(join(spaDir, 'index.html'), '<html>spa</html>');
  // The real SPA build ships dist/404.html byte-identical to dist/index.html (copy-404.mjs).
  writeFileSync(join(spaDir, '404.html'), '<html>spa</html>');
  // The real SPA build also ships its own robots.txt and sitemap.xml fragment (build-seo.mjs) —
  // both real static files assemble-site.mjs's own contract now requires.
  writeFileSync(join(spaDir, 'robots.txt'), 'User-agent: *\nAllow: /\n\nSitemap: https://agentforge4j.org/sitemap.xml\n');
  writeFileSync(join(spaDir, 'sitemap.xml'), sitemapXmlFixture(['https://agentforge4j.org/']));
  mkdirSync(buildDir, {recursive: true});
  writeFileSync(join(buildDir, 'index.html'), '<html>docs</html>');
  // The real Docusaurus build ships its own sitemap.xml (the sitemap plugin's postBuild output).
  writeFileSync(join(buildDir, 'sitemap.xml'), sitemapXmlFixture(['https://agentforge4j.org/docs/0.1.0/']));
  mkdirSync(javadocDir, {recursive: true});
  writeFileSync(join(javadocDir, 'index.html'), realisticJavadocHtml('javadoc next'));
  return {root, spaDir, buildDir, javadocDir, archiveDir: join(root, 'archive-absent'), siteDir: join(root, '_site')};
}

test('composes the _site layout: docs/, javadoc/next, javadoc/latest', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(existsSync(join(siteDir, 'docs', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'next', 'index.html')));
  assert.ok(existsSync(join(siteDir, 'javadoc', 'latest', 'index.html')));
});

test('the full generated Javadoc tree — not only the overview page — receives javadoc-seo\'s canonical/OG/Twitter policy once composed', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  // A real nested class page alongside the fixture's own overview index.html — mirrors a real
  // `javadoc` (JDK 17) build's shape closely enough for javadoc-seo.mjs to recognise it.
  mkdirSync(join(javadocDir, 'com', 'example'), {recursive: true});
  writeFileSync(
    join(javadocDir, 'com', 'example', 'Foo.html'),
    '<!DOCTYPE HTML>\n<html lang="en">\n<head>\n<title>Foo</title>\n' +
      '<meta name="description" content="declaration: package: com.example, class: Foo">\n</head>\n' +
      '<body><h1 class="title">Class Foo</h1></body>\n</html>\n',
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const nested = readFileSync(join(siteDir, 'javadoc', 'next', 'com', 'example', 'Foo.html'), 'utf8');
  assert.match(nested, /<link rel="canonical" href="https:\/\/agentforge4j\.org\/javadoc\/next\/com\/example\/Foo\.html">/);
  assert.match(nested, /<meta property="og:title"/);
  assert.match(nested, /<meta name="twitter:card" content="summary">/);
  // Real generated body content must survive.
  assert.match(nested, /<h1 class="title">Class Foo<\/h1>/);

  // /javadoc/latest/ mirrors /javadoc/next/ when there are no released versions — its own copy of
  // the same nested page must be processed too, independently (latest is a real, separately
  // composed copy, not a symlink).
  const latestNested = readFileSync(join(siteDir, 'javadoc', 'latest', 'com', 'example', 'Foo.html'), 'utf8');
  assert.match(latestNested, /<link rel="canonical" href="https:\/\/agentforge4j\.org\/javadoc\/latest\/com\/example\/Foo\.html">/);
});

test('in the no-released-version composition, /next/ and /latest/ are copied from the exact same source (assembleSite\'s own latestSource fallback) but receive divergent robots policy from applyJavadocSeo() — /latest/ stays indexable, /next/ is suppressed as the duplicate', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  mkdirSync(join(javadocDir, 'com', 'example'), {recursive: true});
  writeFileSync(
    join(javadocDir, 'com', 'example', 'Foo.html'),
    '<!DOCTYPE HTML>\n<html lang="en">\n<head>\n<title>Foo</title>\n' +
      '<meta name="description" content="declaration: package: com.example, class: Foo">\n</head>\n' +
      '<body><h1 class="title">Class Foo</h1></body>\n</html>\n',
  );
  // No releasedVersions/javadocVersionsDir passed — assembleSite's own default (empty array) is
  // exactly the no-release lifecycle state this test targets.
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});

  for (const relPath of ['index.html', 'com/example/Foo.html']) {
    const next = readFileSync(join(siteDir, 'javadoc', 'next', ...relPath.split('/')), 'utf8');
    assert.match(
      next,
      /<meta name="robots" content="noindex,follow">/,
      `/javadoc/next/${relPath} must be noindex,follow — a byte-for-byte duplicate of /latest/ in the no-release state`,
    );
    const latest = readFileSync(join(siteDir, 'javadoc', 'latest', ...relPath.split('/')), 'utf8');
    assert.doesNotMatch(
      latest,
      /<meta name="robots"/,
      `/javadoc/latest/${relPath} must stay indexable — the evergreen public entry point`,
    );
  }
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
    writeFileSync(join(javadocVersionsDir, version, 'index.html'), realisticJavadocHtml(`javadoc ${version}`));
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
  assert.match(
    readFileSync(join(siteDir, 'javadoc', '1.1.0', 'index.html'), 'utf8'),
    /javadoc 1\.1\.0/,
  );
  assert.match(
    readFileSync(join(siteDir, 'javadoc', '1.0.0', 'index.html'), 'utf8'),
    /javadoc 1\.0\.0/,
  );
  // releasedVersions[0] (newest, per versions.json's newest-first convention) is the /latest source.
  assert.match(
    readFileSync(join(siteDir, 'javadoc', 'latest', 'index.html'), 'utf8'),
    /javadoc 1\.1\.0/,
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
    writeFileSync(join(javadocVersionsDir, version, 'index.html'), realisticJavadocHtml(`javadoc ${version}`));
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

  assert.match(
    readFileSync(join(siteDir, 'javadoc', '1.0.0', 'index.html'), 'utf8'),
    /javadoc 1\.0\.0/,
  );
  assert.equal(
    readFileSync(join(siteDir, 'docs', 'archive', '1.0.0', 'index.html'), 'utf8'),
    '<html>archived docs 1.0.0</html>',
  );
  // Only releasedVersions[0] (the true newest ACTIVE version) sources /latest — an archived version
  // must never become the alias target.
  assert.match(
    readFileSync(join(siteDir, 'javadoc', 'latest', 'index.html'), 'utf8'),
    /javadoc 1\.1\.0/,
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

test('verifyComposedArtifact fails closed when /javadoc/latest/ silently composed empty (a real bug: an upstream Javadoc build failing without erroring left an empty, existsSync-true directory)', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
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
        // No javadocVersionsDir/releasedVersions passed — with releasedVersions empty, /latest
        // sources from javadocDir itself (assembleSite's own fallback) — override it to a real,
        // pre-existing-but-empty directory to reproduce the exact bug shape.
        javadocDir: (() => {
          const emptyDir = javadocDir.replace('javadoc-next', 'javadoc-next-composes-empty');
          mkdirSync(emptyDir, {recursive: true}); // exists, but has no index.html
          return emptyDir;
        })(),
        archiveDir,
        siteDir,
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('verifyComposedArtifact fails closed when a released version\'s /javadoc/<v>/ silently composed empty', () => {
  const {root, spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  const javadocVersionsDir = join(root, 'javadoc-versions');
  // 1.1.0 has a real surface; 1.0.0's directory exists (so requireDir's input-side check passes)
  // but is empty — exactly the real bug shape (an upstream per-version Javadoc build that failed
  // without throwing, or without existing before the copy step ran requireDir at all).
  mkdirSync(join(javadocVersionsDir, '1.1.0'), {recursive: true});
  writeFileSync(join(javadocVersionsDir, '1.1.0', 'index.html'), realisticJavadocHtml('javadoc 1.1.0'));
  mkdirSync(join(javadocVersionsDir, '1.0.0'), {recursive: true});

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
        javadocVersionsDir,
        releasedVersions: ['1.1.0', '1.0.0'],
        archiveDir,
        siteDir,
        customDomain: null,
        exit: fakeExit,
      }),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('merges the SPA and docs sitemap fragments into one final sitemap.xml at the site root', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  assert.deepEqual(locs, ['https://agentforge4j.org/', 'https://agentforge4j.org/docs/0.1.0/']);
});

test('the merged sitemap.xml is not just a copy of the SPA fragment — it includes docs URLs too', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const merged = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  const spaOnly = readFileSync(join(spaDir, 'sitemap.xml'), 'utf8');
  assert.notEqual(merged, spaOnly);
  assert.match(merged, /https:\/\/agentforge4j\.org\/docs\/0\.1\.0\//);
});

test('preserves each fragment\'s own <lastmod> through the merge, and tolerates a fragment entry with none', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(spaDir, 'sitemap.xml'),
    sitemapXmlFixtureWithLastmod([{url: 'https://agentforge4j.org/', lastmod: '2026-07-20'}]),
  );
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    sitemapXmlFixture(['https://agentforge4j.org/docs/0.1.0/']), // no lastmod at all
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/<\/loc>\s*<lastmod>2026-07-20<\/lastmod>/);
  const docsBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/<\/loc>\s*<\/url>/;
  assert.match(xml, docsBlock, 'the docs entry (no lastmod in its own fragment) must carry none through the merge either');
});

test('robots.txt is copied through to the site root from the SPA build', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.match(
    readFileSync(join(siteDir, 'robots.txt'), 'utf8'),
    /Sitemap: https:\/\/agentforge4j\.org\/sitemap\.xml/,
  );
});

test('fails closed when the SPA sitemap fragment is missing', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  rmSync(join(spaDir, 'sitemap.xml'));

  const exitCodes = [];
  const originalExit = process.exit;
  process.exit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  try {
    assert.throws(
      () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null}),
      /exit\(1\)/,
    );
  } finally {
    process.exit = originalExit;
  }
  assert.deepEqual(exitCodes, [1]);
});

test('fails closed when the docs sitemap fragment is missing', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  rmSync(join(buildDir, 'sitemap.xml'));

  const exitCodes = [];
  const originalExit = process.exit;
  process.exit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  try {
    assert.throws(
      () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null}),
      /exit\(1\)/,
    );
  } finally {
    process.exit = originalExit;
  }
  assert.deepEqual(exitCodes, [1]);
});

test('fails closed on a duplicate URL across the SPA and docs sitemap fragments', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(join(buildDir, 'sitemap.xml'), sitemapXmlFixture(['https://agentforge4j.org/']));

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null, exit: fakeExit}),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('fails closed when a <url> block does not match the expected <url><loc>...</loc>[<lastmod>...</lastmod>]</url> shape — must not silently drop that URL from the merged sitemap', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  // A <url> block with an extra element after <loc> (e.g. an xhtml:link alternate, the kind of
  // thing a future sitemap-plugin/Docusaurus bump could add) does not match the strict per-block
  // regex at all — without the loc-count cross-check, this URL would silently vanish from the
  // published sitemap instead of failing the build.
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/docs/0.1.0/</loc>\n' +
      '    <xhtml:link rel="alternate" href="https://agentforge4j.org/docs/0.1.0/" />\n' +
      '  </url>\n</urlset>\n',
  );

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null, exit: fakeExit}),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('fails closed when a <url> block has no <loc> at all (e.g. a typo\'d <location>) alongside one valid entry — a <loc>-only count comparison alone would miss this, since both sides would be zero for the malformed block', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/docs/0.1.0/</loc>\n  </url>\n' +
      '  <url>\n    <location>https://agentforge4j.org/docs/0.1.0/other/</location>\n  </url>\n' +
      '</urlset>\n',
  );

  const missingLocExitCodes = [];
  const fakeExitMissingLoc = (code) => {
    missingLocExitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null, exit: fakeExitMissingLoc}),
    /exit\(1\)/,
  );
  assert.deepEqual(missingLocExitCodes, [1]);
});

// --- Root-cause malformed-sitemap sweep (structural parsing, not regex/count heuristics) --------
//
// The two tests above (extra element after <loc>, typo'd <location>) predate this sweep and are
// retained unmodified. Everything below proves the ROOT CAUSE is fixed — a real structural parser
// (sax, strict mode) rejects every one of these shapes by construction, not by adding another
// special-case count. `expectSitemapMergeFailure` writes malformed XML into ONE fragment (SPA or
// docs, both exercised across this sweep — the same protection must apply to either input) and
// asserts assembleSite() itself fails closed with exit(1), never silently composing a sitemap that
// dropped the malformed entry.

function expectSitemapMergeFailure(rawXml, {fragment = 'docs'} = {}) {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(join(fragment === 'spa' ? spaDir : buildDir, 'sitemap.xml'), rawXml);
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null, exit: fakeExit}),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
}

const SITEMAP_HEADER =
  '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n';
const SITEMAP_FOOTER = '</urlset>\n';

test('fails closed on a self-closing <url/> — the exact root-cause gap: zero matched entries, zero <url> tags, zero <loc> tags all agreed under the old count-heuristic, so this vanished silently instead of failing the build', () => {
  expectSitemapMergeFailure(`${SITEMAP_HEADER}  <url/>\n${SITEMAP_FOOTER}`);
});

test('fails closed on an orphan </url> with no matching opening tag', () => {
  expectSitemapMergeFailure(`${SITEMAP_HEADER}  </url>\n${SITEMAP_FOOTER}`);
});

test('fails closed on an unclosed <url> (no closing tag before the document ends)', () => {
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n</urlset>\n',
  );
});

test('fails closed on mismatched closing tags inside a <url> entry', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </loc>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a self-closing <loc/> — an empty <loc> is treated the same as a missing one', () => {
  expectSitemapMergeFailure(`${SITEMAP_HEADER}  <url>\n    <loc/>\n  </url>\n${SITEMAP_FOOTER}`, {fragment: 'spa'});
});

test('fails closed on multiple <loc> elements inside one <url> entry', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a/</loc>\n    <loc>https://agentforge4j.org/b/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a self-closing <lastmod/> — an empty <lastmod> must fail the build rather than silently vanish from the merged sitemap', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod/>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on an empty <lastmod></lastmod> (no self-closing tag, but no text content either) — same guard as the self-closing form', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod></lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on multiple <lastmod> elements inside one <url> entry', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod>2026-07-23</lastmod>\n    <lastmod>2026-07-24</lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a malformed <lastmod> containing a nested element instead of plain text', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod><bad/></lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a malformed <loc> containing a nested element instead of plain text', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc><bad/>https://agentforge4j.org/example/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on malformed XML appearing after an otherwise-valid <url> entry earlier in the same document — must not silently keep only the entries seen before the corruption', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/valid/</loc>\n  </url>\n  <bogus<${SITEMAP_FOOTER}`,
  );
});

test('accepts <lastmod> before <loc> inside a <url> entry — child element order is not significant, both are identified by tag name', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `${SITEMAP_HEADER}  <url>\n    <lastmod>2026-07-23</lastmod>\n    <loc>https://agentforge4j.org/docs/0.1.0/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/<\/loc>\s*<lastmod>2026-07-23<\/lastmod>/);
});

test('a structurally valid but completely empty <urlset> is valid and contributes zero entries — not itself a malformed-input case', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(join(buildDir, 'sitemap.xml'), `${SITEMAP_HEADER}${SITEMAP_FOOTER}`);
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  // Only the SPA fragment's one URL survives — the empty docs <urlset> legitimately contributed
  // none, and the build did not fail because of it.
  assert.deepEqual(locs, ['https://agentforge4j.org/']);
});

test('an unexpected sibling child element inside <url> fails closed even when it is the only content (no <loc> present either)', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <changefreq>daily</changefreq>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('a <loc> containing an XML-escaped & round-trips through the merge as valid XML, not as a literal unescaped &', () => {
  // A real structural parser correctly decodes &amp; -> & when reading the value (unlike a raw
  // regex extractor, which only ever saw the literal escaped text) — the merge must re-escape it
  // on the way back out, or the composed sitemap.xml itself would no longer be well-formed XML.
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/docs/0.1.0/?a=1&amp;b=2</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/\?a=1&amp;b=2<\/loc>/);
  assert.doesNotMatch(xml, /\?a=1&b=2/, 'the & must be re-escaped, never written back literally');
});

test('a <loc>/<lastmod> containing an XML-escaped < or > round-trips through the merge as valid XML, not as a literal unescaped < or >', () => {
  // extractSitemapEntries only constrains <url> entry *structure*, not the characters <loc>/<lastmod>
  // text may legally decode to — a value that legitimately decoded &lt;/&gt; entities must still
  // re-escape safely on the way back out, or the composed sitemap.xml itself is no longer well-formed
  // XML. Proven here by feeding the real, produced output back through a real strict XML parser
  // (sax), not just by pattern-matching the expected escaped substrings.
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/docs/0.1.0/?a=1&lt;2&gt;3</loc>\n` +
      '    <lastmod>2026&lt;07&gt;23</lastmod>\n  </url>\n' +
      `${SITEMAP_FOOTER}`,
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/\?a=1&lt;2&gt;3<\/loc>/);
  assert.match(xml, /<lastmod>2026&lt;07&gt;23<\/lastmod>/);
  assert.doesNotMatch(xml, /\?a=1<2>3/, 'the < and > must be re-escaped, never written back literally');
  assert.doesNotMatch(xml, />2026<07>23</, 'the < and > must be re-escaped, never written back literally');
  // The definitive proof: the produced file itself must parse as well-formed strict XML. sax stays
  // silent on error unless onerror is wired to throw (it does not throw by default), so this must
  // set onerror explicitly rather than relying on write()/close() throwing on its own.
  assert.doesNotThrow(() => {
    const parser = sax.parser(true);
    parser.onerror = (err) => {
      throw err;
    };
    parser.write(xml).close();
  }, 'the composed sitemap.xml must itself be well-formed XML');
});

// --- scanComposedHtmlForForbiddenContent ------------------------------------------------------

test('scanComposedHtmlForForbiddenContent passes clean on ordinary composed HTML', () => {
  const root = mkdtempSync(join(tmpdir(), 'scan-clean-'));
  writeFileSync(join(root, 'index.html'), '<html><body><p>Nothing forbidden here.</p></body></html>');
  const exitCodes = [];
  scanComposedHtmlForForbiddenContent(root, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []);
});

test('scanComposedHtmlForForbiddenContent fails closed on literal, unparsed admonition syntax', () => {
  // The exact real regression this pass fixes: an MDX3 admonition using the old `:::note Title`
  // (space-separated) form, which is not valid directive syntax and so renders as literal text.
  const root = mkdtempSync(join(tmpdir(), 'scan-admonition-'));
  mkdirSync(join(root, 'how-to'), {recursive: true});
  writeFileSync(
    join(root, 'how-to', 'index.html'),
    '<html><body><p>:::note Authoritative lists</p><p>see the list</p><p>:::</p></body></html>',
  );
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(() => scanComposedHtmlForForbiddenContent(root, fakeExit), /exit\(1\)/);
  assert.deepEqual(exitCodes, [1]);
});

test('scanComposedHtmlForForbiddenContent fails closed on a raw {@code}/{@link} Javadoc tag', () => {
  const root = mkdtempSync(join(tmpdir(), 'scan-javadoc-tag-'));
  writeFileSync(
    join(root, 'index.html'),
    '<html><body><td>filesystem directory ({@code agentforge4j.integrations.dir})</td></body></html>',
  );
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(() => scanComposedHtmlForForbiddenContent(root, fakeExit), /exit\(1\)/);
  assert.deepEqual(exitCodes, [1]);
});

test('scanComposedHtmlForForbiddenContent fails closed on a /docs/javadoc/ link', () => {
  const root = mkdtempSync(join(tmpdir(), 'scan-wrong-javadoc-path-'));
  writeFileSync(join(root, 'index.html'), '<html><body><a href="/docs/javadoc/next/">API</a></body></html>');
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(() => scanComposedHtmlForForbiddenContent(root, fakeExit), /exit\(1\)/);
  assert.deepEqual(exitCodes, [1]);
});

test('scanComposedHtmlForForbiddenContent fails closed on stale "wired in a later phase" placeholder copy', () => {
  const root = mkdtempSync(join(tmpdir(), 'scan-stale-placeholder-'));
  writeFileSync(join(root, 'index.html'), '<html><body><p>The generator is wired in a later phase.</p></body></html>');
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(() => scanComposedHtmlForForbiddenContent(root, fakeExit), /exit\(1\)/);
  assert.deepEqual(exitCodes, [1]);
});

test('assembleSite itself fails closed when the composed SPA output contains exposed admonition syntax', () => {
  // Integration-level: the check runs as part of the real assembleSite() call, not just in isolation.
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(join(spaDir, 'index.html'), '<html>spa :::note broken :::</html>');
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null, exit: fakeExit}),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

// --- verifyComposedJavadocLinks ----------------------------------------------------------------

function javadocLinkFixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'javadoc-links-'));
  const docsSourceDir = join(root, 'docs');
  const versionedDocsSourceDir = join(root, 'versioned_docs');
  const siteDir = join(root, '_site');
  mkdirSync(docsSourceDir, {recursive: true});
  mkdirSync(versionedDocsSourceDir, {recursive: true});
  mkdirSync(siteDir, {recursive: true});
  return {root, docsSourceDir, versionedDocsSourceDir, siteDir};
}

test('verifyComposedJavadocLinks passes when every live javadoc: reference resolves to a real composed file', () => {
  const {docsSourceDir, siteDir} = javadocLinkFixtureRoot();
  writeFileSync(
    join(docsSourceDir, 'page.mdx'),
    'See `javadoc:com.agentforge4j.core.workflow.Workflow` for details.\n',
  );
  mkdirSync(join(siteDir, 'javadoc', 'next', 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow'), {
    recursive: true,
  });
  writeFileSync(
    join(siteDir, 'javadoc', 'next', 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow', 'Workflow.html'),
    '<html>Workflow</html>',
  );
  const exitCodes = [];
  verifyComposedJavadocLinks(siteDir, docsSourceDir, join(docsSourceDir, '..', 'versioned_docs'), (code) =>
    exitCodes.push(code),
  );
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedJavadocLinks fails closed when a javadoc: reference has no matching file in the composed artifact', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = javadocLinkFixtureRoot();
  writeFileSync(
    join(docsSourceDir, 'page.mdx'),
    'See `javadoc:com.agentforge4j.core.workflow.Workflow` for details.\n',
  );
  // siteDir has no javadoc/next/... surface at all — the composed artifact is missing the target.
  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => verifyComposedJavadocLinks(siteDir, docsSourceDir, versionedDocsSourceDir, fakeExit),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('verifyComposedJavadocLinks pins a versioned snapshot reference to its own version, not next', () => {
  const {versionedDocsSourceDir, siteDir} = javadocLinkFixtureRoot();
  const versionDir = join(versionedDocsSourceDir, 'version-0.1.0');
  mkdirSync(versionDir, {recursive: true});
  writeFileSync(
    join(versionDir, 'page.mdx'),
    'See `javadoc:com.agentforge4j.core.workflow.Workflow` for details.\n',
  );
  // Only the 0.1.0-pinned surface exists — if the check wrongly resolved against `next`, it would fail.
  mkdirSync(join(siteDir, 'javadoc', '0.1.0', 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow'), {
    recursive: true,
  });
  writeFileSync(
    join(siteDir, 'javadoc', '0.1.0', 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow', 'Workflow.html'),
    '<html>Workflow</html>',
  );
  const exitCodes = [];
  verifyComposedJavadocLinks(siteDir, join(versionedDocsSourceDir, '..', 'docs'), versionedDocsSourceDir, (code) =>
    exitCodes.push(code),
  );
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedJavadocLinks is a no-op (0 checked, never fails) when no source dirs are supplied', () => {
  const {siteDir} = javadocLinkFixtureRoot();
  const exitCodes = [];
  verifyComposedJavadocLinks(siteDir, undefined, undefined, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []);
});

// --- verifyComposedAnchorLinks: in-repo relative links carrying a URL fragment must resolve to a
// REAL anchor on the real composed page, not just an existing file --------------------------------

function anchorFixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'anchor-links-'));
  const docsSourceDir = join(root, 'docs');
  const versionedDocsSourceDir = join(root, 'versioned_docs');
  const siteDir = join(root, '_site');
  mkdirSync(docsSourceDir, {recursive: true});
  mkdirSync(versionedDocsSourceDir, {recursive: true});
  mkdirSync(siteDir, {recursive: true});
  return {root, docsSourceDir, versionedDocsSourceDir, siteDir};
}

function writeBuiltPage(siteDir, versionSegment, relDirSegments, slug, html) {
  const dir = join(siteDir, 'docs', versionSegment, ...relDirSegments, ...(slug ? [slug] : []));
  mkdirSync(dir, {recursive: true});
  writeFileSync(join(dir, 'index.html'), html, 'utf8');
}

test('verifyComposedAnchorLinks passes against a minified build that drops attribute quotes (id=foo, not id="foo")', () => {
  // Real regression: a production Docusaurus build's minifier strips quotes from any attribute
  // value that is a safe unquoted-HTML5 token (every heading-slug id qualifies) — a naive
  // quoted-only `id="foo"` text search false-positive-failed against every real page the first
  // time this checker ran against an actual build, even though the anchors were genuinely present.
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  writeFileSync(join(docsSourceDir, 'page.mdx'), 'See [Foo](#foo) below.\n');
  writeBuiltPage(siteDir, 'next', [], 'page', '<h3 id=foo>Foo<a href=#foo class=hash-link></a></h3>');

  const exitCodes = [];
  verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedAnchorLinks passes when a same-page anchor exists on the real composed page', () => {
  const {docsSourceDir, siteDir} = anchorFixtureRoot();
  writeFileSync(join(docsSourceDir, 'page.mdx'), 'See [Foo](#foo) below.\n\n### Foo\n');
  writeBuiltPage(siteDir, 'next', [], 'page', '<html><h3 id="foo">Foo</h3></html>');

  const exitCodes = [];
  verifyComposedAnchorLinks(siteDir, docsSourceDir, join(docsSourceDir, '..', 'versioned_docs'), (code) =>
    exitCodes.push(code),
  );
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedAnchorLinks fails closed when the anchor does not exist on an otherwise-real page (the real bug class this closes)', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  writeFileSync(join(docsSourceDir, 'page.mdx'), 'See [Foo](#foo) below.\n');
  // The page exists and is well-formed, but genuinely has no id="foo" anywhere — exactly what a
  // stale/incorrect cross-link looks like when only file existence is checked.
  writeBuiltPage(siteDir, 'next', [], 'page', '<html><h3 id="bar">Bar</h3></html>');

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, fakeExit),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('verifyComposedAnchorLinks resolves a cross-file anchor to the target file\'s own id-based route (the real blueprint->workflow shape)', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  mkdirSync(join(docsSourceDir, 'schemas'), {recursive: true});
  writeFileSync(
    join(docsSourceDir, 'schemas', 'blueprint.mdx'),
    '---\nid: reference-schema-blueprint\n---\n\n| [StepDefinition](./workflow.mdx#stepdefinition) |\n',
  );
  writeFileSync(
    join(docsSourceDir, 'schemas', 'workflow.mdx'),
    '---\nid: reference-schema-workflow\n---\n\n### StepDefinition\n',
  );
  writeBuiltPage(siteDir, 'next', ['schemas'], 'reference-schema-workflow', '<html><h3 id="stepdefinition">StepDefinition</h3></html>');

  const exitCodes = [];
  verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedAnchorLinks fails closed when the cross-file target page is missing from the composed artifact', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  mkdirSync(join(docsSourceDir, 'schemas'), {recursive: true});
  writeFileSync(
    join(docsSourceDir, 'schemas', 'blueprint.mdx'),
    '---\nid: reference-schema-blueprint\n---\n\n| [StepDefinition](./workflow.mdx#stepdefinition) |\n',
  );
  writeFileSync(join(docsSourceDir, 'schemas', 'workflow.mdx'), '---\nid: reference-schema-workflow\n---\n\n### StepDefinition\n');
  // workflow's built page was never written — the composed artifact is missing it entirely.

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, fakeExit),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

test('verifyComposedAnchorLinks resolves an index page anchor at its own directory root, not an id-named subdirectory', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  mkdirSync(join(docsSourceDir, 'reference'), {recursive: true});
  writeFileSync(
    join(docsSourceDir, 'reference', 'index.mdx'),
    '---\nid: reference-config\n---\n\nSee [details](#details).\n\n### Details\n',
  );
  // index.mdx maps to its own directory (reference/index.html), never reference/reference-config/.
  writeBuiltPage(siteDir, 'next', ['reference'], null, '<html><h3 id="details">Details</h3></html>');

  const exitCodes = [];
  verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedAnchorLinks resolves a versioned snapshot\'s anchor against that version\'s own composed path, not next', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  const versionDir = join(versionedDocsSourceDir, 'version-0.1.0', 'schemas');
  mkdirSync(versionDir, {recursive: true});
  writeFileSync(join(versionDir, 'blueprint.mdx'), '---\nid: reference-schema-blueprint\n---\n\n| [StepDefinition](./workflow.mdx#stepdefinition) |\n');
  writeFileSync(join(versionDir, 'workflow.mdx'), '---\nid: reference-schema-workflow\n---\n\n### StepDefinition\n');
  // Only the 0.1.0-pinned surface exists — if resolution wrongly fell back to `next`, this would fail.
  writeBuiltPage(siteDir, '0.1.0', ['schemas'], 'reference-schema-workflow', '<html><h3 id="stepdefinition">StepDefinition</h3></html>');

  const exitCodes = [];
  verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []);
});

test('verifyComposedAnchorLinks does not flag an absolute/external link with a fragment (out of its scope)', () => {
  const {docsSourceDir, versionedDocsSourceDir, siteDir} = anchorFixtureRoot();
  writeFileSync(docsSourceDir + '/page.mdx', 'See [external](https://example.com/foo#bar) and [javadoc](pathname:///javadoc/next/Foo.html#bar).\n');

  const exitCodes = [];
  verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, (code) => exitCodes.push(code));
  assert.deepEqual(exitCodes, []); // 0 checked, 0 failures — neither link matches the in-repo pattern
});

test('verifyComposedJavadocLinks is fragment-aware: a resolved URL fragment must exist as a real anchor on the target page', () => {
  // resolveJavadocUrl never emits a fragment today, so this proves the checker's own fragment
  // handling directly, independent of that role ever actually producing one.
  const {siteDir} = anchorFixtureRoot();
  const docsSourceDir = mkdtempSync(join(tmpdir(), 'anchor-links-docs-'));
  writeFileSync(join(docsSourceDir, 'page.mdx'), 'See `javadoc:com.agentforge4j.core.workflow.Workflow` for details.\n');
  const targetDir = join(siteDir, 'javadoc', 'next', 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow');
  mkdirSync(targetDir, {recursive: true});
  // The file exists, but genuinely has no id="someMethod" anchor — file-existence alone would pass.
  writeFileSync(join(targetDir, 'Workflow.html'), '<html><body>no anchors here</body></html>');

  const exitCodes = [];
  verifyComposedJavadocLinks(siteDir, docsSourceDir, join(docsSourceDir, '..', 'versioned_docs'), (code) =>
    exitCodes.push(code),
  );
  // No fragment was ever appended by resolveJavadocUrl, so this still passes today — this test
  // documents/locks in that the checker reads resolved.url's own fragment, not the source text.
  assert.deepEqual(exitCodes, []);
});

test('fails closed on a sitemap URL outside https://agentforge4j.org/', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(join(buildDir, 'sitemap.xml'), sitemapXmlFixture(['https://evil.example/docs/0.1.0/']));

  const exitCodes = [];
  const fakeExit = (code) => {
    exitCodes.push(code);
    throw new Error(`exit(${code})`);
  };
  assert.throws(
    () => assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null, exit: fakeExit}),
    /exit\(1\)/,
  );
  assert.deepEqual(exitCodes, [1]);
});

// --- <urlset>/<url>/<loc>/<lastmod> attribute policy ------------------------------------------
//
// The parser's narrow structural contract (exactly one <loc>, optional <lastmod>, no other
// sibling) was previously silent on attributes: sax parses `<url foo="bar">` successfully, and the
// serializer only ever reads element text, so any attribute on any of these four elements was
// accepted and quietly dropped — undermining the doc comment's claim of an exhaustive, narrow
// contract. `<urlset>` alone may carry attributes, and only the exact sitemap namespace
// declarations real first-party output actually uses (see URLSET_ALLOWED_ATTRIBUTES); `<url>`,
// `<loc>`, and `<lastmod>` may carry none.

test('accepts the real Docusaurus <urlset> shape: all five sitemap namespace declarations (xmlns + news/xhtml/image/video)', () => {
  // Byte-for-byte the root element `npm run build` in this exact module produced (verified against
  // a real build/sitemap.xml before this test was written) — the `sitemap` npm package's
  // SitemapStream declares all four extension namespaces unconditionally by default, and
  // @docusaurus/plugin-sitemap never overrides that default.
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" ' +
      'xmlns:news="http://www.google.com/schemas/sitemap-news/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml" ' +
      'xmlns:image="http://www.google.com/schemas/sitemap-image/1.1" xmlns:video="http://www.google.com/schemas/sitemap-video/1.1">' +
      '<url><loc>https://agentforge4j.org/docs/0.1.0/</loc></url></urlset>',
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/<\/loc>/);
});

test('accepts the real SPA <urlset> shape: only the bare xmlns declaration', () => {
  // build-seo.mjs hand-writes its own sitemap.xml template with only the base xmlns — proven
  // compatible in every other test via sitemapXmlFixture(), which uses exactly this shape; this
  // test names the property explicitly rather than leaving it implicit.
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  assert.ok(existsSync(join(siteDir, 'sitemap.xml')));
});

test('fails closed on an unexpected <urlset> attribute', () => {
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" foo="bar">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n</urlset>\n',
  );
});

test('fails closed on a known <urlset> attribute name with the wrong (foreign) value', () => {
  // A namespace declaration that lies about its own URI must not be silently trusted — same class
  // of defect as accepting an unknown attribute name outright.
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<urlset xmlns="https://not-the-real-sitemap-namespace.example/">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n</urlset>\n',
    {fragment: 'spa'},
  );
});

test('fails closed on an attribute on <url>', () => {
  expectSitemapMergeFailure(`${SITEMAP_HEADER}  <url foo="bar">\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n${SITEMAP_FOOTER}`);
});

test('fails closed on an attribute on <loc>', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc foo="bar">https://agentforge4j.org/example/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on an attribute on <lastmod>', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod foo="bar">2026-07-23</lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

// --- Untested fail-closed branches: wrong root, stray top-level sibling, nested <url> ----------
//
// These three shapes were already correctly rejected by extractSitemapEntries (confirmed by manual
// trace of the onopentag handler), but had no direct test proving it. Exercised through the real
// assembleSite()/mergeSitemaps() path, like every other malformed-fragment test in this suite, so
// the assertion proves actual deployment behavior rather than a bare unit of the parser.

test('fails closed when the document root is not <urlset>', () => {
  expectSitemapMergeFailure('<?xml version="1.0" encoding="UTF-8"?>\n<foo>\n  <bar/>\n</foo>\n');
});

test('fails closed when the document root is a bare <url> with no <urlset> wrapper at all — the specific shape the dedicated root check exists for (confirmed by negative control: with only that check disabled, this exact document is silently accepted and merged)', () => {
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n<url>\n  <loc>https://agentforge4j.org/example/</loc>\n</url>\n',
  );
});

test('fails closed on a stray top-level sibling element alongside <url> under <urlset>', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n  <foo/>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a bare top-level <loc> outside any <url> wrapper — the specific shape the top-level stray-element check exists for (a name that also happens to be a valid <url> child, so only the top-level guard, not the child-name guard, can catch it)', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <loc>https://agentforge4j.org/example/</loc>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a <url> nested inside another <url>', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/outer/</loc>\n` +
      '    <url>\n      <loc>https://agentforge4j.org/inner/</loc>\n    </url>\n  </url>\n' +
      `${SITEMAP_FOOTER}`,
  );
});

// --- <urlset> is only the document root; element names are exact; lastmod may not be blank -----
//
// The urlset-acceptance branch originally ran before the inside-<url>/inside-leaf guards, so a
// <urlset>-named element was silently accepted at ANY depth: a stray <urlset/> inside <url> was
// ignored, one inside <loc> text spliced the surrounding text into a single corrupted published
// URL (`.../a<urlset/>b` -> `.../ab`), and a second root-level <urlset> after the real root closed
// was accepted outright (sax does not reject multi-root documents on its own). These tests pin the
// root-only rule, the exact-name rule (no namespace-prefix folding), and the whitespace-only
// <lastmod> guard — all through the real assembleSite()/mergeSitemaps() path.

test('fails closed on a <urlset/> nested inside a <url> entry — <urlset> is only accepted as the document root', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <urlset/>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a <urlset/> inside <loc> text — previously the element was silently dropped and the text around it published as one corrupted URL', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a<urlset/>b</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a second root-level <urlset> after the real root closed — a sitemap document has exactly one root', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n</urlset>\n<urlset/>\n`,
  );
});

test('fails closed on a namespace-prefixed root <sm:urlset> — the root must be exactly <urlset>, not a prefixed spelling of it', () => {
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n<sm:urlset xmlns:sm="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n</sm:urlset>\n',
  );
});

test('fails closed on a namespace-prefixed <x:url> under <urlset> — element names are matched exactly, never folded to their bare local name', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <x:url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </x:url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a namespace-prefixed <foo:loc> inside <url> — a prefixed spelling is an unexpected child, not a <loc>', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <foo:loc>https://agentforge4j.org/example/</foo:loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a whitespace-only <lastmod> — treated the same as present-but-empty, never shipped verbatim as an invalid W3C datetime', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod>   </lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

// --- Comments, processing instructions, DOCTYPE, HTML-only entities, duplicate <urlset>
// attributes, and stray text: sibling gaps in the invariants already fixed above --------------
//
// A comment or processing instruction inside a <loc>/<lastmod> leaf splices the surrounding text
// into one corrupted value exactly like a stray nested element did before that was fixed; an
// HTML-only entity (e.g. `&copy;`) decodes text that is not actually well-formed XML, the same
// class the well-formedness guarantee already covers; a duplicate <urlset> attribute is a
// well-formedness violation the allowlist check alone cannot see, because sax silently keeps only
// the first value and never surfaces the dropped duplicate through any callback; DOCTYPE and stray
// top-level text were simply never enumerated.

test('fails closed on a comment spliced into <loc> text — previously silently spliced the surrounding text into one corrupted published URL', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a<!--x-->b/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a comment spliced into <lastmod> text', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod>2026-07-<!--x-->24</lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a comment as a sibling inside <url>, outside any leaf', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <!-- a comment -->\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a processing instruction spliced into <loc> text', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a<?pi target?>b/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a DOCTYPE declaration', () => {
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE urlset>\n' +
      '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n</urlset>\n',
  );
});

test('fails closed on an HTML-only character entity (&copy;) inside <loc> — decoding it would accept text that is not actually well-formed XML', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/&copy;</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('accepts the five standard XML entities and a numeric character reference in <loc> — strictEntities narrows only the HTML-only entity table, not real XML text content', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a&amp;b&#169;/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/a&amp;b©\/<\/loc>/);
});

test('fails closed on a duplicate <urlset> attribute name — even when the first, kept value is the genuine one and a foreign second declaration would otherwise be silently dropped unseen', () => {
  expectSitemapMergeFailure(
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns="https://foreign.example/">\n' +
      '  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n</urlset>\n',
  );
});

test('fails closed on stray non-whitespace text directly inside <urlset>, outside any <url> — previously silently discarded', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  stray\n  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on stray non-whitespace text directly inside <url>, outside <loc>/<lastmod> — previously silently discarded', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    stray\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a <loc> with trailing whitespace — not a valid URL value, and previously shipped verbatim', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/ </loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a <loc> with leading whitespace', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc> https://agentforge4j.org/example/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a <lastmod> with trailing whitespace around otherwise-valid content — the same guard applied to <loc>, applied symmetrically', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod>2026-07-24 </lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

// --- The leading <?xml ...?> declaration is exempted only by POSITION, never by name alone -----
//
// The declaration is well-formed XML only as the very first thing in the document. A same-named
// PI anywhere else is not the declaration and must fail like any other PI — previously the PI
// handler exempted any PI named "xml" (case-insensitively) no matter where it appeared, so one
// spliced into a <loc>/<lastmod> leaf silently corrupted the published value instead of failing.

test('fails closed on a same-named "xml" processing instruction spliced into <loc> text — the leading-declaration exemption must not apply mid-document', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a<?xml version="1.0"?>b/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on an uppercase "XML" processing instruction spliced into <loc> text — the exemption is not merely case-sensitive, it is position-sensitive', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a<?XML foo?>b/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('fails closed on a repeated <?xml ...?> declaration appearing as a <url> sibling after the real root opened', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <?xml version="1.0"?>\n  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

// --- Characters outside the XML 1.0 Char production must fail closed, even when the numeric ----
// character reference or raw byte that produces them is itself syntactically well-formed --------
//
// sax validates a numeric character reference's syntax (`&#x1;`) and a raw literal byte's tag/
// entity structure, but never whether the resulting codepoint is an XML 1.0 Char at all. Both
// decode straight into <loc>/<lastmod> text like any other character, so previously they shipped
// straight into the composed sitemap.xml — making the published artifact itself not well-formed
// XML, undetectable by the suite's own reparse-with-sax round-trip proof (sax skips this exact
// check on the way back in too).

test('fails closed on a numeric character reference to a codepoint the XML 1.0 Char production forbids (&#x1;) inside <loc>', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a&#x1;b/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a raw literal control byte (U+000B) inside <lastmod> — not caught by the stray-whitespace allowance, since JS treats U+000B as whitespace', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <lastmod>2026-0724</lastmod>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

test('accepts the three whitespace XML 1.0 Chars (tab, LF, CR) embedded in <loc> text — the invalid-character guard narrows only genuinely illegal codepoints, not legal control chars', () => {
  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a&#x9;b&#xA;c&#xD;d/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
  );
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const xml = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /<loc>https:\/\/agentforge4j\.org\/a\tb\nc\rd\/<\/loc>/);
});

// --- CDATA sections are outside the narrow, enumerated contract, symmetric with comments/PIs ----
//
// Previously a CDATA section was silently folded into leaf text (`oncdata = ontext`), round-
// tripping correctly but newly asymmetric with the comment/PI/DOCTYPE rejections added alongside
// it, and never mentioned by the "every case is enumerated" contract.

test('fails closed on a CDATA section spliced into <loc> text', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/a<![CDATA[b]]>c/</loc>\n  </url>\n${SITEMAP_FOOTER}`,
    {fragment: 'spa'},
  );
});

test('fails closed on a CDATA section as a sibling inside <url>, outside any leaf', () => {
  expectSitemapMergeFailure(
    `${SITEMAP_HEADER}  <url>\n    <loc>https://agentforge4j.org/example/</loc>\n    <![CDATA[stray]]>\n  </url>\n${SITEMAP_FOOTER}`,
  );
});

// --- Root-cause regression: the parser's narrow <url> contract stays compatible with
// docusaurus.config.ts's real, live sitemap options --------------------------------------------

test("the real Docusaurus sitemap config stays compatible with the parser's narrow <url> contract", async () => {
  // Reads docusaurus.config.ts itself (Node's native TS type-stripping imports it directly — no
  // hardcoded copy of "changefreq: null, priority: null" in this test) and drives the REAL `sitemap`
  // npm package's item-serialization code (the exact library @docusaurus/plugin-sitemap's own
  // xml.js uses) with those live values. If a future edit to docusaurus.config.ts ever restores
  // `changefreq`/`priority` to a non-null value, this test fails at the config change — the real
  // library will emit a real <changefreq>/<priority> element, and the real parser will reject it —
  // instead of the failure surfacing later as an opaque, unrelated composed-site build break.
  const {default: docusaurusConfig} = await import('../docusaurus.config.ts');
  const [, presetOptions] = docusaurusConfig.presets[0];
  const {sitemap: sitemapOptions} = presetOptions;

  const stream = new SitemapStream({});
  stream.write({
    url: 'https://agentforge4j.org/docs/0.1.0/',
    changefreq: sitemapOptions.changefreq,
    priority: sitemapOptions.priority,
    lastmod: undefined,
    video: [],
    img: [],
    links: [],
  });
  stream.end();
  const realGeneratedXml = (await streamToPromise(stream)).toString();

  const {spaDir, buildDir, javadocDir, archiveDir, siteDir} = fixture();
  writeFileSync(join(buildDir, 'sitemap.xml'), realGeneratedXml);
  // Must NOT fail closed — this is the compatibility proof itself.
  assembleSite({spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null});
  const merged = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  assert.match(merged, /<loc>https:\/\/agentforge4j\.org\/docs\/0\.1\.0\/<\/loc>/);
});
