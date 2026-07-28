// SPDX-License-Identifier: Apache-2.0
//
// Client-side navigation SEO convergence, checked in a real browser against the real production
// build — the one place the audited defect was actually observable.
//
// Every other SEO gate in this module reads the static HTML a route is SERVED. That is a complete
// account of what a crawler gets on a first request, and it is exactly why the defect survived: the
// per-route shells were each individually correct, so every existing check passed, while a visitor
// who navigated *within* the app carried the first-loaded route's og:title/og:description/og:url/
// twitter:title/twitter:description onto every page after it. Nothing that reads served HTML can
// see that; only running the real bundle and really navigating can.
//
// The oracle is deliberately CONVERGENCE, not a table of expected values: for each route, the head
// state reached by a client-side navigation must equal the head state reached by loading that same
// URL directly. That states the actual requirement ("direct load and client navigation must
// converge to equivalent SEO state") rather than a snapshot of today's copy, so it keeps testing the
// real property when titles change, and it stays correct for the unknown-route case without this
// file having to encode what the 404 policy currently is.
//
// Run as its own build stage after verify-seo.mjs (see package.json), against the same dist/ that
// build actually produced. It has no fixture-based unit test of its own by design: a fixture SPA
// would be a second, simpler app whose navigation behaviour proves nothing about this one, and the
// property under test only exists once the real bundle is running.

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
// Deliberately the GitHub-Pages-emulating server, NOT prerender-routes.mjs's own static server.
// That one answers every path with dist/index.html (correct for its job — at prerender time no
// per-route shell exists yet), which would make every "direct load" here the HOME shell: both sides
// of the comparison below would then start from identical markup and converge no matter what the
// build produced. This was not hypothetical — the first version of this file used that server and
// passed with the client-side sync deliberately removed. The emulator serves the real per-route
// shell each URL actually gets, and dist/404.html under a real 404 for an unknown path.
import { startGhPagesEmulatingServer } from './verify-seo.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const DIST_DIR = join(MODULE_ROOT, 'dist');
const SEO_ROUTES_PATH = join(MODULE_ROOT, 'src', 'config', 'seo-routes.json');
const CATALOGUE_DATA_PATH = join(MODULE_ROOT, 'src', 'generated', 'catalogue-data.json');

// A path no route and no catalogue id can ever match, so it always lands on the catch-all
// NotFoundPage — the unknown-route case, exercised through both entry paths like every other route.
const UNKNOWN_ROUTE_PATH = '/definitely-not-a-real-route-cf1d9d/';

/** The head state this check compares. Read from the live DOM after the bundle has run, so it
 * reflects what a metadata consumer executing JavaScript would actually observe — including every
 * tag `usePageSeo` is responsible for, and the count of each so a hook that APPENDS a second
 * og:title instead of updating the first is caught rather than passing on "the first one is right".
 *
 * Runs inside the page — Playwright serializes this function and executes it in the browser, so
 * `document` here is that page's real DOM global, never a Node one (the same arrangement, and the
 * same lint exemption, prerender-routes.mjs already uses for its in-browser predicates). */
/* eslint-disable no-undef */
const readHeadState = () => {
  const metaByKey = (attribute, key) => {
    const tags = [...document.querySelectorAll(`meta[${attribute}="${key}"]`)];
    return { count: tags.length, content: tags.map((tag) => tag.getAttribute('content')) };
  };
  const canonicalLinks = [...document.querySelectorAll('link[rel="canonical"]')];
  const jsonLdScripts = [...document.querySelectorAll('script[type="application/ld+json"]')];
  return {
    title: document.title,
    description: metaByKey('name', 'description'),
    canonical: {
      count: canonicalLinks.length,
      content: canonicalLinks.map((link) => link.getAttribute('href')),
    },
    ogTitle: metaByKey('property', 'og:title'),
    ogDescription: metaByKey('property', 'og:description'),
    ogUrl: metaByKey('property', 'og:url'),
    ogSiteName: metaByKey('property', 'og:site_name'),
    ogImage: metaByKey('property', 'og:image'),
    twitterTitle: metaByKey('name', 'twitter:title'),
    twitterDescription: metaByKey('name', 'twitter:description'),
    twitterCard: metaByKey('name', 'twitter:card'),
    twitterImage: metaByKey('name', 'twitter:image'),
    jsonLd: {
      count: jsonLdScripts.length,
      content: jsonLdScripts.map((script) => script.textContent),
    },
  };
};
/* eslint-enable no-undef */

