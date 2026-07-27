// SPDX-License-Identifier: Apache-2.0
import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { canonicalUrl, findSeoRoute, type JsonLd } from '@/config/seo';
import { catalogueData } from '@/lib/catalogueData';
import { catalogueWorkflowDescription, catalogueWorkflowTitle } from '@/lib/catalogueSeo';

// `i` (case-insensitive) and an optional trailing slash before `$`, matching React Router's own
// default matching for the literal `/catalogue/` segment. The captured id itself is matched
// case-SENSITIVELY against real workflow data below (`entry.id === id`) — CatalogueDetailPage.tsx
// does the same exact-match lookup, so a wrong-case id renders that page's own NotFoundPage even
// though the *route* matched; this hook must agree with what actually rendered, not diverge from it.
const CATALOGUE_DETAIL_PATH = /^\/catalogue\/([^/]+?)\/?$/i;

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

// Must stay byte-identical to build-seo.mjs's exported JSON_LD_SCRIPT_ID — the id the build-time
// static shell stamps on its own JSON-LD script, and the one `setJsonLd` below looks for so a fresh
// load adopts that node instead of creating a duplicate beside it. Re-declared rather than imported
// only because this module cannot import build-seo.mjs at all (it pulls in node:child_process).
// tests/usePageSeo.test.tsx imports that constant and asserts on the id the hook CREATES, which is
// the single assertion in the suite that fails if these two ever drift apart — every other guard on
// this invariant lives on the build side and compares the shell against build-seo.mjs's own copy,
// so all of them stay green while production ships a shell this hook can no longer find.
const JSON_LD_SCRIPT_ID = 'seo-json-ld';

/** Adds, updates, or removes the page's `<script type="application/ld+json">` block to match
 * `jsonLd` exactly — `undefined` removes any block a previous route left behind, so structured
 * data never survives a client-side navigation to a route that doesn't declare its own (the build-
 * time shell, scripts/build-seo.mjs's `injectJsonLd`, only ever covers the *first* request; without
 * this, home's JSON-LD would either linger on every later route or never appear at all when a
 * route lands on `/` via client-side navigation rather than a fresh load).
 *
 * Sets `.textContent` rather than splicing a serialized string into `innerHTML` — this writes the
 * script node's text data directly, never engaging the HTML parser's "look for a literal `</script`"
 * scan the way string-based HTML assembly would, so unlike the build-time `injectJsonLd` (which
 * must escape `<` to `\u003c` defensively for exactly that reason) there is no `</script>`-breakout
 * risk here and no escaping is needed. */
function setJsonLd(jsonLd: JsonLd | undefined): void {
  const existing = document.getElementById(JSON_LD_SCRIPT_ID);
  if (jsonLd === undefined) {
    existing?.remove();
    return;
  }
  let script = existing;
  if (!script || script.tagName !== 'SCRIPT') {
    existing?.remove();
    script = document.createElement('script');
    script.id = JSON_LD_SCRIPT_ID;
    script.setAttribute('type', 'application/ld+json');
    document.head.appendChild(script);
  }
  script.textContent = JSON.stringify(jsonLd);
}

/**
 * Keeps `document.title`, `<meta name="description">`, `<link rel="canonical">`, and any
 * structured-data (`jsonLd`) block in sync with the current client-side route. The build-time
 * static per-route HTML shells (scripts/build-seo.mjs) only cover the *first* request to a given
 * route — any subsequent in-app navigation is client-side only and never re-fetches a shell, so
 * without this the tab title/canonical/JSON-LD would silently keep whatever the initially-loaded
 * shell said.
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
      setJsonLd(staticEntry.jsonLd);
      return;
    }

    const catalogueMatch = CATALOGUE_DETAIL_PATH.exec(path);
    const workflow = catalogueMatch
      ? catalogueData.workflows.find((entry) => entry.id === catalogueMatch[1])
      : undefined;
    if (workflow) {
      document.title = catalogueWorkflowTitle(workflow);
      setMetaDescription(catalogueWorkflowDescription(workflow));
      // Built from the real, matched `workflow.id` — never from the visited `path` — so a
      // trailing-slash or differently-cased "catalogue" segment on the visited URL can never leak
      // into the emitted canonical; the canonical is always the one clean, normalized address.
      setCanonical(canonicalUrl(`/catalogue/${workflow.id}`));
      // No catalogue workflow declares its own jsonLd today — clears whatever an earlier route
      // (e.g. home) left behind rather than letting it linger on an unrelated page.
      setJsonLd(undefined);
      return;
    }

    // Unmatched path (NotFoundPage) or any other route with no SEO entry: fall back to the
    // home entry rather than leaving a stale title/canonical from whatever route preceded it —
    // matches the static 404.html shell, whose head carries the same home title/canonical
    // (copy-404.mjs copies the built index.html shell before any per-route rewriting).
    const home = findSeoRoute('/');
    if (home) {
      document.title = home.title;
      setMetaDescription(home.description);
      setCanonical(canonicalUrl('/'));
      setJsonLd(home.jsonLd);
    }
  }, [location.pathname]);
}
