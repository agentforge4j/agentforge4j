// SPDX-License-Identifier: Apache-2.0
//
// Post-build production-artifact check (mirrors agentforge4j-docs/scripts/verify-noindex.mjs's own
// philosophy: check the real build output directly, not a fixture standing in for it) — the
// prerendered body content and the trailing-slash sitemap/canonical fix only mean something once
// verified against the actual dist/ this build produced. Wired to run right after build-seo.mjs +
// copy-404.mjs in package.json's build/check scripts, so this gate runs on every real build.
//
// Serves dist/ through a static file server that faithfully reproduces GitHub Pages' own
// directory-redirect behaviour (a bare directory path 301s to its trailing-slash form; only the
// slash form serves 200 directly) — the exact hosting semantics that made every non-slash sitemap
// URL redirect before this fix — so "every sitemap URL returns 200 with no redirect" below is a
// real, mechanical HTTP round-trip against production-equivalent serving semantics, not an
// assumption; and a dedicated self-check proves the emulation itself is faithful (not vacuously
// always-200), so a future regression that reintroduces a non-slash sitemap URL would be caught.

import { createServer } from 'node:http';
import { createReadStream, existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, isAbsolute, join, relative, resolve } from 'node:path';
// `URL` is imported explicitly rather than leaned on as an ambient global — the same thing
// build-seo.test.mjs and prerender-routes.test.mjs already do, and what this repo's lint block for
// `scripts/**/*.mjs` requires (it declares only console/process/Buffer/fetch as globals).
import { fileURLToPath, URL } from 'node:url';
// The one import of another script's internals in this file, and a deliberate exception to the
// convention withTrailingSlash's own comment below documents ("duplicated deliberately, not
// imported"). That convention governs DERIVATIONS — two scripts computing the same answer from the
// same committed config compute it separately, so a bug in one cannot make the other agree with it.
// JSON_LD_SCRIPT_ID is not a derivation: it is an opaque literal whose entire contract is "these
// bytes are identical everywhere", with no config to re-derive it from. Re-typing it here would not
// buy independence, only a third place to drift.
//
// The route-scoped social tag list is deliberately NOT imported — see
// REQUIRED_ROUTE_SCOPED_SOCIAL_TAGS below for why an imported list would make this gate's coverage
// a function of the very producer it is checking.
import { JSON_LD_SCRIPT_ID } from './build-seo.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const DIST_DIR = join(MODULE_ROOT, 'dist');
const SEO_ROUTES_PATH = join(MODULE_ROOT, 'src', 'config', 'seo-routes.json');
const SITE_ORIGIN = 'https://agentforge4j.org';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.xml': 'application/xml',
};

/** Resolves a request path against `root`, returning the resolved absolute path only if it is
 * genuinely `root` itself or a real descendant of it — never a bare string-prefix comparison
 * (which a sibling directory sharing the same prefix, e.g. `dist-evil` next to `dist`, would
 * incorrectly pass) and never a path a `../` (or an already-decoded `%2e%2e%2f`, since the caller
 * decodes the URL before this runs) traversal segment could walk outside `root`. Returns `null` for
 * anything outside `root` — the caller must reject, never serve, that case. */
export function resolveWithinRoot(root, urlPath) {
  const candidate = resolve(root, `.${urlPath}`);
  const rel = relative(root, candidate);
  if (rel === '' || (!rel.startsWith('..') && !isAbsolute(rel))) {
    return candidate;
  }
  return null;
}

/** `decodeURIComponent` throws `URIError` on malformed percent-encoding (e.g. a truncated escape
 * like `%E0%A4%A`) — uncaught, that would escape the request handler and crash the whole process,
 * taking down every other in-flight check with it. Returns `null` for anything malformed; the
 * caller must reject with a controlled 400, never attempt path resolution or a filesystem call on
 * an input that couldn't even be decoded. */
function safeDecodeURIComponent(value) {
  try {
    return decodeURIComponent(value);
  } catch {
    return null;
  }
}

/** A bare directory path 301s to its trailing-slash form; the slash form serves the directory's
 * index.html directly at 200; a real file serves as itself; anything else serves `dist/404.html`
 * — the SPA's own branded not-found shell — under a real HTTP 404 status.
 *
 * That last case is what GitHub Pages genuinely does, and it matters in both directions. It is NOT
 * an SPA fallback in the prerender-routes.mjs sense: that server answers EVERY path with
 * dist/index.html at 200, because at prerender time no per-route shell exists yet. Doing that here
 * would be actively wrong — every route would appear to serve the home page's `<head>`, and any
 * check that compares one route's served metadata against another's would pass vacuously no matter
 * what the build produced. The status stays 404, so every "must return 200" assertion in this file
 * fails on a missing page exactly as before; the only thing that changes is that an unknown path
 * now returns the same bytes a real visitor would get, which is what makes the unknown-route case
 * checkable at all. */
export function startGhPagesEmulatingServer(distDir) {
  const notFoundPath = join(distDir, '404.html');
  const serveNotFound = (res) => {
    if (!existsSync(notFoundPath)) {
      res.writeHead(404);
      res.end('not found');
      return;
    }
    res.writeHead(404, { 'content-type': 'text/html; charset=utf-8' });
    createReadStream(notFoundPath).pipe(res);
  };
  const server = createServer((req, res) => {
    const urlPath = safeDecodeURIComponent((req.url ?? '/').split('?')[0]);
    if (urlPath === null) {
      res.writeHead(400);
      res.end('bad request');
      return;
    }
    const candidate = resolveWithinRoot(distDir, urlPath);
    if (candidate === null) {
      res.writeHead(400);
      res.end('bad request');
      return;
    }
    let stat;
    try {
      stat = statSync(candidate);
    } catch {
      serveNotFound(res);
      return;
    }
    if (stat.isDirectory()) {
      if (!urlPath.endsWith('/')) {
        res.writeHead(301, { location: `${urlPath}/` });
        res.end();
        return;
      }
      const indexPath = join(candidate, 'index.html');
      if (!existsSync(indexPath)) {
        serveNotFound(res);
        return;
      }
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      createReadStream(indexPath).pipe(res);
      return;
    }
    res.writeHead(200, { 'content-type': MIME_TYPES[extname(candidate)] ?? 'application/octet-stream' });
    createReadStream(candidate).pipe(res);
  });
  return new Promise((resolvePromise) => {
    server.listen(0, '127.0.0.1', () => resolvePromise(server));
  });
}

/** Extracts `{url, lastmodTags}` per `<url>` block, in document order — `lastmodTags` is every
 * `<lastmod>` value found inside that block (0, 1, or more), so the caller can distinguish
 * "missing" from "duplicate" rather than only ever seeing a single optional value silently
 * collapse a malformed multi-tag block into "no lastmod at all" (the previous single-capture-group
 * regex matched the whole `<url>` zero times when a second `<lastmod>` was present, quietly
 * dropping that URL out of `entries` instead of failing on it). Same simple regex approach as
 * assemble-site.mjs's own extractSitemapLocs (both fragments are machine-generated, no CDATA/nested
 * namespaces, so a full XML parser buys nothing here). */
