// SPDX-License-Identifier: Apache-2.0
import { Routes, Route } from 'react-router-dom';
import SiteHeader from '@/components/SiteHeader';
import SiteFooter from '@/components/SiteFooter';
import BuilderPage from '@/pages/BuilderPage';
import NotFoundPage from '@/pages/NotFoundPage';

export default function App() {
  return (
    <div className="flex flex-col h-dvh bg-bg text-fg overflow-hidden">
      <SiteHeader />
      <main id="main-content" className="flex-1 min-h-0">
        <Routes>
          <Route path="/" element={<BuilderPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <SiteFooter />
    </div>
  );
}
