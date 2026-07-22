// SPDX-License-Identifier: Apache-2.0
//
// Build-time route prerendering. This is a pure client-rendered SPA (main.tsx uses
// `createRoot(...).render(...)`, never `hydrateRoot`) — so the initial static response for every
// route was a bare `<div id="root"></div>`, with all real content, including the page's H1,
// appearing only after the JS bundle executes. Search-engine crawlers that do not (or do not yet)
// run that render pass see no content at all, which is the confirmed root cause of Bing reporting
// "H1 tag missing" (see docs-content-checks below and the audit this pass fixes).
//
// This script boots the real built bundle in a real headless browser, one full navigation per
// route (matching exactly how a fresh crawler visit — or a real user — arrives at that URL: through
// `window.location.pathname` on first load, not a client-side transition), waits for that route's
// real content to mount, and captures `#root`'s serialized HTML. build-seo.mjs then splices this
// verbatim into that route's own static shell in place of the empty mount point — the exact same
// markup React would produce on the client, so the later `createRoot(...).render()` call replaces
// visually-identical nodes (no flash) and never invokes React's hydration path at all (no hydration
// mismatch is possible, because hydration is simply never used here).
//
// Deterministic by construction: every route's content is pure client-side data already fixed at
// build time (page copy, seo-routes.json, catalogue-data.json) — no timestamps, no randomness, no
// network calls — so a snapshot taken now and one taken from an identical commit are byte-identical.
//
// Run as part of `node scripts/build-seo.mjs` (its CLI entry awaits this before writing shells) —
// kept as its own module, not inlined into build-seo.mjs, so buildSeo() itself stays a synchronous,
// browser-free pure function its existing fixture-based unit tests exercise with no headless
// browser dependency at all.

import { createServer } from 'node:http';
import { createReadStream, existsSync, readFileSync } from 'node:fs';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');

const DIST_DIR = join(MODULE_ROOT, 'dist');
const SEO_ROUTES_PATH = join(MODULE_ROOT, 'src', 'config', 'seo-routes.json');
const CATALOGUE_DATA_PATH = join(MODULE_ROOT, 'src', 'generated', 'catalogue-data.json');

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
  '.webmanifest': 'application/manifest+json',
};

/**
 * The same route inventory build-seo.mjs reads, independently — this script runs as its own build
 * stage before any shell exists, so it cannot read per-route data back out of dist/ the way
 * build-seo.mjs's head-injection does; both read the same two committed source-of-truth JSON files
 * instead (the established pattern here — see this module's build-seo.mjs header comment on
 * catalogueSeo.ts — rather than one script importing internals of the other).
 */
export function collectRoutePaths({ seoRoutesPath = SEO_ROUTES_PATH, catalogueDataPath = CATALOGUE_DATA_PATH } = {}) {
  const { routes } = JSON.parse(readFileSync(seoRoutesPath, 'utf8'));
  const catalogueData = existsSync(catalogueDataPath)
    ? JSON.parse(readFileSync(catalogueDataPath, 'utf8'))
    : { workflows: [] };
  const paths = routes.map((route) => route.path);
  for (const workflow of catalogueData.workflows ?? []) {
    paths.push(`/catalogue/${workflow.id}`);
  }
  return paths;
}

/** A minimal static file server with SPA fallback: any request path with no matching real file
 * under `distDir` serves `dist/index.html` instead of 404ing — the standard prerender technique,
 * since real per-route shells (build-seo.mjs's own output) do not exist yet at this build stage.
 * React Router reads `window.location.pathname` from the browser itself once the bundle boots, so
 * serving the one generic entry document for every route is sufficient for each route to mount its
 * real page. */
function startStaticServer(distDir) {
  const indexHtml = readFileSync(join(distDir, 'index.html'));
  const server = createServer((req, res) => {
    const urlPath = decodeURIComponent((req.url ?? '/').split('?')[0]);
    const candidate = join(distDir, urlPath);
    const isRealFile = !urlPath.endsWith('/') && existsSync(candidate) && candidate.startsWith(distDir);
    if (isRealFile) {
      const ext = extname(candidate);
      res.writeHead(200, { 'content-type': MIME_TYPES[ext] ?? 'application/octet-stream' });
      createReadStream(candidate).pipe(res);
      return;
    }
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
    res.end(indexHtml);
  });
  return new Promise((resolvePromise) => {
    server.listen(0, '127.0.0.1', () => resolvePromise(server));
  });
}