/** Every route a visitor can reach, read from the same committed config build-seo.mjs and
 * prerender-routes.mjs read — independently, per this module's established convention — plus the
 * unknown-route case. Trailing-slash form throughout: that is the address the host serves directly
 * and the form the site's own links use. */
export function collectNavigablePaths({
  seoRoutesPath = SEO_ROUTES_PATH,
  catalogueDataPath = CATALOGUE_DATA_PATH,
} = {}) {
  const { routes } = JSON.parse(readFileSync(seoRoutesPath, 'utf8'));
  const catalogueData = existsSync(catalogueDataPath)
    ? JSON.parse(readFileSync(catalogueDataPath, 'utf8'))
    : { workflows: [] };
  // Redirect routes are excluded: they are forwards, not destinations. Visiting one lands the
  // browser on its target, so "the head at this path" is not a thing that exists for them — and a
  // convergence check that waited for the location to settle on the redirecting address would hang
  // rather than fail, which is the worst way to learn this. Their behaviour is covered where it
  // belongs: verify-seo.mjs asserts the served stub forwards, is noindex and carries no content,
  // and tests/routeInventory.test.ts asserts each one targets a real indexable route.
  const paths = routes
    .filter((route) => !route.redirectTo)
    .map((route) => (route.path === '/' ? '/' : `${route.path}/`));
  for (const workflow of catalogueData.workflows ?? []) {
    paths.push(`/catalogue/${workflow.id}/`);
  }
  paths.push(UNKNOWN_ROUTE_PATH);
  return paths;
}

/** Waits until the SPA has mounted and `usePageSeo`'s effect has run. `#root h1` is the same
 * mounted-content signal prerender-routes.mjs waits on, and the effect that writes the head runs in
 * the same commit as that render, so a settled DOM here means a settled head. */
async function waitForAppReady(page) {
  await page.waitForSelector('#root h1', { state: 'attached', timeout: 30000 });
  await page.waitForFunction(
    // eslint-disable-next-line no-undef
    () => (document.querySelector('#root h1')?.textContent ?? '').trim().length > 0,
    { timeout: 30000 },
  );
}

/** Navigates within the running app the way a visitor does — a real click on a real rendered link
 * where one exists, falling back to a history push for a route the current page does not link to
 * (the unknown-route case, and any route not in the header/footer). Both are genuine client-side
 * transitions: no document load, so no new shell is ever fetched. */
async function navigateClientSide(page, path) {
  const link = page.locator(`a[href="${path}"]`).first();
  if ((await link.count()) > 0) {
    await link.click();
  } else {
    await page.evaluate(
      // eslint-disable-next-line no-undef
      (target) => window.history.pushState({}, '', target),
      path,
    );
    // A pushState alone does not notify React Router in every version; dispatching popstate is the
    // documented way to make the router re-read location, and it keeps this a client-side
    // transition rather than a reload.
    // eslint-disable-next-line no-undef
    await page.evaluate(() => window.dispatchEvent(new PopStateEvent('popstate')));
  }
  await waitForAppReady(page);
  await page.waitForFunction(
    // eslint-disable-next-line no-undef
    (target) => window.location.pathname === target,
    path,
    { timeout: 30000 },
  );
}

function differences(expected, actual) {
  const problems = [];
  for (const key of Object.keys(expected)) {
    const left = JSON.stringify(expected[key]);
    const right = JSON.stringify(actual[key]);
    if (left !== right) {
      problems.push(`${key}: direct load ${left} vs client navigation ${right}`);
    }
  }
  return problems;
}

/**
 * @param {{distDir?: string, paths?: string[]}} [options]
 * @returns {Promise<{routesChecked: number, transitionsChecked: number}>}
 */
