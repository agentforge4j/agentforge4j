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

/** One node of a schema.org JSON-LD graph — every node needs its own `@type`; everything else is
 * type-specific and left open. */
export interface JsonLdGraphNode {
  readonly '@type': string;
  readonly [key: string]: unknown;
}

/** A route's structured-data block (scripts/build-seo.mjs's `injectJsonLd` / this module's own
 * `usePageSeo`), either a single typed node (`@type`) or a `@graph` of several related nodes
 * (today's home-page config: WebSite + Organization + SoftwareSourceCode) — never an empty or
 * untyped object, which build-seo.mjs's own `assertValidJsonLd` rejects at build time. */
export interface JsonLd {
  readonly '@context': string;
  readonly '@type'?: string;
  readonly '@graph'?: readonly JsonLdGraphNode[];
  readonly [key: string]: unknown;
}

export interface SeoRouteEntry {
  readonly path: string;
  readonly title: string;
  readonly description: string;
  /** Present only when this route's canonical URL is a different, real route (e.g. an
   * intentional alias serving identical content) — never a fabricated destination. */
  readonly canonicalPath?: string;
  /**
   * Present only when this path is not a page at all but a permanent forward to one — the value is
   * the real route it forwards to.
   *
   * `/contributing` is the one such entry today. It used to be a full second copy of `/community`:
   * the same component, the same prerendered body, the same visible content at a second address,
   * with only a `canonicalPath` asking search engines to please disregard one of them. A canonical
   * is a hint, and it was the only thing standing between the site and two indexable pages with
   * identical content and conflicting titles. Forwarding removes the duplicate rather than
   * annotating it, while keeping the address alive for inbound links that already exist — which is
   * the whole reason it cannot simply be deleted.
   *
   * A redirect route has no content of its own, so it is never prerendered, never carries an `<h1>`,
   * never appears in the sitemap, and is served as a `noindex` stub whose canonical names the
   * destination.
   */
  readonly redirectTo?: string;
  /** `false` excludes this route from the sitemap (e.g. a non-canonical alias). Defaults to
   * included. */
  readonly sitemap?: boolean;
  /** Structured data for this route (today: only "/"). Absent for every other route — never an
   * empty placeholder. */
  readonly jsonLd?: JsonLd;
}

/**
 * The metadata an address that matches no route gets — deliberately its own entry rather than a
 * fall back to the home page's.
 *
 * Falling back to home was the audited defect. An unknown URL served the home page's title,
 * description, canonical AND its WebSite+Organization+SoftwareSourceCode structured data, which
 * says three untrue things at once: that a mistyped address is the home page, that the home page's
 * identity claims apply to it, and — through the canonical — that a URL which does not exist is a
 * legitimate alternate address for one that does. The HTTP 404 status was the only thing keeping
 * that out of an index, and `/404.html` itself is served at 200, where no status protects anything.
 *
 * `canonicalPath` is absent by design, and its absence is meaningful: a not-found page gets NO
 * canonical link at all. A canonical pointing at the home page would ask a crawler to consolidate a
 * nonexistent URL into a real one (a textbook soft-404 signal), and a self-referential one would
 * assert that this arbitrary address is a real, canonical page of the site. Neither is true; saying
 * nothing is.
 */
export interface NotFoundSeo {
  readonly title: string;
  readonly description: string;
  /** The `robots` directive a not-found page carries. `follow` is deliberate: the page is not
   * indexable, but the site navigation on it is still worth crawling. */
  readonly robots: string;
}

export const SITE_URL: string = seoData.siteUrl;
export const SEO_ROUTES: readonly SeoRouteEntry[] = seoData.routes;
export const NOT_FOUND_SEO: NotFoundSeo = seoData.notFound;

/** Every route that is a permanent forward rather than a page — see `redirectTo`. App.tsx renders
 * one `<Navigate replace>` per entry, so the router and the static shells agree about which paths
 * are redirects without either side keeping its own list. */
export const REDIRECT_ROUTES: readonly (SeoRouteEntry & { readonly redirectTo: string })[] = SEO_ROUTES.filter(
  (entry): entry is SeoRouteEntry & { readonly redirectTo: string } => entry.redirectTo !== undefined,
);

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

/** The absolute HTTPS canonical URL for a site-relative path (`/`, `/api`, ...) — always in the
 * trailing-slash form, matching scripts/build-seo.mjs's own `withTrailingSlash` exactly: every
 * generated route shell is a directory (`dist/<path>/index.html`), which GitHub Pages only serves
 * without a redirect at its trailing-slash address. Client-side navigation (usePageSeo) must keep
 * emitting the same form the static shell already declared, or a SPA route transition would
 * silently rewrite `<link rel="canonical">` back to the redirecting non-slash form. */
export function canonicalUrl(path: string): string {
  return path === '/' ? `${SITE_URL}/` : `${SITE_URL}${path.replace(/\/+$/, '')}/`;
}
