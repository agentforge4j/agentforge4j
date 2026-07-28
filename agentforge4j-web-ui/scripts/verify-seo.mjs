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
// buy independence, only a third place to drift. See build-seo.mjs's own comment on the constant.
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
 * index.html directly at 200; a real file serves as itself. No SPA fallback here (unlike
 * prerender-routes.mjs's own static server) — this must reproduce exactly what a real static host
 * serves for the shells build-seo.mjs actually wrote, not fall back to a generic entry document. */
export function startGhPagesEmulatingServer(distDir) {
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
      res.writeHead(404);
      res.end('not found');
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
        res.writeHead(404);
        res.end('not found');
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
    expectedCanonical: `${siteUrl}${withTrailingSlash(route.redirectTo ?? route.canonicalPath ?? route.path)}`,
    // A redirect route is checked as a redirect stub, not as a page: it deliberately has no <h1>
    // and no content of its own, which is the entire point of it.
    ...(route.redirectTo !== undefined ? { redirectTarget: `${siteUrl}${withTrailingSlash(route.redirectTo)}` } : {}),
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

const TYPE_ATTR_PATTERN = attributePattern('type');
const ID_ATTR_PATTERN = attributePattern('id');

/** The raw, untrimmed value of an attribute in `attrs`, or `null` when it is absent. Whichever of
 * the three quoting forms matched, exactly one of the three groups is set. */
function attributeValue(attrs, pattern) {
  const match = pattern.exec(attrs);
  return match ? (match[1] ?? match[2] ?? match[3]) : null;
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
 * Not handled, and not claimed to be: an attribute value containing a literal `>`, which the outer
 * tag regex ends the tag on. No producer here emits one, and the failure direction is over- rather
 * than under-detection. */
function extractJsonLdScripts(html) {
  return [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)]
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
    let resolved;
    try {
      resolved = new URL(value, `${SITE_ORIGIN}/`);
    } catch {
      return;
    }
    if (resolved.origin === SITE_ORIGIN) {
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
  if (!/<div id="root"><\/div>/.test(readFileSync(notFoundPath, 'utf8'))) {
    throw new Error(
      'verify-seo: dist/404.html no longer contains an empty <div id="root"></div> mount point — ' +
        'it must stay the pre-prerender SPA shell (copy-404.mjs must run before build-seo.mjs, ' +
        'never after it)',
    );
  }

  // Which served paths have a declaration of their own to be checked against, below. Every other
  // sitemap URL is a route that can only legitimately carry zero structured data — see the sitemap
  // loop's own stray-JSON-LD check.
  const staticRequestPaths = new Set(staticRoutes.map((route) => route.requestPath));

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
    }

    for (const { requestPath, expectedCanonical, jsonLd, redirectTarget } of staticRoutes) {
      const response = await fetch(`${origin}${requestPath}`, { redirect: 'manual' });
      if (response.status !== 200) {
        throw new Error(
          `verify-seo: configured route ${requestPath} did not return 200 with no redirect (got ${response.status})`,
        );
      }
      const html = await response.text();

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

      if (redirectTarget !== undefined) {
        // A redirect stub is held to the opposite standard of a page: it must forward, say it is
        // not for indexing, and carry NO content — the duplicate-content defect this replaced was
        // precisely a second full copy of the destination page living at this address.
        const refresh = extractTag(html, /<meta http-equiv="refresh" content="0; url=([^"]+)"/);
        if (refresh !== redirectTarget) {
          throw new Error(
            `verify-seo: ${requestPath} — expected a meta refresh to "${redirectTarget}", got ${JSON.stringify(refresh)}`,
          );
        }
        const robots = extractTag(html, /<meta name="robots" content="([^"]*)"/);
        if (robots === null || !/\bnoindex\b/i.test(robots)) {
          throw new Error(
            `verify-seo: ${requestPath} — a redirect stub must be noindex, got ${JSON.stringify(robots)}; a canonical ` +
              'alone is a hint, and two indexable copies of one page is what this address used to be',
          );
        }
        const stubH1Count = h1Count(html);
        if (stubH1Count !== 0) {
          throw new Error(
            `verify-seo: ${requestPath} — a redirect stub must carry no <h1> at all, found ${stubH1Count}; content ` +
              'here means the destination page has been duplicated at this address again',
          );
        }
        continue;
      }

      const count = h1Count(html);
      if (count !== 1) {
        throw new Error(`verify-seo: ${requestPath} — expected exactly one <h1> in the raw served HTML, found ${count}`);
      }
      const h1Text = extractTag(html, /<h1[^>]*>([\s\S]*?)<\/h1>/);
      if (!h1Text || !h1Text.replace(/<[^>]+>/g, '').trim()) {
        throw new Error(`verify-seo: ${requestPath} — the raw <h1> has no real visible text content`);
      }
    }
  } finally {
    await new Promise((resolvePromise) => server.close(resolvePromise));
  }

  // Scoped to exactly what was checked. The JSON-LD absence claim covers every sitemap URL and
  // every configured static route — between them, every shell build-seo.mjs writes — but NOT
  // dist/404.html, which is neither, and is gated separately above (empty pre-prerender shell).
  // Keep this sentence and the checks above in step: a claim of absence is only worth as much as
  // the set it was actually evaluated over.
  const redirectRouteCount = staticRoutes.filter((route) => route.redirectTarget !== undefined).length;
  console.log(
    `[verify-seo] verified ${entries.length} sitemap URL(s) (200, no redirect, self-canonical, exactly one real <h1>) ` +
      `and ${staticRoutes.length - redirectRouteCount} configured page route(s) plus ${redirectRouteCount} redirect ` +
      `stub(s) (each forwarding to its declared destination, noindex, and carrying no <h1> of its own) ` +
      `(200, no redirect, exactly one real <h1>, expected canonical, ` +
      `declared JSON-LD present with the shared script id and matching content, every same-origin asset it names served ` +
      `200) — with no JSON-LD on any sitemap URL or configured route that declares none — all clean`,
  );
}

if (process.argv[1]?.endsWith('verify-seo.mjs')) {
  verifySeo().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