export async function verifyClientNavSeo({ distDir = DIST_DIR, paths } = {}) {
  if (!existsSync(join(distDir, 'index.html'))) {
    throw new Error(`verify-client-nav-seo: ${distDir}/index.html does not exist — run the full build first`);
  }
  const routePaths = paths ?? collectNavigablePaths();
  if (routePaths.length < 2) {
    throw new Error('verify-client-nav-seo: need at least two routes to check a navigation between them');
  }

  const server = await startGhPagesEmulatingServer(distDir);
  const { port } = server.address();
  const origin = `http://127.0.0.1:${port}`;

  let browser;
  let transitionsChecked = 0;
  try {
    browser = await chromium.launch();
    const page = await browser.newPage();

    // 1. The reference: what each route's head looks like when that URL is loaded directly. This is
    //    the state a crawler gets, and the state client navigation must reproduce.
    const direct = new Map();
    for (const path of routePaths) {
      await page.goto(`${origin}${path}`, { waitUntil: 'domcontentloaded' });
      await waitForAppReady(page);
      direct.set(path, await page.evaluate(readHeadState));
    }

    // 2. Every ordered pair of consecutive routes, both directions (A -> B and B -> A), so a hook
    //    that only ever writes on the way "forward" — or one that leaves a residue the next route
    //    happens to overwrite — cannot pass. Starting each pair from a fresh load of A keeps the
    //    two directions independent rather than one long chain whose later steps are only reached
    //    if the earlier ones were right.
    for (let index = 0; index + 1 < routePaths.length; index += 1) {
      const [from, to] = [routePaths[index], routePaths[index + 1]];
      for (const [start, end] of [
        [from, to],
        [to, from],
      ]) {
        await page.goto(`${origin}${start}`, { waitUntil: 'domcontentloaded' });
        await waitForAppReady(page);
        await navigateClientSide(page, end);
        const problems = differences(direct.get(end), await page.evaluate(readHeadState));
        if (problems.length > 0) {
          throw new Error(
            `verify-client-nav-seo: after navigating ${start} -> ${end} in the app, the page's SEO state does not ` +
              `match a direct load of ${end}:\n  ${problems.join('\n  ')}`,
          );
        }
        transitionsChecked += 1;
      }
    }

    // 3. Repeated navigation around a cycle. Catches the failure a single hop cannot: tags that
    //    ACCUMULATE rather than update (a second og:title appended on each visit reads as correct
    //    to anything that looks at only the first one) and any state that only goes wrong on a
    //    route's second visit.
    const cycle = routePaths.slice(0, Math.min(4, routePaths.length));
    await page.goto(`${origin}${cycle[0]}`, { waitUntil: 'domcontentloaded' });
    await waitForAppReady(page);
    for (let lap = 0; lap < 3; lap += 1) {
      for (const path of cycle) {
        await navigateClientSide(page, path);
        const problems = differences(direct.get(path), await page.evaluate(readHeadState));
        if (problems.length > 0) {
          throw new Error(
            `verify-client-nav-seo: on lap ${lap + 1} of repeated navigation, ${path} does not match a direct ` +
              `load of itself:\n  ${problems.join('\n  ')}`,
          );
        }
        transitionsChecked += 1;
      }
    }
  } finally {
    try {
      if (browser) {
        await browser.close();
      }
    } finally {
      await new Promise((resolvePromise) => server.close(resolvePromise));
    }
  }

  return { routesChecked: routePaths.length, transitionsChecked };
}

if (process.argv[1]?.endsWith('verify-client-nav-seo.mjs')) {
  verifyClientNavSeo()
    .then(({ routesChecked, transitionsChecked }) => {
      console.log(
        `[verify-client-nav-seo] ${routesChecked} route(s) incl. an unknown route, ${transitionsChecked} real ` +
          'in-app navigation(s) checked in headless Chromium — every one reaches the same title, description, ' +
          'canonical, og:*, twitter:* and JSON-LD state (and the same tag COUNTS) as a direct load of that URL',
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exit(1);
    });
}
