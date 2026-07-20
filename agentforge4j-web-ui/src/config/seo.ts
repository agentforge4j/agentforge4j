// SPDX-License-Identifier: Apache-2.0
//
// Single source of truth for per-route SEO metadata (title/description/canonical), consumed by
// both the client-side title/meta sync (usePageSeo) and the build-time static-shell generator
// (scripts/build-seo.mjs — plain Node ESM, reads the same seo-routes.json directly rather than
// importing this TS module, since there is no bundler step ahead of it).
//
// Dynamic /catalogue/:id pages are not listed here — their title/description are computed from
// the real shipped-workflow data at both build time and render time; see catalogueSeo.ts.

import seoData from './seo-routes.json';

export interface SeoRouteEntry {
  readonly path: string;
  readonly title: string;
  readonly description: string;
  /** Present only when this route's canonical URL is a different, real route (e.g. an
   * intentional alias serving identical content) — never a fabricated destination. */
  readonly canonicalPath?: string;
  /** `false` excludes this route from the sitemap (e.g. a non-canonical alias). Defaults to
   * included. */
  readonly sitemap?: boolean;
}

export const SITE_URL: string = seoData.siteUrl;
export const SEO_ROUTES: readonly SeoRouteEntry[] = seoData.routes;

/** Normalizes a path for *route-matching purposes only* — lowercased, with any trailing slash
 * (other than the root `/` itself) stripped. React Router's own default matching is
 * case-insensitive and tolerates an optional trailing slash, so a lookup here must recognize the
 * same URL variants the router already renders successfully, or a real, correctly-rendered page
 * would silently report the wrong SEO metadata. This only affects which entry is *found* — the
 * value returned is always that entry's own declared, already-clean `path`/`canonicalPath`, never
 * a transformation of the input, so the emitted canonical is unaffected by which variant matched. */
function normalizeForMatch(path: string): string {
  const lower = path.toLowerCase();
  return lower.length > 1 && lower.endsWith('/') ? lower.slice(0, -1) : lower;
}

const BY_PATH = new Map<string, SeoRouteEntry>(SEO_ROUTES.map((entry) => [normalizeForMatch(entry.path), entry]));

/** The static SEO entry for a route path — tolerant of a trailing slash and letter case, matching
 * React Router's own default matching — or `undefined` for dynamic/unknown routes. */
export function findSeoRoute(path: string): SeoRouteEntry | undefined {
  return BY_PATH.get(normalizeForMatch(path));
}

/** The absolute HTTPS canonical URL for a site-relative path (`/`, `/api`, ...). */
export function canonicalUrl(path: string): string {
  return path === '/' ? `${SITE_URL}/` : `${SITE_URL}${path}`;
}
