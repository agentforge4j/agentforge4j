// SPDX-License-Identifier: Apache-2.0
import { Routes, Route } from 'react-router-dom';
import SiteHeader from '@/components/SiteHeader';
import SiteFooter from '@/components/SiteFooter';
import HomePage from '@/pages/HomePage';
import DocsPage from '@/pages/DocsPage';
import UsePage from '@/pages/UsePage';
import CataloguePage from '@/pages/CataloguePage';
import CatalogueDetailPage from '@/pages/CatalogueDetailPage';
import BuilderPage from '@/pages/BuilderPage';
import ArchitecturePage from '@/pages/ArchitecturePage';
import ReleasesPage from '@/pages/ReleasesPage';
import CommunityPage from '@/pages/CommunityPage';
import SecurityPage from '@/pages/SecurityPage';
import LegalPage from '@/pages/LegalPage';
import ContactPage from '@/pages/ContactPage';
import NotFoundPage from '@/pages/NotFoundPage';

export default function App() {
  return (
    <div className="flex flex-col min-h-dvh bg-bg text-fg">
      <a href="#main-content" className="skip-link">Skip to content</a>
      <SiteHeader />
      <main id="main-content" className="flex-1">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/docs" element={<DocsPage />} />
          <Route path="/use" element={<UsePage />} />
          <Route path="/catalogue" element={<CataloguePage />} />
          <Route path="/catalogue/:id" element={<CatalogueDetailPage />} />
          <Route path="/builder" element={<BuilderPage />} />
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
      <SiteFooter />
    </div>
  );
}
