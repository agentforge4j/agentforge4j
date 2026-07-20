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
import { SEO_ROUTES, type SeoRouteEntry } from '@/config/seo';

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
    const expectedSet = new Set(EXPECTED_INDEXABLE_ROUTE_PATHS);
    const stale = SEO_ROUTES.map((entry) => entry.path).filter((path) => !expectedSet.has(path));
    expect(stale, `seo-routes.json entry/entries with no matching rendered route: ${stale.join(', ')}`).toEqual([]);
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

  test('two seo-routes.json entries never share a canonical unless the sharing is an explicit, declared alias', () => {
    const byCanonical = new Map<string, SeoRouteEntry[]>();
    for (const entry of SEO_ROUTES) {
      const canonical = entry.canonicalPath ?? entry.path;
      byCanonical.set(canonical, [...(byCanonical.get(canonical) ?? []), entry]);
    }
    for (const [canonical, entries] of byCanonical) {
      if (entries.length === 1) continue;
      // Exactly one entry may be the canonical's own "owner" (its path IS that canonical, with no
      // canonicalPath of its own); every other entry sharing it must explicitly declare
      // canonicalPath === canonical — proving the collision was deliberately authored, not two
      // routes that coincidentally ended up pointing at the same address.
      const owners = entries.filter((entry) => entry.path === canonical && entry.canonicalPath === undefined);
      const declaredAliases = entries.filter((entry) => entry.canonicalPath === canonical);
      expect(
        owners.length,
        `canonical "${canonical}" must have exactly one non-aliasing owner entry; found ${owners.length}`,
      ).toBe(1);
      expect(
        declaredAliases.length,
        `canonical "${canonical}" is shared by ${entries.length} entries but only ${declaredAliases.length} explicitly declare canonicalPath === "${canonical}"`,
      ).toBe(entries.length - 1);
    }
  });

  test('every canonicalPath alias target is itself a real seo-routes.json entry, never a fabricated destination', () => {
    const paths = new Set(SEO_ROUTES.map((entry) => entry.path));
    const badTargets = SEO_ROUTES.filter(
      (entry) => entry.canonicalPath !== undefined && !paths.has(entry.canonicalPath),
    );
    expect(
      badTargets,
      `canonicalPath alias target(s) with no matching real entry: ${JSON.stringify(badTargets)}`,
    ).toEqual([]);
  });
});
