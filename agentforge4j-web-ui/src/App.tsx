// SPDX-License-Identifier: Apache-2.0
import { lazy, Suspense } from 'react';
import { Routes, Route, useMatch } from 'react-router-dom';
import SiteHeader from '@/components/SiteHeader';
import SiteFooter from '@/components/SiteFooter';
import HomePage from '@/pages/HomePage';
import ApiPage from '@/pages/ApiPage';
import UsePage from '@/pages/UsePage';
import CataloguePage from '@/pages/CataloguePage';
import CatalogueDetailPage from '@/pages/CatalogueDetailPage';
import ArchitecturePage from '@/pages/ArchitecturePage';
import ReleasesPage from '@/pages/ReleasesPage';
import CommunityPage from '@/pages/CommunityPage';
import SecurityPage from '@/pages/SecurityPage';
import LegalPage from '@/pages/LegalPage';
import ContactPage from '@/pages/ContactPage';
import NotFoundPage from '@/pages/NotFoundPage';
import { BUILDER_COPY } from '@/copy/builder';

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
  // useMatch (not a pathname string compare) shares the <Route path="/builder"> matcher's
  // semantics, so trailing-slash and case variants that render the builder get its shell too.
  const isBuilderRoute = useMatch('/builder') !== null;
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
          <Route path="/" element={<HomePage />} />
          <Route path="/api" element={<ApiPage />} />
          <Route path="/use" element={<UsePage />} />
          <Route path="/catalogue" element={<CataloguePage />} />
          <Route path="/catalogue/:id" element={<CatalogueDetailPage />} />
          <Route
            path="/builder"
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
          <Route path="/architecture" element={<ArchitecturePage />} />
          <Route path="/releases" element={<ReleasesPage />} />
          <Route path="/community" element={<CommunityPage />} />
          <Route path="/contributing" element={<CommunityPage />} />
          <Route path="/security" element={<SecurityPage />} />
          <Route path="/legal" element={<LegalPage />} />
          <Route path="/contact" element={<ContactPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      {!isBuilderRoute && <SiteFooter />}
    </div>
  );
}