function parseSitemap(xml) {
  return [...xml.matchAll(/<url>([\s\S]*?)<\/url>/g)].map(([, block]) => {
    const locMatch = /<loc>([^<]+)<\/loc>/.exec(block);
    const lastmodTags = [...block.matchAll(/<lastmod>([^<]*)<\/lastmod>/g)].map((match) => match[1]);
    return { url: locMatch ? locMatch[1] : null, lastmodTags };
  });
}

/** A merely regex-shaped `YYYY-MM-DD` string can still name a calendar date that does not exist
 * (`2026-02-31`, or a non-leap-year `2026-02-29`) — `Date`'s own UTC parsing normalizes overflow
 * instead of rejecting it (`2026-02-31` silently becomes March 3rd), so the shape check alone is
 * not enough. Re-serializing the parsed date and comparing it back against the original string
 * catches exactly this: a real date round-trips unchanged; an overflowed one does not. No date
 * library needed — `Date`'s own UTC (`Z`) parsing/formatting is unambiguous and TZ-independent. */
function isRealCalendarDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function h1Count(html) {
  return (html.match(/<h1[\s>]/g) ?? []).length;
}

/**
 * The catch-all shell GitHub Pages serves for every unmatched address must describe itself as a
 * not-found page — not as the home page, which is what a verbatim copy of dist/index.html made it.
 *
 * Each clause below is one of the audited claims, checked against the real built artifact:
 *  - it carries the configured not-found title and description, so it is not simply the home page
 *    under a different status code;
 *  - it carries a `noindex` robots directive. `/404.html` is itself served at 200, so nothing else
 *    keeps this out of an index, and the mismatch between "reads as a real page" and "is not one"
 *    is precisely a soft-404 signal;
 *  - it has NO canonical link and no `og:url`. Either one, pointed anywhere, makes a claim about a
 *    URL that does not exist (see build-seo.mjs's injectNotFoundHead);
 *  - it carries NO structured data. The home page's JSON-LD asserts this URL is the site's WebSite
 *    entity and its Organization's home page, which is untrue of every address that lands here.
 *
 * `expected` comes from the same committed seo-routes.json the build reads, so this compares the
 * artifact against the declared intent rather than against a second copy of the copy.
 */
function assertNotFoundShellSeo(html, expected) {
  if (!expected) {
    throw new Error(
      'verify-seo: seo-routes.json declares no `notFound` metadata — dist/404.html would ship the home page\'s ' +
        'title, description and canonical, which is the exact defect this check exists to prevent',
    );
  }
  const title = extractTag(html, /<title>([\s\S]*?)<\/title>/);
  if (title !== expected.title) {
    throw new Error(`verify-seo: dist/404.html — expected the not-found title "${expected.title}", got "${title}"`);
  }
  const description = extractTag(html, /<meta name="description" content="([^"]*)"/);
  if (description !== expected.description) {
    throw new Error(`verify-seo: dist/404.html — expected the not-found description, got "${description}"`);
  }
  const robots = extractTag(html, /<meta name="robots" content="([^"]*)"/);
  if (robots === null || !/\bnoindex\b/i.test(robots)) {
    throw new Error(
      `verify-seo: dist/404.html — robots directive is ${JSON.stringify(robots)}, which does not say noindex; ` +
        '/404.html is served at 200, so nothing else stops it being indexed',
    );
  }
  if (/<link\s+rel="canonical"/.test(html)) {
    throw new Error(
      'verify-seo: dist/404.html carries a canonical link — a 404 must not name any URL as its canonical ' +
        '(the home page would consolidate a nonexistent URL into a real one; itself would assert the address is real)',
    );
  }
  if (/<meta\s+property="og:url"/.test(html)) {
    throw new Error('verify-seo: dist/404.html carries an og:url — the canonical claim it makes is as untrue as a canonical link');
  }
  const jsonLdBlocks = [...html.matchAll(/<script\b[^>]*type="application\/ld\+json"[^>]*>/gi)];
  if (jsonLdBlocks.length > 0) {
    throw new Error(
      `verify-seo: dist/404.html carries ${jsonLdBlocks.length} JSON-LD block(s) — a not-found page must not ` +
        "assert the home page's WebSite/Organization identity",
    );
  }
}

/**
 * The route-scoped social tags this gate REQUIRES every published page to carry, stated
 * independently of the producer that writes them.
 *
 * Deliberately duplicated rather than imported from build-seo.mjs's `ROUTE_SCOPED_SOCIAL_TAGS` —
 * the same convention withTrailingSlash's own comment below documents, and for a sharper reason
 * than usual. An imported list makes this gate's coverage a function of the very thing it checks:
 * delete an entry from the producer's table and `injectHead` stops rewriting that tag AND this file
 * stops looking for it in the same edit, so every shell silently ships index.html's home-page value
 * for it — the crawler-visible half of the exact defect this pass fixed — with this gate green.
 * (verify-client-nav-seo.mjs cannot cover that either: `usePageSeo` runs on a direct load too, so
 * both sides of its convergence comparison are hook-written and agree regardless of what the shell
 * shipped.) Stated here, a producer-side deletion fails the real build on the first route.
 *
 * The opposite drift — a tag ADDED to the producer's table and not to this one — is closed by
 * verify-seo.test.mjs, which imports both lists and asserts they are equal in both directions. That
 * assertion is the only place the two are allowed to meet.
 */
export const REQUIRED_ROUTE_SCOPED_SOCIAL_TAGS = [
  { attribute: 'property', key: 'og:title', source: 'title' },
  { attribute: 'property', key: 'og:description', source: 'description' },
  { attribute: 'property', key: 'og:url', source: 'canonical' },
  { attribute: 'name', key: 'twitter:title', source: 'title' },
  { attribute: 'name', key: 'twitter:description', source: 'description' },
];

// The site-constant social tags: identical on every page, so index.html is their one home and a
// route change has nothing to re-derive for them (deliberately the complement of the route-scoped
// list above). Checked for PRESENCE and non-emptiness on every served shell rather than against
// expected literals — this file is not a second copy of the site's marketing copy, and a check that
// restated those strings would fail on every legitimate wording change while catching nothing a
// human would not already see.
const CONSTANT_SOCIAL_TAGS = [
  { attribute: 'property', key: 'og:type' },
  { attribute: 'property', key: 'og:site_name' },
  { attribute: 'property', key: 'og:image' },
  { attribute: 'property', key: 'og:image:alt' },
  { attribute: 'name', key: 'twitter:card' },
  { attribute: 'name', key: 'twitter:image' },
  { attribute: 'name', key: 'twitter:image:alt' },
];

