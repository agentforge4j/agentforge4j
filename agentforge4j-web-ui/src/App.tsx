// SPDX-License-Identifier: Apache-2.0
import { Routes, Route } from 'react-router-dom';
import SiteHeader from '@/components/SiteHeader';
import SiteFooter from '@/components/SiteFooter';
import BuilderPage from '@/pages/BuilderPage';
import NotFoundPage from '@/pages/NotFoundPage';

export default function App() {
  return (
    <div className="flex flex-col min-h-screen bg-bg text-fg">
      <SiteHeader />
      <main id="main-content" className="flex-1">
        <Routes>
          <Route path="/" element={<BuilderPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <SiteFooter />
    </div>
  );
}
