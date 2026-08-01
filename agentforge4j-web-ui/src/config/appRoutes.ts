// SPDX-License-Identifier: Apache-2.0
//
// The single source of truth for every ordinary (one-path-one-page, plain `<Component />`) static
// SPA route. App.tsx's <Routes> renders one <Route> per entry here via a map — it does not
// hand-write these paths a second time — so this array and what the app actually renders can
// never independently drift apart. tests/routeInventory.test.ts compares the full expected route
// set (this array, plus the two named exceptions below) against seo-routes.json to catch a route
// added to one and forgotten in the other.
//
// Three routes are NOT plain-`<Component />` entries in this array, and are the drift check's
// documented, mechanically-represented exceptions:
//   - /builder (LAZY_LOADED_ROUTE_PATH below) — IS a real, indexable static route with its own
//     seo-routes.json entry, but is rendered via a bespoke Suspense/lazy wrapper in App.tsx (by
//     far the heaviest route in the bundle) rather than a plain `<Component />`, so it cannot join
//     this array's uniform shape. Still included in the drift check's expected-route set.
//   - /catalogue/:id (CATALOGUE_DETAIL_ROUTE_PATH below) — dynamic; checked against real
//     catalogue-data.json (see usePageSeo.test.tsx's cross-consistency test), not seo-routes.json.
//     Excluded from the drift check's expected-route set entirely.
//   - * (CATCH_ALL_ROUTE_PATH below) — the catch-all NotFound route; not a real indexable page.
//     Excluded from the drift check's expected-route set entirely.
import type { ComponentType } from 'react';
import HomePage from '@/pages/HomePage';
import ApiPage from '@/pages/ApiPage';
import UsePage from '@/pages/UsePage';
import CataloguePage from '@/pages/CataloguePage';
import ArchitecturePage from '@/pages/ArchitecturePage';
import ReleasesPage from '@/pages/ReleasesPage';
import CommunityPage from '@/pages/CommunityPage';
import SecurityPage from '@/pages/SecurityPage';
import LegalPage from '@/pages/LegalPage';
import ContactPage from '@/pages/ContactPage';

export interface StaticRouteEntry {
  readonly path: string;
  readonly Component: ComponentType;
}

export const STATIC_ROUTES: readonly StaticRouteEntry[] = [
  { path: '/', Component: HomePage },
  { path: '/api', Component: ApiPage },
  { path: '/use', Component: UsePage },
  { path: '/catalogue', Component: CataloguePage },
  { path: '/architecture', Component: ArchitecturePage },
  { path: '/releases', Component: ReleasesPage },
  { path: '/community', Component: CommunityPage },
  { path: '/contributing', Component: CommunityPage },
  { path: '/security', Component: SecurityPage },
  { path: '/legal', Component: LegalPage },
  { path: '/contact', Component: ContactPage },
];

/** A real, indexable static route (has its own seo-routes.json entry) that App.tsx renders with a
 * bespoke Suspense/lazy wrapper instead of a plain `<Component />`, so it cannot join
 * STATIC_ROUTES's uniform shape — kept as a named export so App.tsx's dedicated <Route> and the
 * drift test's expected-route set share one definition instead of two independent literals. */
export const LAZY_LOADED_ROUTE_PATH = '/builder';

/** The dynamic catalogue-detail route — one real page per shipped workflow id, checked against
 * catalogue-data.json rather than seo-routes.json. A documented, mechanically-represented
 * exception to the static-route drift check, not an oversight. */
export const CATALOGUE_DETAIL_ROUTE_PATH = '/catalogue/:id';

/** The catch-all not-found route — not a real indexable page. A documented,
 * mechanically-represented exception to the static-route drift check, not an oversight. */
export const CATCH_ALL_ROUTE_PATH = '*';

/**
 * Static routes that are deliberately excluded from having their own seo-routes.json entry
 * entirely (not even as a `canonicalPath` alias) — distinct from `/contributing`, which IS present
 * in seo-routes.json but declares itself an alias of `/community`. Empty today: every current
 * static route has its own real or alias entry. A future route added here must carry a comment
 * explaining why it is intentionally non-indexable, mirroring this codebase's other allowlist
 * conventions (e.g. lint-content-gate.mjs's EXTRA_FILES).
 */
export const NON_INDEXABLE_STATIC_ROUTE_PATHS: readonly string[] = [];