// Twitter/X only honours a large summary card when the image is at least this size; below it the
// card silently degrades to the small square form, which is exactly the state this pass moved the
// site off. Declaring `summary_large_image` while shipping an image under it is therefore a real,
// checkable contradiction rather than a matter of taste.
const LARGE_IMAGE_CARD_MIN_WIDTH = 300;
const LARGE_IMAGE_CARD_MIN_HEIGHT = 157;
const RECOMMENDED_LARGE_IMAGE_WIDTH = 1200;
const RECOMMENDED_LARGE_IMAGE_HEIGHT = 630;

/**
 * Proves one served page's social metadata against the page itself, not against a copy of the
 * site's own copywriting:
 *
 *  - every route-scoped tag (REQUIRED_ROUTE_SCOPED_SOCIAL_TAGS above) is present exactly once and
 *    carries the same value as the `<title>` / description meta / canonical link it is derived
 *    from. This is the audited defect stated as an invariant: a page whose og:title says one thing
 *    while its `<title>` says another is wrong no matter which of the two is "right", and it is
 *    exactly the state a client-side navigation used to leave behind;
 *  - every site-constant tag is present and non-empty.
 *
 * `values` accumulates each constant tag's value across pages so the caller can prove they really
 * are constant (and fetch the one declared image once).
 */
function assertSocialMetaConsistent(label, html, values) {
  const derivedFrom = {
    title: extractTag(html, /<title>([\s\S]*?)<\/title>/),
    description: singleMetaContent(html, 'name', 'description', label),
    canonical: extractTag(html, /<link rel="canonical" href="([^"]+)"/),
  };
  for (const { attribute, key, source } of REQUIRED_ROUTE_SCOPED_SOCIAL_TAGS) {
    const actual = singleMetaContent(html, attribute, key, label);
    if (actual === null) {
      throw new Error(`verify-seo: ${label} — no <meta ${attribute}="${key}"> tag at all`);
    }
    if (actual !== derivedFrom[source]) {
      throw new Error(
        `verify-seo: ${label} — ${key} is "${actual}" but the page's own ${source} is ` +
          `"${derivedFrom[source]}"; a page's social metadata must agree with the page it describes`,
      );
    }
  }
  for (const { attribute, key } of CONSTANT_SOCIAL_TAGS) {
    const value = singleMetaContent(html, attribute, key, label);
    if (value === null || value.trim() === '') {
      throw new Error(`verify-seo: ${label} — <meta ${attribute}="${key}"> is missing or empty`);
    }
    if (!values.has(key)) {
      values.set(key, new Map());
    }
    values.get(key).set(value, label);
  }
  // Dimensions are optional in principle but load-bearing here (see index.html) — recorded when
  // declared so the caller can check them against the real image bytes.
  for (const key of ['og:image:width', 'og:image:height']) {
    const value = singleMetaContent(html, 'property', key, label);
    if (value !== null) {
      if (!values.has(key)) {
        values.set(key, new Map());
      }
      values.get(key).set(value, label);
    }
  }
}

/** The one value recorded for `key`, or `null` when the tag never appeared. Only meaningful after
 * `assertConstantSocialTagsAreConstant` has proven there is at most one. */
function onlyValue(values, key) {
  const byValue = values.get(key);
  return byValue ? [...byValue.keys()][0] : null;
}

/** Every site-constant tag must genuinely be constant: one distinct value across the whole
 * published corpus. A tag that differs page to page is either not site-constant after all (and
 * belongs in the route-scoped table, where it would be derived and checked) or is a build that
 * rewrote it on some pages and not others. */
function assertConstantSocialTagsAreConstant(values) {
  for (const [key, byValue] of values) {
    if (byValue.size > 1) {
      const shown = [...byValue].map(([value, label]) => `"${value}" (e.g. ${label})`).join(' vs ');
      throw new Error(
        `verify-seo: <meta ... "${key}"> is supposed to be site-constant but the build published ` +
          `${byValue.size} different values: ${shown}`,
      );
    }
  }
}

// Mounts that exist only in the FINAL composed artifact, never in this module's own dist/:
// agentforge4j-docs/scripts/assemble-site.mjs copies the Docusaurus build to /docs/ and each
// Javadoc surface to /javadoc/**. A link into one of those is a real, correct production link
// this build simply has nothing to answer with, so it is excluded from the crawl below — but
// never on trust: `assertComposedOnlyPrefixesAreAbsentLocally` re-proves on every run that each
// prefix really is absent from this build, so a genuine SPA path can never hide behind an entry
// here and escape the crawl. A declaration that nothing links into any more is harmless by
// construction and deliberately not treated as an error: an exclusion can only ever remove link
// targets that were actually found, so an unused one narrows nothing — the failure mode worth
// guarding is the opposite one, an entry shadowing a path this build really does serve.
const COMPOSED_ONLY_LINK_PREFIXES = ['/docs/', '/javadoc/'];

/** Every site-internal `<a href>` target in `html` — the page served at `sourcePath` — as request
 * paths, with any `#fragment` and `?query` dropped and duplicates left in (the caller deduplicates
 * across pages).
 *
 * Reads the anchor the way `extractJsonLdScripts` reads a script tag — tokenise the element, then
 * look the attribute up by name — so all three quoting forms the HTML tokenizer accepts
 * (`href="x"`, `href='x'`, bare `href=x`) are read, attribute order is free, and a quoted value
 * carrying a literal `>` does not truncate the tag (see `tagSource`). Anchoring on one exact
 * spelling would make this a statement about how today's producers happen to format their markup
 * rather than about the links the served page really contains — the same distinction
 * `extractJsonLdScripts`'s own comment draws — and a link this missed is a link the crawl below
 * would silently never check.
 *
 * Internal means same-origin *after resolution against the page's own address*, not "the href
 * starts with a slash". A bare-form `https://agentforge4j.org/api`, or a bare-form relative `api`
 * on `/`, redirects in production exactly like the root-relative `/api` this gate was written for,
 * and a rule keyed on the literal spelling waves both through — the same how-it-is-written versus
 * what-it-resolves-to distinction as above, one level up. Resolving also settles the
 * protocol-relative case by construction instead of by special case: `//example.com/x` lands
 * off-origin and drops out, while `//agentforge4j.org/x` really is an internal link and is crawled.
 * `mailto:`, `tel:` and every other non-site origin drop out the same way.
 *
 * Fragment-only (`#main-content`, the skip link) and query-only hrefs address the page they are
 * already on rather than a route, and are skipped before resolution so the crawl reports link
 * targets rather than handing every page its own address back.
 *
 * HTML comments are stripped first. Unlike the JSON-LD readers — where a commented-out block being
 * seen is fail-closed — an anchor found inside a comment is a link no browser can follow, so
 * counting it would fail the build over markup that ships inert.
 *
 * Scope stated rather than assumed: `<a>` only (this site renders no `<area>` or other link
 * element), and attribute values are read raw rather than HTML-entity-decoded — no producer here
 * emits an entity inside an href, and that direction fails towards checking a link at its literal
 * spelling rather than skipping it.
 *
 * Exported so the extraction rule itself is directly testable against real served markup rather
 * than only through the end-to-end gate. */
