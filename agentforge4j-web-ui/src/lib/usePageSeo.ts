// SPDX-License-Identifier: Apache-2.0
import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { canonicalUrl, findSeoRoute } from '@/config/seo';
import { catalogueData } from '@/lib/catalogueData';
import { catalogueWorkflowDescription, catalogueWorkflowTitle } from '@/lib/catalogueSeo';

const CATALOGUE_DETAIL_PATH = /^\/catalogue\/([^/]+)$/;

function setMetaDescription(content: string): void {
  let tag = document.querySelector('meta[name="description"]');
  if (!tag) {
    tag = document.createElement('meta');
    tag.setAttribute('name', 'description');
    document.head.appendChild(tag);
  }
  tag.setAttribute('content', content);
}

function setCanonical(href: string): void {
  let link = document.querySelector('link[rel="canonical"]');
  if (!link) {
    link = document.createElement('link');
    link.setAttribute('rel', 'canonical');
    document.head.appendChild(link);
  }
  link.setAttribute('href', href);
}

/**
 * Keeps `document.title`, `<meta name="description">`, and `<link rel="canonical">` in sync
 * with the current client-side route. The build-time static per-route HTML shells
 * (scripts/build-seo.mjs) only cover the *first* request to a given route — any subsequent
 * in-app navigation is client-side only and never re-fetches a shell, so without this the tab
 * title/canonical would silently keep whatever the initially-loaded shell said.
 */
export function usePageSeo(): void {
  const location = useLocation();

  useEffect(() => {
    const path = location.pathname;
    const staticEntry = findSeoRoute(path);
    if (staticEntry) {
      document.title = staticEntry.title;
      setMetaDescription(staticEntry.description);
      setCanonical(canonicalUrl(staticEntry.canonicalPath ?? staticEntry.path));
      return;
    }

    const catalogueMatch = CATALOGUE_DETAIL_PATH.exec(path);
    const workflow = catalogueMatch
      ? catalogueData.workflows.find((entry) => entry.id === catalogueMatch[1])
      : undefined;
    if (workflow) {
      document.title = catalogueWorkflowTitle(workflow);
      setMetaDescription(catalogueWorkflowDescription(workflow));
      setCanonical(canonicalUrl(path));
      return;
    }

    // Unmatched path (NotFoundPage) or any other route with no SEO entry: fall back to the
    // home entry rather than leaving a stale title/canonical from whatever route preceded it —
    // matches the static 404.html shell, which is a byte-identical copy of the home shell.
    const home = findSeoRoute('/');
    if (home) {
      document.title = home.title;
      setMetaDescription(home.description);
      setCanonical(canonicalUrl('/'));
    }
  }, [location.pathname]);
}