// Neutralizes every source of randomness a page could reach for during the prerender capture only
// (never shipped — this only runs inside the throwaway build-time browser context; a real user's
// browser keeps the real, genuinely random crypto/Math.random). Found necessary in practice: the
// /builder route's default empty-canvas starter node id is generated via nanoid's browser build,
// which calls crypto.getRandomValues — capturing it without this override produced a different
// `data-id`/`data-testid` suffix on every single prerender, the exact class of defect this pass
// must not ship (proven by two consecutive real builds from the same commit differing only in that
// one route). A seeded, deterministic PRNG (mulberry32) drives both Math.random and
// crypto.getRandomValues so the same page always produces the same output for a given navigation.
const DETERMINISTIC_RNG_INIT_SCRIPT = `
(() => {
  function mulberry32(seed) {
    let a = seed;
    return function next() {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  const next = mulberry32(0x5eed5eed);
  Math.random = () => next();
  if (window.crypto) {
    window.crypto.getRandomValues = (arr) => {
      for (let i = 0; i < arr.length; i += 1) {
        arr[i] = Math.floor(next() * 256);
      }
      return arr;
    };
  }
})();
`;

/** Waits for the route's real page content to mount (a non-empty `<h1>` inside `#root` — every
 * real page component renders exactly one, including /builder's screen-reader-only heading; the
 * Suspense loading fallback on /builder renders no `<h1>` at all, so this also correctly waits past
 * it rather than snapshotting the fallback), then lets in-flight async chunk loads settle. */
async function waitForRouteContent(page) {
  await page.waitForSelector('#root h1', { state: 'attached', timeout: 30000 });
  await page.waitForFunction(
    // Evaluated in-browser by Playwright (serialized and run inside the page, never in Node) —
    // `document` is that page's real DOM global, not a Node one.
    // eslint-disable-next-line no-undef
    () => (document.querySelector('#root h1')?.textContent ?? '').trim().length > 0,
    { timeout: 30000 },
  );
  await page.waitForLoadState('networkidle', { timeout: 30000 });
}

/**
 * Boots the real built bundle in headless Chromium and captures each route's fully client-rendered
 * `#root` markup.
 *
 * @param {{distDir?: string, routePaths?: string[]}} [options]
 * @returns {Promise<Record<string, string>>} routePath -> serialized #root innerHTML
 */
export async function prerenderRoutes({ distDir = DIST_DIR, routePaths } = {}) {
  const indexPath = join(distDir, 'index.html');
  if (!existsSync(indexPath)) {
    throw new Error(`prerender-routes: ${indexPath} does not exist — run "vite build" first`);
  }
  const paths = routePaths ?? collectRoutePaths();

  const server = await startStaticServer(distDir);
  const { port } = server.address();
  const origin = `http://127.0.0.1:${port}`;

  const browser = await chromium.launch();
  const snapshots = {};
  try {
    const page = await browser.newPage();
    await page.addInitScript(DETERMINISTIC_RNG_INIT_SCRIPT);
    for (const routePath of paths) {
      // Captured twice, via two independent fresh navigations (not a re-render of the same load) —
      // the same real-world check two separate crawler visits would get. Any difference is a build
      // failure, not a warning: nondeterministic output must never silently ship in the composed
      // shell (see this module's header comment for the real defect this caught).
      await page.goto(`${origin}${routePath}`, { waitUntil: 'domcontentloaded' });
      await waitForRouteContent(page);
      const first = await page.locator('#root').innerHTML();

      await page.goto(`${origin}${routePath}`, { waitUntil: 'domcontentloaded' });
      await waitForRouteContent(page);
      const second = await page.locator('#root').innerHTML();

      if (first !== second) {
        throw new Error(
          `prerender-routes: ${routePath} produced different markup across two consecutive captures ` +
            '(nondeterministic prerender — a build must never ship this). Inspect the component tree ' +
            'for a source of randomness or time-dependence not covered by the deterministic RNG seeding.',
        );
      }
      snapshots[routePath] = first;
    }
  } finally {
    await browser.close();
    await new Promise((resolvePromise) => server.close(resolvePromise));
  }
  return snapshots;
}