export function extractInternalLinkTargets(html, sourcePath) {
  const base = `${SITE_ORIGIN}${sourcePath}`;
  return [...stripHtmlComments(html).matchAll(ANCHOR_TAG_PATTERN)]
    .map((match) => attributeValue(match[1], HREF_ATTR_PATTERN))
    .filter((href) => href !== null && href !== '' && !href.startsWith('#') && !href.startsWith('?'))
    .map((href) => resolveSameOrigin(href, base))
    .filter((resolved) => resolved !== null)
    .map((resolved) => resolved.pathname);
}

/** `html` with every HTML comment removed, so a reader below cannot mistake commented-out markup
 * for markup the page actually renders. */
function stripHtmlComments(html) {
  return html.replace(/<!--[\s\S]*?-->/g, '');
}

/** Re-proves, on every run, that no `COMPOSED_ONLY_LINK_PREFIXES` entry is shadowing a path this
 * build really serves — see that constant's own comment. */
function assertComposedOnlyPrefixesAreAbsentLocally(distDir) {
  for (const prefix of COMPOSED_ONLY_LINK_PREFIXES) {
    const localPath = join(distDir, ...prefix.split('/').filter(Boolean));
    if (existsSync(localPath)) {
      throw new Error(
        `verify-seo: "${prefix}" is excluded from the internal-link crawl as composed-artifact-only, but this ` +
          `build really does serve ${localPath} — it is a real path of this module's own and must be crawled, ` +
          'not excluded',
      );
    }
  }
}

/** `{width, height}` from a PNG's IHDR header. PNG is the only format this build publishes as a
 * social image; anything else must extend this rather than be waved through, so an unrecognized
 * format throws instead of silently skipping the dimension agreement check its caller performs. */
function pngDimensions(bytes) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (bytes.length < 24 || Buffer.compare(bytes.subarray(0, 8), signature) !== 0) {
    throw new Error(
      'verify-seo: the declared og:image is not a PNG — this check reads dimensions from a PNG IHDR header only. ' +
        'Extend it for the new format rather than leaving the declared og:image:width/height unverified.',
    );
  }
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

function extractTag(html, pattern) {
  const match = pattern.exec(html);
  return match ? match[1] : null;
}

// Mirrors build-seo.mjs's own withTrailingSlash exactly. Duplicated deliberately, not imported:
// this script independently reads the same committed seo-routes.json build-seo.mjs itself reads,
// rather than importing internals of one script from the other — the same "read the same
// source-of-truth config independently" convention prerender-routes.mjs's own header comment
// documents for the identical situation.
function withTrailingSlash(routePath) {
  return routePath === '/' ? '/' : `${routePath.replace(/\/+$/, '')}/`;
}

/** The real static-route verification inventory — every route declared in the committed
 * seo-routes.json, not a hand-maintained subset. This is the only way a route excluded from the
 * sitemap (e.g. `/contributing`, `sitemap: false`) still gets its raw-HTML/H1/canonical checked at
 * all: the sitemap-driven loop below never sees a route that isn't in the sitemap.
 *
 * Each entry's `expectedCanonical` is the route's own trailing-slash URL, or its `canonicalPath`
 * target's trailing-slash URL when the route is a declared alias — normalized the same way
 * build-seo.mjs itself normalizes them when it writes the real canonical tag, so this check fails
 * the moment the two ever disagree. */
export function loadStaticRouteInventory(seoRoutesPath) {
  const { siteUrl, routes } = JSON.parse(readFileSync(seoRoutesPath, 'utf8'));
  return routes.map((route) => ({
    requestPath: withTrailingSlash(route.path),
    expectedCanonical: `${siteUrl}${withTrailingSlash(route.canonicalPath ?? route.path)}`,
    // Present only when the route actually declares one, so an entry with no jsonLd deep-equals
    // exactly what every existing hand-built inventory fixture (and the tests asserting against
    // one) already expects — no `jsonLd: undefined` key showing up where none existed before.
    ...(route.jsonLd !== undefined ? { jsonLd: route.jsonLd } : {}),
  }));
}

/** Matches `name="v"`, `name='v'` and bare `name=v` in a raw attribute list, with optional
 * whitespace either side of the `=` — every form the HTML tokenizer accepts for an attribute value.
 * The name must be preceded by the start of the list or a separator, so `data-id=` can never be
 * read as `id=` (a plain `\bid=` matches inside `data-id`, since `-` is a word boundary).
 *
 * Built once per attribute rather than inline at each call site: one reader, so the `type` and `id`
 * matchers below cannot drift into recognising different subsets of legal HTML — which is exactly
 * how the earlier quoted-only pair came to disagree with the browser. */
function attributePattern(name) {
  return new RegExp(`(?:^|[\\s/])${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s"'\`=<>]+))`, 'i');
}

/** The source of a start-tag matcher for `name`, capturing its raw attribute list.
 *
 * A `>` only ends the tag when it sits outside an attribute value, exactly as the HTML tokenizer
 * treats it — so a `title="a > b"` next to the attribute being read cannot truncate the tag and
 * hide the element from whichever reader below is looking for it. Built once, for the same reason
 * `attributePattern` is: two hand-written tag regexes in one file drift into recognising different
 * subsets of legal HTML, and here the drift is silent in the worst direction (an element skipped is
 * an element never checked). The alternatives are disjoint on their first character, so there is no
 * backtracking to worry about. */
function tagSource(name) {
  return `<${name}\\b((?:[^>"']|"[^"]*"|'[^']*')*)>`;
}

const TYPE_ATTR_PATTERN = attributePattern('type');
const ID_ATTR_PATTERN = attributePattern('id');
const CONTENT_ATTR_PATTERN = attributePattern('content');
const HREF_ATTR_PATTERN = attributePattern('href');
const ANCHOR_TAG_PATTERN = new RegExp(tagSource('a'), 'gi');
const META_TAG_PATTERN = new RegExp(tagSource('meta'), 'gi');
const JSON_LD_SCRIPT_PATTERN = new RegExp(`${tagSource('script')}([\\s\\S]*?)<\\/script>`, 'g');

/** `value` resolved against `base`, or `null` when it names another origin or is not a URL a
 * browser could follow at all. One reader for the only question this file ever asks of a raw URL
 * string — is this address on our own site, after resolution rather than by its spelling. */
function resolveSameOrigin(value, base) {
  let resolved;
  try {
    resolved = new URL(value, base);
  } catch {
    return null;
  }
  return resolved.origin === SITE_ORIGIN ? resolved : null;
}

