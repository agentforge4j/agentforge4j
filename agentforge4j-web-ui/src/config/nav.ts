// SPDX-License-Identifier: Apache-2.0

/**
 * Single source of the site's primary navigation, footer columns, and GitHub URL.
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
  { label: 'Docs', to: DOCS_ENTRY_URL, external: true },
  { label: 'API', to: '/api' },
  { label: 'Catalogue', to: '/catalogue' },
  { label: 'Builder', to: '/builder' },
  { label: 'Architecture', to: '/architecture' },
  { label: 'Community', to: '/community' },
];

export const NAV_CTA: NavLink = { label: 'Use', to: '/use' };

export interface FooterColumn {
  readonly heading: string;
  readonly links: readonly NavLink[];
}

export const FOOTER_COLUMNS: readonly FooterColumn[] = [
  {
    heading: 'Product',
    links: [
      { label: 'Docs', to: DOCS_ENTRY_URL, external: true },
      { label: 'API', to: '/api' },
      { label: 'Catalogue', to: '/catalogue' },
      { label: 'Builder', to: '/builder' },
      { label: 'Architecture', to: '/architecture' },
      { label: 'Releases', to: '/releases' },
    ],
  },
  {
    heading: 'Community',
    links: [{ label: 'Community & Contributing', to: '/community' }],
  },
  {
    heading: 'Legal',
    links: [
      { label: 'Legal', to: '/legal' },
      { label: 'Security', to: '/security' },
      { label: 'Contact', to: '/contact' },
    ],
  },
];
