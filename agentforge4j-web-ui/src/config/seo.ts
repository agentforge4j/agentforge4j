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

const BY_PATH = new Map<string, SeoRouteEntry>(SEO_ROUTES.map((entry) => [entry.path, entry]));

/** The static SEO entry for an exact route path, or `undefined` for dynamic/unknown routes. */
export function findSeoRoute(path: string): SeoRouteEntry | undefined {
  return BY_PATH.get(path);
}

/** The absolute HTTPS canonical URL for a site-relative path (`/`, `/api`, ...). */
export function canonicalUrl(path: string): string {
  return path === '/' ? `${SITE_URL}/` : `${SITE_URL}${path}`;
}