/** The raw, untrimmed value of an attribute in `attrs`, or `null` when it is absent. Whichever of
 * the three quoting forms matched, exactly one of the three groups is set. */
function attributeValue(attrs, pattern) {
  const match = pattern.exec(attrs);
  return match ? (match[1] ?? match[2] ?? match[3]) : null;
}

/** Every `<meta>` element's raw attribute list, in document order — read through the shared
 * `tagSource` matcher, so a `>` inside an attribute value cannot truncate the tag and hide the
 * element from the readers below. That is the whole reason `tagSource` exists: a second
 * hand-written tag regex in this file would drift into recognising a different subset of legal
 * HTML from the one `extractJsonLdScripts` and `extractInternalLinkTargets` accept, silently and
 * in the worst direction — a meta element skipped here reads as absent, which fails the build on a
 * page that is in fact correct, and lets a genuine duplicate go uncounted. */
function metaAttributeLists(html) {
  return [...html.matchAll(META_TAG_PATTERN)].map((match) => match[1]);
}

/** The `content` of the single `<meta>` identified by `attribute="key"`, or `null` when absent —
 * and a throw when the page carries more than one, which is the failure mode a client-side sync
 * that appends instead of updating would produce: a crawler reads the first, a human debugging the
 * page reads the last, and both are "present and correct" to any check that stops at the first hit.
 *
 * Reads the tag the way `extractJsonLdScripts` reads a script tag — tokenise the element, then look
 * up attributes by name — rather than by matching one exact spelling. The earlier form required the
 * `content` attribute to sit immediately after the identifying one and required both to be
 * double-quoted, so a tag written `<meta content="…" property="og:title">`, or with single quotes,
 * was reported as absent. That direction fails closed, but it makes the check a statement about one
 * producer's formatting rather than about the page a crawler receives — the exact distinction
 * `extractJsonLdScripts`'s own comment draws, and this file should not answer it two different ways.
 *
 * Fail-closed behaviour is unchanged: a genuinely missing tag still reads as `null` (the caller
 * rejects it) and duplicates still throw here.
 *
 * `label` names the page under inspection. It is required rather than optional because this is the
 * one assertion in the social pass that only real `dist/` output can trigger, and a corpus of 25
 * pages makes an unattributed "on one page" unactionable — every sibling assertion in
 * `assertSocialMetaConsistent` already names the page it failed on. */
function singleMetaContent(html, attribute, key, label) {
  const identifying = attributePattern(attribute);
  const contents = metaAttributeLists(html)
    .filter((attrs) => attributeValue(attrs, identifying) === key)
    .map((attrs) => attributeValue(attrs, CONTENT_ATTR_PATTERN));
  if (contents.length > 1) {
    throw new Error(
      `verify-seo: ${label} — ${contents.length} <meta ${attribute}="${key}"> tags on one page — expected exactly one`,
    );
  }
  return contents.length === 1 ? contents[0] : null;
}

/** Every `<script>` block whose `type` attribute is `application/ld+json`, in document order —
 * `{ id, content }` pairs. build-seo.mjs's own `assertValidJsonLd` already rejects a malformed
 * config at build time — by the time this runs against real `dist/` output, a config bad enough to
 * reach here at all would mean that gate itself regressed, which this catches too (a script whose
 * content fails to parse as JSON, below).
 *
 * Deliberately tolerant of how the attributes are *written*, not just of the one form
 * `injectJsonLd` happens to emit. Anchoring on `injectJsonLd`'s exact output would make this a
 * statement about that one producer rather than about the served page, and this function's most
 * important caller is the opposite check — proving a route that declares no structured data is
 * carrying none, from ANY producer (a third-party component, a hand-edited shell, a future
 * generator). So attribute order is free, all three quoting forms are read, whitespace may sit
 * either side of the `=`, and the `type` comparison is case-insensitive and whitespace-stripped —
 * each of those being a form that renders as real structured data in production while a stricter
 * regex reports the page as clean.
 *
 * The `type` value is stripped before comparison because the HTML spec strips it before
 * classifying a script block; the `id` value deliberately is NOT. An `id=" seo-json-ld "` really is
 * an id with spaces in it, which `document.getElementById('seo-json-ld')` does not find — trimming
 * it here would wave through precisely the shell the id check exists to reject.
 *
 * An attribute value containing a literal `>` is handled too, via the shared `tagSource` tokenizer
 * — previously it ended the tag early, which under-detected on the one caller (stray-JSON-LD
 * absence) where under-detection is the unsafe direction. */
function extractJsonLdScripts(html) {
  return [...html.matchAll(JSON_LD_SCRIPT_PATTERN)]
    .filter((match) => attributeValue(match[1], TYPE_ATTR_PATTERN)?.trim().toLowerCase() === 'application/ld+json')
    .map((match) => ({ id: attributeValue(match[1], ID_ATTR_PATTERN), content: match[2] }));
}

/** The asset-valued keys in a JSON-LD block whose target this build is supposed to have produced —
 * an `Organization`'s `logo` and any node's `image`. Not `@id`/`url`: those are identifiers and page
 * addresses, already covered by the sitemap/canonical checks and by build-seo.test.mjs's own
 * origin guard, and an `@id` is frequently a bare fragment that names no retrievable resource at
 * all. */
const ASSET_KEYS = new Set(['logo', 'image']);

/** The URL strings an asset-valued key can carry, covering every shape schema.org permits for
 * `logo`/`image`: a bare URL string, an array of either form, or a full `ImageObject`-style node
 * whose own `url` names the file. Reading only the bare string would silently skip the
 * `ImageObject` form rather than verify it — and that form is the more idiomatic schema.org
 * spelling, so it is the likeliest next edit to this config, not an exotic one.
 *
 * Mirrored by build-seo.test.mjs's `selfReferentialUrls`, which does the same shape flattening for
 * `@id`/`url`/`logo`. The two walk the same tree and must not cover different shapes. */
function assetUrlStrings(value) {
  if (typeof value === 'string') {
    return [value];
  }
  if (Array.isArray(value)) {
    return value.flatMap(assetUrlStrings);
  }
  if (value !== null && typeof value === 'object' && typeof value.url === 'string') {
    return [value.url];
  }
  return [];
}

/** Every same-origin asset path (`logo`/`image`) declared anywhere in `jsonLd`, as request paths
 * this server can serve, deduplicated and in document order.
 *
 * Off-origin values are deliberately skipped rather than fetched: this gate verifies what THIS
 * build produced, and reaching out to a third-party host would make a local build gate depend on
 * someone else's uptime. (`logo` on another origin is separately rejected at build time by
 * build-seo.test.mjs's self-referencing-URL guard; `image` is not, so it is skipped here rather
 * than assumed on-site.) A value that is not a URL reference at all is skipped for the same
 * reason — there is nothing to fetch. */
