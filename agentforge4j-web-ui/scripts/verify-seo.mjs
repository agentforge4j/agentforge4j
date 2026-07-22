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
import { fileURLToPath } from 'node:url';

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

/** Extracts `{url, lastmod}` per `<url>` block, in document order — same simple regex approach as
 * assemble-site.mjs's own extractSitemapLocs (both fragments are machine-generated, no CDATA/nested
 * namespaces, so a full XML parser buys nothing here). */
function parseSitemap(xml) {
  return [...xml.matchAll(/<url>\s*<loc>([^<]+)<\/loc>(?:\s*<lastmod>([^<]+)<\/lastmod>)?\s*<\/url>/g)].map(
    ([, url, lastmod]) => ({ url, lastmod: lastmod ?? null }),
  );
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
    expectedCanonical: `${siteUrl}${withTrailingSlash(route.canonicalPath ?? route.path)}`,
  }));
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
  const urls = entries.map((entry) => entry.url);
  if (new Set(urls).size !== urls.length) {
    throw new Error('verify-seo: duplicate URL(s) found in the real dist/sitemap.xml');
  }

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

    for (const { url, lastmod } of entries) {
      if (!url.startsWith(`${SITE_ORIGIN}/`)) {
        throw new Error(`verify-seo: sitemap URL outside ${SITE_ORIGIN}: ${url}`);
      }
      const path = url.slice(SITE_ORIGIN.length);
      const response = await fetch(`${origin}${path}`, { redirect: 'manual' });
      if (response.status !== 200) {
        throw new Error(`verify-seo: sitemap URL ${url} did not return 200 with no redirect (got ${response.status})`);
      }
      const html = await response.text();

      const canonical = extractTag(html, /<link rel="canonical" href="([^"]+)"/);
      if (canonical !== url) {
        throw new Error(`verify-seo: ${url} — canonical tag ("${canonical}") does not match its own sitemap URL exactly`);
      }

      const count = h1Count(html);
      if (count !== 1) {
        throw new Error(`verify-seo: ${url} — expected exactly one <h1> in the raw served HTML, found ${count}`);
      }

      if (lastmod !== null && !/^\d{4}-\d{2}-\d{2}$/.test(lastmod)) {
        throw new Error(`verify-seo: ${url} — <lastmod>${lastmod}</lastmod> is not a valid W3C date (YYYY-MM-DD)`);
      }
    }

    for (const { requestPath, expectedCanonical } of staticRoutes) {
      const response = await fetch(`${origin}${requestPath}`, { redirect: 'manual' });
      if (response.status !== 200) {
        throw new Error(
          `verify-seo: configured route ${requestPath} did not return 200 with no redirect (got ${response.status})`,
        );
      }
      const html = await response.text();

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
    }
  } finally {
    await new Promise((resolvePromise) => server.close(resolvePromise));
  }

  console.log(
    `[verify-seo] verified ${entries.length} sitemap URL(s) (200, no redirect, self-canonical, exactly one real <h1>) ` +
      `and ${staticRoutes.length} configured static route(s) (200, no redirect, exactly one real <h1>, expected canonical) — all clean`,
  );
}

if (process.argv[1]?.endsWith('verify-seo.mjs')) {
  verifySeo().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
