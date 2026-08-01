// SPDX-License-Identifier: Apache-2.0

/**
 * Single source of the site's primary navigation, footer columns, and GitHub URL.
 *
 * Every internal `to` is written in the trailing-slash form. That form is not cosmetic: each
 * built route is a directory (`dist/<route>/index.html`), which GitHub Pages serves without a
 * redirect ONLY at its trailing-slash address — the bare form 301s there. It is also the exact
 * form `seo.ts`'s `canonicalUrl` and `build-seo.mjs`'s `withTrailingSlash` already publish as
 * every route's canonical and sitemap URL, so a bare-form link would make the site's own
 * navigation disagree with its own canonicals and send every internal click (and every crawl
 * hop) through a redirect. Two guards hold this, with deliberately different corpora:
 * `verify-seo.mjs` crawls the real production build and fails on any internal link in the
 * PRERENDERED markup that does not answer 200 directly — which covers this file, every page
 * component, and any future link site, but by construction cannot see a link that exists only
 * after a client-side interaction; `tests/App.test.tsx`'s canonical-internal-link-form assertion
 * covers the one such surface that exists today, the header's mobile menu panel, by rendering the
 * shell with it open. So a bare-form entry in this file cannot regress silently by either route —
 * but a NEW client-only surface (a modal, a drawer) is outside both corpora until it is added to
 * that assertion, which is the cost of the client-only rendering, not an oversight.
 * Internal to this module for the foundation track — not consumed by the Docusaurus
 * theme (that cross-build sharing is deferred alongside full visual parity).
 *
 * Entries reference only routes wired in a shipped track. Non-`[B]` routes
 * (`/visualizer`, `/examples`, `/roadmap`, `/search`) are intentionally absent, not
 * dead-linked placeholders.
 */

import docsEntry from '@/generated/docs-entry.json';

export const GITHUB_URL = 'https://github.com/agentforge4j/agentforge4j';

/**
 * The current documentation entry point, resolved at build time from the docs module's own version
 * lifecycle (scripts/build-docs-entry.mjs — see its header for the full reasoning).
 *
 * Deliberately NOT `/docs/`. That address is not a page: it is the client-redirect stub
 * @docusaurus/plugin-client-redirects generates, whose entire body is a meta refresh to the current
 * version. Linking it made every visit to the documentation — and every crawl of it — take an extra
 * hop that only resolves after HTML or JavaScript is parsed, and it was the only way in, since
 * nothing linked the real versioned tree at all. GitHub Pages cannot answer that with a 301, so the
 * site links the destination instead. The stub stays published, correctly labelled and
 * non-indexable, for inbound links that already point at it.
 *
 * Never a hardcoded version: a literal would keep pointing at an older, still-live tree the day a
 * new version ships, and nothing would fail.
 */
export const DOCS_ENTRY_URL: string = docsEntry.url;

export interface NavLink {
  readonly label: string;
  readonly to: string;
  // True for a link that must leave the SPA via a real browser navigation (plain <a>) rather
  // than client-side routing (<Link>) — e.g. Docs, which targets the real Docusaurus artifact
  // the Assembler track composes under /docs/, not an SPA-owned route.
  readonly external?: boolean;
}

export const PRIMARY_NAV: readonly NavLink[] = [
  // DOCS_ENTRY_URL is itself trailing-slash by construction (build-docs-entry.mjs), so the
  // canonical-form rule the docblock above describes holds for this entry too — it just resolves
  // to the versioned tree rather than the redirect stub at `/docs/`.
  { label: 'Docs', to: DOCS_ENTRY_URL, external: true },
  { label: 'API', to: '/api/' },
  { label: 'Catalogue', to: '/catalogue/' },
  { label: 'Builder', to: '/builder/' },
  { label: 'Architecture', to: '/architecture/' },
  { label: 'Community', to: '/community/' },
];

export const NAV_CTA: NavLink = { label: 'Use', to: '/use/' };

export interface FooterColumn {
  readonly heading: string;
  readonly links: readonly NavLink[];
}

export const FOOTER_COLUMNS: readonly FooterColumn[] = [
  {
    heading: 'Product',
    links: [
      { label: 'Docs', to: DOCS_ENTRY_URL, external: true },
      { label: 'API', to: '/api/' },
      { label: 'Catalogue', to: '/catalogue/' },
      { label: 'Builder', to: '/builder/' },
      { label: 'Architecture', to: '/architecture/' },
      { label: 'Releases', to: '/releases/' },
    ],
  },
  {
    heading: 'Community',
    links: [{ label: 'Community & Contributing', to: '/community/' }],
  },
  {
    heading: 'Legal',
    links: [
      { label: 'Legal', to: '/legal/' },
      { label: 'Security', to: '/security/' },
      { label: 'Contact', to: '/contact/' },
    ],
  },
];