function sameOriginAssetPaths(jsonLd) {
  const paths = new Set();
  const consider = (value) => {
    const resolved = resolveSameOrigin(value, `${SITE_ORIGIN}/`);
    if (resolved !== null) {
      paths.add(`${resolved.pathname}${resolved.search}`);
    }
  };
  const walk = (node) => {
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (node === null || typeof node !== 'object') {
      return;
    }
    for (const [key, value] of Object.entries(node)) {
      if (ASSET_KEYS.has(key)) {
        assetUrlStrings(value).forEach(consider);
      }
      walk(value);
    }
  };
  walk(jsonLd);
  return [...paths];
}

export async function verifySeo({
  distDir = DIST_DIR,
  seoRoutesPath = SEO_ROUTES_PATH,
  staticRoutes = loadStaticRouteInventory(seoRoutesPath),
} = {}) {
  const sitemapPath = join(distDir, 'sitemap.xml');
  if (!existsSync(sitemapPath)) {
    throw new Error(`verify-seo: ${sitemapPath} does not exist — run the full build first`);
  }
  const entries = parseSitemap(readFileSync(sitemapPath, 'utf8'));
  if (entries.length === 0) {
    throw new Error('verify-seo: sitemap.xml parsed to zero <url> entries — check the regex against the real file');
  }
  for (const entry of entries) {
    if (entry.url === null) {
      throw new Error('verify-seo: a <url> block in dist/sitemap.xml has no <loc>');
    }
  }
  const urls = entries.map((entry) => entry.url);
  if (new Set(urls).size !== urls.length) {
    throw new Error('verify-seo: duplicate URL(s) found in the real dist/sitemap.xml');
  }

  // dist/404.html is GitHub Pages' catch-all for every unmatched path, served under a real HTTP
  // 404 — it must stay the empty pre-prerender SPA shell (copy-404.mjs runs before build-seo.mjs
  // for exactly this reason). If it ever carried prerendered body content, every mistyped URL
  // would statically display the full home page under a 404 status until the JS bundle runs —
  // and permanently for any client that never runs it.
  const notFoundPath = join(distDir, '404.html');
  if (!existsSync(notFoundPath)) {
    throw new Error(`verify-seo: ${notFoundPath} does not exist — run the full build first`);
  }
  const notFoundHtml = readFileSync(notFoundPath, 'utf8');
  if (!/<div id="root"><\/div>/.test(notFoundHtml)) {
    throw new Error(
      'verify-seo: dist/404.html no longer contains an empty <div id="root"></div> mount point — ' +
        'it must stay the pre-prerender SPA shell (copy-404.mjs must run before build-seo.mjs, ' +
        'never after it)',
    );
  }
  assertNotFoundShellSeo(notFoundHtml, JSON.parse(readFileSync(seoRoutesPath, 'utf8')).notFound);

  // Which served paths have a declaration of their own to be checked against, below. Every other
  // sitemap URL is a route that can only legitimately carry zero structured data — see the sitemap
  // loop's own stray-JSON-LD check.
  const staticRequestPaths = new Set(staticRoutes.map((route) => route.requestPath));

  // key -> (value -> first page that carried it). Filled by assertSocialMetaConsistent on every
  // served page; read afterwards to prove the site-constant tags really are constant and that the
  // one declared social image is real and the size it claims to be.
  const constantSocialValues = new Map();
  let socialImageChecked = 'none declared';

  // Every page this run actually served, keyed by the path it was served at — filled by both
  // loops below and consumed once by the internal-link crawl after them, so the crawl covers the
  // union of "every sitemap URL" and "every configured static route" (a superset of either alone:
  // catalogue detail shells are only in the first, `sitemap: false` aliases only in the second)
  // without re-fetching a single page.
  const servedHtmlByPath = new Map();
  let internalLinksChecked = 0;

  const server = await startGhPagesEmulatingServer(distDir);
  const { port } = server.address();
  const origin = `http://127.0.0.1:${port}`;

  try {
    // Self-check: the emulation must itself be faithful, or every "no redirect" assertion below is
    // vacuous. Any non-root sitemap URL is a real directory this build always produces; requesting
    // its bare (non-slash) form must redirect, exactly like the real GitHub Pages bug this pass
    // fixes — proving a future regression that reintroduces a non-slash sitemap URL would be caught.
    const nonRootPath = urls.map((url) => url.slice(SITE_ORIGIN.length)).find((path) => path !== '/');
    if (nonRootPath) {
      const bareForm = nonRootPath.replace(/\/$/, '');
      const selfCheck = await fetch(`${origin}${bareForm}`, { redirect: 'manual' });
      if (selfCheck.status !== 301) {
        throw new Error(
          `verify-seo: the GitHub-Pages-emulating test server did not 301 a bare directory path as expected ` +
            `(got ${selfCheck.status}) — this check's own harness is not faithful, fix it before trusting the rest`,
        );
      }
    }

    for (const { url, lastmodTags } of entries) {
      if (!url.startsWith(`${SITE_ORIGIN}/`)) {
        throw new Error(`verify-seo: sitemap URL outside ${SITE_ORIGIN}: ${url}`);
      }

      // Every real SPA route's <lastmod> is now git-derived from at least its always-non-empty
      // globalSourceFiles (see build-seo.mjs) — a production sitemap where any URL carries none at
      // all is a genuine regression (e.g. globalSourceFiles emptied by mistake), not a legitimate
      // state, and must fail the real build gate rather than silently pass through. This check is
      // deliberately real-build-only: unit-level buildSeo() fixture tests are free to omit
      // sourceFiles/git history for a synthetic route (see build-seo.test.mjs) — that allowance
      // stops here, at the production verification gate.
      if (lastmodTags.length === 0) {
        throw new Error(`verify-seo: ${url} has no <lastmod> — every SPA sitemap URL must carry exactly one valid <lastmod>`);
      }
      if (lastmodTags.length > 1) {
        throw new Error(`verify-seo: ${url} has ${lastmodTags.length} <lastmod> tags — expected exactly one`);
      }
      const [lastmod] = lastmodTags;
      if (!isRealCalendarDate(lastmod)) {
        throw new Error(`verify-seo: ${url} — <lastmod>${lastmod}</lastmod> is not a valid real calendar date (YYYY-MM-DD)`);
      }

      const path = url.slice(SITE_ORIGIN.length);
      const response = await fetch(`${origin}${path}`, { redirect: 'manual' });
      if (response.status !== 200) {
        throw new Error(`verify-seo: sitemap URL ${url} did not return 200 with no redirect (got ${response.status})`);
      }
      const html = await response.text();
      servedHtmlByPath.set(path, html);

      // Only `seo-routes.json` routes can declare `jsonLd`, and the loop below checks every one of
      // them against its own declaration. A sitemap URL that is NOT one of those routes — every
      // /catalogue/<id>/ detail shell — can therefore only ever legitimately carry zero structured
      // data, and nothing else in this file would notice if it started carrying some. Checking it
      // here is what makes "no JSON-LD anywhere it was never declared" a statement about the whole
      // published site rather than only about the configured static routes.
      if (!staticRequestPaths.has(path)) {
        const strayJsonLd = extractJsonLdScripts(html);
        if (strayJsonLd.length > 0) {
          throw new Error(
            `verify-seo: ${url} has ${strayJsonLd.length} JSON-LD script(s) but declares no jsonLd in seo-routes.json ` +
              '— only a configured static route may carry structured data',
          );
        }
      }

      const canonical = extractTag(html, /<link rel="canonical" href="([^"]+)"/);
      if (canonical !== url) {
        throw new Error(`verify-seo: ${url} — canonical tag ("${canonical}") does not match its own sitemap URL exactly`);
      }

      const count = h1Count(html);
      if (count !== 1) {
        throw new Error(`verify-seo: ${url} — expected exactly one <h1> in the raw served HTML, found ${count}`);
      }

      assertSocialMetaConsistent(url, html, constantSocialValues);
    }

    for (const { requestPath, expectedCanonical, jsonLd } of staticRoutes) {
      const response = await fetch(`${origin}${requestPath}`, { redirect: 'manual' });
      if (response.status !== 200) {
        throw new Error(
          `verify-seo: configured route ${requestPath} did not return 200 with no redirect (got ${response.status})`,
        );
      }
      const html = await response.text();
      servedHtmlByPath.set(requestPath, html);

      // Proven against the real dist/ output this build actually produced, not just a fixture:
      // a route that declares jsonLd must carry exactly one JSON-LD script whose content parses as
      // JSON and matches the declared config exactly; a route that declares none must carry none —
      // catches both a build regression that silently drops it and one that leaks a previous/
      // unrelated route's structured data onto this one.
      const jsonLdScripts = extractJsonLdScripts(html);
      if (jsonLd === undefined) {
        if (jsonLdScripts.length > 0) {
          throw new Error(
            `verify-seo: ${requestPath} has ${jsonLdScripts.length} JSON-LD script(s) but declares no jsonLd in seo-routes.json`,
          );
        }
      } else {
        if (jsonLdScripts.length !== 1) {
          throw new Error(`verify-seo: ${requestPath} has ${jsonLdScripts.length} JSON-LD script(s) — expected exactly 1`);
        }
        // Must carry the shared script id — a served script with no id (or a different one) would
        // pass the content check below yet still leave the client-side hook unable to find it,
        // creating a second, duplicate JSON-LD block on hydration and permanently stranding this
        // one after the first client-side navigation.
        //
        // Scoped honestly: this compares the SERVED page against the same constant injectJsonLd
        // writes, so what it catches is a shell whose script lost or changed its id by some route
        // other than that constant — a hand-edited shell, a third-party or future generator, a
        // post-processing step. It cannot catch a rename of the constant itself, since producer and
        // verifier would move together. The binding that does catch that is
        // tests/usePageSeo.test.tsx's assertion on the id the HOOK creates, which is the only place
        // usePageSeo.ts's own copy of the literal is compared against this one.
        if (jsonLdScripts[0].id !== JSON_LD_SCRIPT_ID) {
          throw new Error(
            `verify-seo: ${requestPath}'s JSON-LD script has id ${JSON.stringify(jsonLdScripts[0].id)} — expected ` +
              `${JSON.stringify(JSON_LD_SCRIPT_ID)} so client-side hydration (usePageSeo.ts) adopts it instead of duplicating it`,
          );
        }
        let servedJsonLd;
        try {
          servedJsonLd = JSON.parse(jsonLdScripts[0].content);
        } catch (err) {
          throw new Error(`verify-seo: ${requestPath}'s JSON-LD script does not parse as valid JSON — ${err.message}`, { cause: err });
        }
        if (JSON.stringify(servedJsonLd) !== JSON.stringify(jsonLd)) {
          throw new Error(`verify-seo: ${requestPath}'s served JSON-LD does not match its declared seo-routes.json config`);
        }

        // Every same-origin asset the structured data points at must actually be in this build.
        // A renamed or removed `logo`/`image` target is otherwise invisible to every gate: the
        // origin guard only asks whose host it is, assertValidJsonLd never looks at it, and the
        // served-vs-declared comparison above is satisfied by two copies of the same dead URL. The
        // failure it prevents is a real one — an `Organization` whose logo 404s is a Search Console
        // structured-data error that no build ever complains about. Fetched through the same
        // dist/-backed server every other check here uses, so this asks the one question that
        // matters: would a crawler hitting the published site get this file?
        for (const assetPath of sameOriginAssetPaths(jsonLd)) {
          const assetResponse = await fetch(`${origin}${assetPath}`, { redirect: 'manual' });
          if (assetResponse.status !== 200) {
            throw new Error(
              `verify-seo: ${requestPath}'s JSON-LD points at ${assetPath}, which this build does not serve ` +
                `(got ${assetResponse.status}) — structured data must not name an asset that is not published`,
            );
          }
        }
      }

      const canonical = extractTag(html, /<link rel="canonical" href="([^"]+)"/);
      if (canonical !== expectedCanonical) {
        throw new Error(
          `verify-seo: ${requestPath} — expected canonical "${expectedCanonical}", got "${canonical}"`,
        );
      }

      const count = h1Count(html);
      if (count !== 1) {
        throw new Error(`verify-seo: ${requestPath} — expected exactly one <h1> in the raw served HTML, found ${count}`);
      }
      const h1Text = extractTag(html, /<h1[^>]*>([\s\S]*?)<\/h1>/);
      if (!h1Text || !h1Text.replace(/<[^>]+>/g, '').trim()) {
        throw new Error(`verify-seo: ${requestPath} — the raw <h1> has no real visible text content`);
      }

      assertSocialMetaConsistent(requestPath, html, constantSocialValues);
    }

    assertConstantSocialTagsAreConstant(constantSocialValues);

    // The declared social image, proven against the real bytes this build serves. Three separate
    // ways a social card silently breaks, none of which any other gate here can see: the image URL
    // names a file the build does not publish; the declared og:image:width/height describe a
    // different image than the one shipped (a crawler that trusts them reserves the wrong box, and
    // a mismatch is the normal outcome of swapping the asset without touching index.html); and a
    // `summary_large_image` card whose image is under the platform's own minimum, which is not
    // rejected — it just silently renders as the small square card the site was moving away from.
    const declaredImage = onlyValue(constantSocialValues, 'og:image');
    if (declaredImage !== null) {
      const imageUrl = new URL(declaredImage, `${SITE_ORIGIN}/`);
      if (imageUrl.origin !== SITE_ORIGIN) {
        throw new Error(
          `verify-seo: og:image "${declaredImage}" is off-origin — this gate verifies what THIS build ` +
            'publishes, and a social image the site does not own cannot be proven to exist or to be the right size',
        );
      }
      const imageResponse = await fetch(`${origin}${imageUrl.pathname}`, { redirect: 'manual' });
      if (imageResponse.status !== 200) {
        throw new Error(
          `verify-seo: og:image ${imageUrl.pathname} is not served by this build (got ${imageResponse.status}) — ` +
            'every social card would fall back to no image at all',
        );
      }
      const { width, height } = pngDimensions(Buffer.from(await imageResponse.arrayBuffer()));

      const declaredWidth = onlyValue(constantSocialValues, 'og:image:width');
      const declaredHeight = onlyValue(constantSocialValues, 'og:image:height');
      if (declaredWidth !== null && Number(declaredWidth) !== width) {
        throw new Error(`verify-seo: og:image:width declares ${declaredWidth} but the served image is ${width}px wide`);
      }
      if (declaredHeight !== null && Number(declaredHeight) !== height) {
        throw new Error(`verify-seo: og:image:height declares ${declaredHeight} but the served image is ${height}px tall`);
      }

      if (onlyValue(constantSocialValues, 'twitter:card') === 'summary_large_image') {
        if (width < LARGE_IMAGE_CARD_MIN_WIDTH || height < LARGE_IMAGE_CARD_MIN_HEIGHT) {
          throw new Error(
            `verify-seo: twitter:card is summary_large_image but the image is only ${width}x${height} — below the ` +
              `${LARGE_IMAGE_CARD_MIN_WIDTH}x${LARGE_IMAGE_CARD_MIN_HEIGHT} minimum, so the card silently degrades ` +
              'to the small summary form',
          );
        }
        if (width < RECOMMENDED_LARGE_IMAGE_WIDTH || height < RECOMMENDED_LARGE_IMAGE_HEIGHT) {
          throw new Error(
            `verify-seo: twitter:card is summary_large_image but the image is only ${width}x${height} — under the ` +
              `${RECOMMENDED_LARGE_IMAGE_WIDTH}x${RECOMMENDED_LARGE_IMAGE_HEIGHT} a large card needs to render ` +
              'sharply across platforms',
          );
        }
      }
      socialImageChecked = `${imageUrl.pathname} (${width}x${height})`;
    }

    // Internal-link crawl. Canonicals and sitemap URLs are published in the trailing-slash form
    // GitHub Pages serves directly, but the site's own navigation is a separate surface that can
    // (and did) disagree with them: every generated route is a directory, so a bare-form `href`
    // costs a 301 on every internal click and every crawl hop, and makes the site link to a URL
    // its own canonical says is not the address of that page. Asking the real production build
    // over real HTTP — through the same GitHub-Pages-emulating server whose own 301 behaviour is
    // self-checked above — is what makes this a statement about what visitors and crawlers get,
    // rather than about one config file's literals: it holds for links from nav.ts, from page
    // components, and from anywhere a future link is added to the served markup, none of which
    // this file enumerates.
    //
    // Scoped honestly: the corpus is the PRERENDERED markup of each served page, so a link that
    // only exists after a client-side interaction (the header's mobile menu panel, and any future
    // modal or drawer) is not in it and cannot be — there is no browser here to open it. The one
    // such surface that exists today is covered at the component level instead, by
    // tests/App.test.tsx's canonical-internal-link-form assertion, which renders the shell with
    // the menu open; a new one would have to be added there too. The two are complements, not one
    // guard and its duplicate.
    const sourcesByTarget = new Map();
    for (const [sourcePath, html] of servedHtmlByPath) {
      for (const target of extractInternalLinkTargets(html, sourcePath)) {
        if (!sourcesByTarget.has(target)) {
          sourcesByTarget.set(target, new Set());
        }
        sourcesByTarget.get(target).add(sourcePath);
      }
    }
    assertComposedOnlyPrefixesAreAbsentLocally(distDir);

    const crawlableTargets = [...sourcesByTarget.keys()].filter(
      (target) => !COMPOSED_ONLY_LINK_PREFIXES.some((prefix) => target.startsWith(prefix)),
    );
    // Non-vacuity, stated as a real precondition rather than assumed: a corpus whose pages
    // collectively link nowhere would make every assertion in the loop below true by having
    // nothing to assert over, and would report the same clean line as a genuinely correct build.
    if (crawlableTargets.length === 0) {
      throw new Error(
        `verify-seo: found no crawlable internal links at all across ${servedHtmlByPath.size} served page(s) — ` +
          'the internal-link check would be vacuous, so this is a failure, not a pass',
      );
    }
    for (const target of crawlableTargets.sort()) {
      const response = await fetch(`${origin}${target}`, { redirect: 'manual' });
      if (response.status !== 200) {
        const sources = [...sourcesByTarget.get(target)].sort().join(', ');
        throw new Error(
          `verify-seo: internal link ${target} (linked from ${sources}) did not return 200 with no redirect ` +
            `(got ${response.status}) — every internal link must target the canonical trailing-slash form the ` +
            'host serves directly, not a form that redirects to it',
        );
      }
      // Counted as each target actually clears, never set to the intended total up front: the
      // number this run reports is then the number it really proved, even if it exits early.
      internalLinksChecked += 1;
    }
  } finally {
    await new Promise((resolvePromise) => server.close(resolvePromise));
  }

  // Scoped to exactly what was checked. The JSON-LD absence claim covers every sitemap URL and
  // every configured static route — between them, every shell build-seo.mjs writes — but NOT
  // dist/404.html, which is neither, and is gated separately above (empty pre-prerender shell).
  // Keep this sentence and the checks above in step: a claim of absence is only worth as much as
  // the set it was actually evaluated over.
  console.log(
    `[verify-seo] verified ${entries.length} sitemap URL(s) (200, no redirect, self-canonical, exactly one real <h1>) ` +
      `and ${staticRoutes.length} configured static route(s) (200, no redirect, exactly one real <h1>, expected canonical, ` +
      `declared JSON-LD present with the shared script id and matching content, every same-origin asset it names served ` +
      `200) — with no JSON-LD on any sitemap URL or configured route that declares none — and, on every one of those ` +
      `pages, each of the ${REQUIRED_ROUTE_SCOPED_SOCIAL_TAGS.length} route-scoped social tags present exactly once and equal ` +
      `to the page's own title/description/canonical, every site-constant social tag present, non-empty and genuinely ` +
      `constant across the corpus, social image ${socialImageChecked} — plus ${internalLinksChecked} ` +
      `distinct internal link target(s) across those pages, each served 200 with no redirect (composed-artifact-only ` +
      `mounts ${COMPOSED_ONLY_LINK_PREFIXES.join(', ')} excluded, each re-proven absent from this build so none can ` +
      'be shadowing a real path) — all clean',
  );
}

if (process.argv[1]?.endsWith('verify-seo.mjs')) {
  verifySeo().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
