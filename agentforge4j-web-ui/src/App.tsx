// SPDX-License-Identifier: Apache-2.0
import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate, useMatch } from 'react-router-dom';
import { REDIRECT_ROUTES } from '@/config/seo';
import SiteHeader from '@/components/SiteHeader';
import SiteFooter from '@/components/SiteFooter';
import CatalogueDetailPage from '@/pages/CatalogueDetailPage';
import NotFoundPage from '@/pages/NotFoundPage';
import { BUILDER_COPY } from '@/copy/builder';
import { usePageSeo } from '@/lib/usePageSeo';
import {
  CATALOGUE_DETAIL_ROUTE_PATH,
  CATCH_ALL_ROUTE_PATH,
  LAZY_LOADED_ROUTE_PATH,
  STATIC_ROUTES,
} from '@/config/appRoutes';

// The builder embed pulls in the workflow-builder package + its own graph/zip dependencies —
// by far the heaviest route in the bundle. Loaded on demand so every other route (the vast
// majority of first-time visits) never pays for it.
const BuilderPage = lazy(() => import('@/pages/BuilderPage'));

export default function App() {
  // The embedded builder sizes itself with `height: 100%`, which only resolves if every
  // ancestor up to the viewport has a definite height — `min-h-dvh` + `flex-1` alone leave
  // <main>'s height indefinite, collapsing the builder to its own min-height. On /builder the
  // shell therefore pins the page to exactly one viewport (h-dvh + min-h-0) and drops the
  // marketing footer, so the editor gets the full height below the header while editing.
  // useMatch (not a pathname string compare) shares the <Route path={LAZY_LOADED_ROUTE_PATH}>
  // matcher's semantics, so trailing-slash and case variants that render the builder get its shell too.
  const isBuilderRoute = useMatch(LAZY_LOADED_ROUTE_PATH) !== null;
  usePageSeo();
  return (
    <div className={['flex flex-col bg-bg text-fg', isBuilderRoute ? 'h-dvh' : 'min-h-dvh'].join(' ')}>
      <a href="#main-content" className="skip-link">Skip to content</a>
      <SiteHeader />
      {/* tabIndex={-1}: not a normal Tab stop, but programmatically focusable so activating the
          skip link above actually moves keyboard focus here (a plain <main> without a tabIndex
          only scrolls to the fragment, it never receives focus). */}
      <main
        id="main-content"
        tabIndex={-1}
        className={['flex-1', isBuilderRoute ? 'min-h-0' : ''].filter(Boolean).join(' ')}
      >
        <Routes>
          {STATIC_ROUTES.map(({ path, Component }) => (
            <Route key={path} path={path} element={<Component />} />
          ))}
          {/* Permanent forwards, driven by the same seo-routes.json entries the static redirect
              stubs are generated from (see config/seo.ts's REDIRECT_ROUTES). `replace` so the
              redirecting address does not become a history entry the back button returns to.
              A visitor arriving on one of these client-side ends up on the destination, exactly
              like the static stub's meta refresh does for a fresh load. */}
          {REDIRECT_ROUTES.map(({ path, redirectTo }) => (
            <Route key={path} path={path} element={<Navigate to={`${redirectTo}/`} replace />} />
          ))}
          <Route path={CATALOGUE_DETAIL_ROUTE_PATH} element={<CatalogueDetailPage />} />
          <Route
            path={LAZY_LOADED_ROUTE_PATH}
            element={
              <Suspense
                fallback={
                  <p role="status" className="px-6 py-12 text-center text-sm text-fg-muted">
                    {BUILDER_COPY.loadingLabel}
                  </p>
                }
              >
                <BuilderPage />
              </Suspense>
            }
          />
          <Route path={CATCH_ALL_ROUTE_PATH} element={<NotFoundPage />} />
        </Routes>
      </main>
      {!isBuilderRoute && <SiteFooter />}
    </div>
  );
}
