// SPDX-License-Identifier: Apache-2.0
//
// Deterministic drift gate between the React routes App.tsx actually renders (src/config/appRoutes.ts
// — the same array/constants App.tsx's <Routes> renders from, not a hand-maintained shadow copy)
// and the static SEO metadata inventory (src/config/seo-routes.json). A route added to one and
// forgotten in the other fails here instead of silently shipping with the wrong (or missing) title/
// description/canonical.

import { describe, expect, test } from 'vitest';
import {
  CATALOGUE_DETAIL_ROUTE_PATH,
  CATCH_ALL_ROUTE_PATH,
  LAZY_LOADED_ROUTE_PATH,
  NON_INDEXABLE_STATIC_ROUTE_PATHS,
  STATIC_ROUTES,
} from '@/config/appRoutes';
import { REDIRECT_ROUTES, SEO_ROUTES, withTrailingSlash, type SeoRouteEntry } from '@/config/seo';

/** Lowercase + strip a trailing slash (except for "/" itself) — the same normalization
 * seo.ts's own route-matching uses, so "does this path collide with that one" answers the same
 * question the app's real lookup would. */
function normalize(path: string): string {
  const lower = path.toLowerCase();
  return lower.length > 1 && lower.endsWith('/') ? lower.slice(0, -1) : lower;
}

const EXPECTED_INDEXABLE_ROUTE_PATHS: readonly string[] = [
  ...STATIC_ROUTES.map((route) => route.path),
  LAZY_LOADED_ROUTE_PATH,
].filter((path) => !NON_INDEXABLE_STATIC_ROUTE_PATHS.includes(path));

/** Redirect routes are rendered by App.tsx (one `<Navigate replace>` each, from the same
 * REDIRECT_ROUTES list) but are not pages: no content, no sitemap entry, nothing to index. They
 * belong in the "is this entry stale?" set below and nowhere near the indexable one. */
const REDIRECT_ROUTE_PATHS: readonly string[] = REDIRECT_ROUTES.map((entry) => entry.path);

const EXPECTED_RENDERED_ROUTE_PATHS: readonly string[] = [
  ...EXPECTED_INDEXABLE_ROUTE_PATHS,
  ...REDIRECT_ROUTE_PATHS,
];

describe('route / SEO inventory drift gate', () => {
  test('the two documented dynamic/catch-all exceptions are not plain static routes', () => {
    // Sanity-check the exceptions themselves are real, distinct route shapes, not accidentally
    // equal to an ordinary path (which would silently defeat the exclusion below).
    expect(CATALOGUE_DETAIL_ROUTE_PATH).toBe('/catalogue/:id');
    expect(CATCH_ALL_ROUTE_PATH).toBe('*');
    expect(EXPECTED_INDEXABLE_ROUTE_PATHS).not.toContain(CATALOGUE_DETAIL_ROUTE_PATH);
    expect(EXPECTED_INDEXABLE_ROUTE_PATHS).not.toContain(CATCH_ALL_ROUTE_PATH);
  });

  test('neither dynamic/catch-all exception has been given its own seo-routes.json entry by mistake', () => {
    const seoPaths = SEO_ROUTES.map((entry) => entry.path);
    expect(seoPaths).not.toContain(CATALOGUE_DETAIL_ROUTE_PATH);
    expect(seoPaths).not.toContain(CATCH_ALL_ROUTE_PATH);
  });

  test('every indexable route App.tsx renders has exactly one seo-routes.json entry (fails on a missing SEO entry)', () => {
    const seoPathCounts = new Map<string, number>();
    for (const entry of SEO_ROUTES) {
      seoPathCounts.set(entry.path, (seoPathCounts.get(entry.path) ?? 0) + 1);
    }
    const missing: string[] = [];
    const duplicated: string[] = [];
    for (const path of EXPECTED_INDEXABLE_ROUTE_PATHS) {
      const count = seoPathCounts.get(path) ?? 0;
      if (count === 0) missing.push(path);
      if (count > 1) duplicated.push(path);
    }
    expect(missing, `route(s) rendered by App.tsx with no seo-routes.json entry: ${missing.join(', ')}`).toEqual([]);
    expect(
      duplicated,
      `route(s) with more than one seo-routes.json entry: ${duplicated.join(', ')}`,
    ).toEqual([]);
  });

  test('every seo-routes.json entry corresponds to a route App.tsx actually renders (fails on a stale SEO entry)', () => {
    const expectedSet = new Set(EXPECTED_RENDERED_ROUTE_PATHS);
    const stale = SEO_ROUTES.map((entry) => entry.path).filter((path) => !expectedSet.has(path));
    expect(stale, `seo-routes.json entry/entries with no matching rendered route: ${stale.join(', ')}`).toEqual([]);
  });

  test('a redirect route is never also an indexable route — one address cannot both forward and be a page', () => {
    for (const path of REDIRECT_ROUTE_PATHS) {
      expect(EXPECTED_INDEXABLE_ROUTE_PATHS, `${path} is rendered as both a redirect and a page`).not.toContain(path);
    }
  });

  test('every redirect target is a real, indexable route — never a fabricated or itself-redirecting destination', () => {
    const indexable = new Set(EXPECTED_INDEXABLE_ROUTE_PATHS);
    for (const { path, redirectTo } of REDIRECT_ROUTES) {
      expect(indexable, `${path} forwards to "${redirectTo}", which is not a real indexable route`).toContain(redirectTo);
    }
  });

  test('a redirect route is excluded from the sitemap and declares no canonicalPath of its own', () => {
    for (const entry of REDIRECT_ROUTES) {
      expect(entry.sitemap, `${entry.path} is a redirect and must be sitemap: false`).toBe(false);
      // redirectTo already implies the canonical (the destination); a second, independently-written
      // canonicalPath could disagree with it.
      expect(entry.canonicalPath, `${entry.path} declares both redirectTo and canonicalPath`).toBeUndefined();
    }
  });

  test('/contributing is a redirect, not a second copy of /community — the audited duplicate', () => {
    // Named explicitly because this is the finding, not a generic property: the address existed as a
    // full second rendering of the Community page, distinguished only by a canonical hint.
    const contributing = SEO_ROUTES.find((entry) => entry.path === '/contributing');
    expect(contributing?.redirectTo).toBe('/community');
    expect(STATIC_ROUTES.map((route) => route.path)).not.toContain('/contributing');
  });

  test('no two seo-routes.json entries declare the same path after case/trailing-slash normalization', () => {
    const seen = new Map<string, string[]>();
    for (const entry of SEO_ROUTES) {
      const key = normalize(entry.path);
      seen.set(key, [...(seen.get(key) ?? []), entry.path]);
    }
    const collisions = [...seen.entries()].filter(([, paths]) => paths.length > 1);
    expect(collisions, `normalized path collisions: ${JSON.stringify(collisions)}`).toEqual([]);
  });

  test('every route App.tsx renders is likewise unique after case/trailing-slash normalization', () => {
    const seen = new Map<string, string[]>();
    for (const path of EXPECTED_INDEXABLE_ROUTE_PATHS) {
      const key = normalize(path);
      seen.set(key, [...(seen.get(key) ?? []), path]);
    }
    const collisions = [...seen.entries()].filter(([, paths]) => paths.length > 1);
    expect(collisions, `normalized route-path collisions: ${JSON.stringify(collisions)}`).toEqual([]);
  });

  // The two canonicalPath gates below are written as pure functions over an arbitrary entry list,
  // then run twice: once over the real committed config, and once over synthetic entries that are
  // supposed to fail. The second run is not decoration. `canonicalPath` is still a fully supported,
  // implemented feature across seo.ts, build-seo.mjs, verify-seo.mjs and usePageSeo.ts, but no
  // route declares one any more — /contributing was the last, and it is a `redirectTo` stub now. Run
  // over the real config alone, both checks iterate zero candidates and pass without executing a
  // single assertion. This file already states elsewhere that a check which cannot fail is not a
  // check (verify-seo.mjs makes the same point about its internal-link corpus); a silently vacuous
  // gate over a live feature is exactly that.

  /** Entries sharing one canonical, other than by an explicitly declared alias. */
  function undeclaredCanonicalSharing(entries: readonly SeoRouteEntry[]): string[] {
    const byCanonical = new Map<string, SeoRouteEntry[]>();
    for (const entry of entries) {
      const canonical = entry.canonicalPath ?? entry.path;
      byCanonical.set(canonical, [...(byCanonical.get(canonical) ?? []), entry]);
    }
    const problems: string[] = [];
    for (const [canonical, sharing] of byCanonical) {
      if (sharing.length === 1) continue;
      // Exactly one entry may be the canonical's own "owner" (its path IS that canonical, with no
      // canonicalPath of its own); every other entry sharing it must explicitly declare
      // canonicalPath === canonical — proving the collision was deliberately authored, not two
      // routes that coincidentally ended up pointing at the same address.
      const owners = sharing.filter((entry) => entry.path === canonical && entry.canonicalPath === undefined);
      const declaredAliases = sharing.filter((entry) => entry.canonicalPath === canonical);
      if (owners.length !== 1) {
        problems.push(`canonical "${canonical}" must have exactly one non-aliasing owner entry; found ${owners.length}`);
      }
      if (declaredAliases.length !== sharing.length - 1) {
        problems.push(
          `canonical "${canonical}" is shared by ${sharing.length} entries but only ${declaredAliases.length} explicitly declare canonicalPath === "${canonical}"`,
        );
      }
    }
    return problems;
  }

  /** Entries whose canonicalPath names a destination that is not itself an entry. */
  function fabricatedAliasTargets(entries: readonly SeoRouteEntry[]): SeoRouteEntry[] {
    const paths = new Set(entries.map((entry) => entry.path));
    return entries.filter((entry) => entry.canonicalPath !== undefined && !paths.has(entry.canonicalPath));
  }

  const entry = (path: string, canonicalPath?: string): SeoRouteEntry =>
    ({ path, title: `${path} title`, description: `${path} description`, ...(canonicalPath ? { canonicalPath } : {}) }) as SeoRouteEntry;

  test('two seo-routes.json entries never share a canonical unless the sharing is an explicit, declared alias', () => {
    expect(undeclaredCanonicalSharing(SEO_ROUTES)).toEqual([]);
  });

  test('the shared-canonical check actually rejects undeclared sharing (it is not vacuous over a config with no aliases)', () => {
    // Two entries land on the same canonical with neither declaring the alias.
    expect(undeclaredCanonicalSharing([entry('/community'), entry('/contributing', '/community')])).toEqual([]);
    expect(undeclaredCanonicalSharing([entry('/community'), entry('/community')])).not.toEqual([]);
    // An alias whose owner is missing entirely is still a violation.
    expect(undeclaredCanonicalSharing([entry('/a', '/z'), entry('/b', '/z')])).not.toEqual([]);
  });

  test('every canonicalPath alias target is itself a real seo-routes.json entry, never a fabricated destination', () => {
    expect(
      fabricatedAliasTargets(SEO_ROUTES),
      `canonicalPath alias target(s) with no matching real entry: ${JSON.stringify(fabricatedAliasTargets(SEO_ROUTES))}`,
    ).toEqual([]);
  });

  test('the alias-target check actually rejects a fabricated destination (it is not vacuous over a config with no aliases)', () => {
    expect(fabricatedAliasTargets([entry('/community'), entry('/contributing', '/community')])).toEqual([]);
    expect(fabricatedAliasTargets([entry('/contributing', '/nowhere')])).toHaveLength(1);
  });

  test('the router and the build-time shell compute the same destination from one redirectTo declaration', () => {
    // App.tsx renders `<Navigate to={withTrailingSlash(redirectTo)}>` and build-seo.mjs writes
    // `withTrailingSlash(route.redirectTo)` into the stub's meta refresh. Asserted over the whole
    // REDIRECT_ROUTES list rather than the single current entry, and including the two inputs that
    // used to diverge under the old `${redirectTo}/` concatenation.
    for (const { path, redirectTo } of REDIRECT_ROUTES) {
      expect(withTrailingSlash(redirectTo), `${path}'s destination must be a clean root-relative path`).toMatch(
        /^\/(?!\/)/,
      );
      expect(withTrailingSlash(redirectTo)).toBe(withTrailingSlash(withTrailingSlash(redirectTo)));
    }
    expect(withTrailingSlash('/')).toBe('/');
    expect(withTrailingSlash('/community/')).toBe('/community/');
    expect(withTrailingSlash('/community')).toBe('/community/');
  });
});
