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
 * hop) through a redirect. `verify-seo.mjs` crawls the real production build and fails on any
 * internal link that does not answer 200 directly, so this cannot silently regress here or at
 * any other link site.
 * Internal to this module for the foundation track — not consumed by the Docusaurus
 * theme (that cross-build sharing is deferred alongside full visual parity).
 *
 * Entries reference only routes wired in a shipped track. Non-`[B]` routes
 * (`/visualizer`, `/examples`, `/roadmap`, `/search`) are intentionally absent, not
 * dead-linked placeholders.
 */

export const GITHUB_URL = 'https://github.com/agentforge4j/agentforge4j';

export interface NavLink {
  readonly label: string;
  readonly to: string;
  // True for a link that must leave the SPA via a real browser navigation (plain <a>) rather
  // than client-side routing (<Link>) — e.g. Docs, which targets the real Docusaurus artifact
  // the Assembler track composes at this exact path (/docs/), not an SPA-owned route.
  readonly external?: boolean;
}

export const PRIMARY_NAV: readonly NavLink[] = [
  { label: 'Docs', to: '/docs/', external: true },
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
      { label: 'Docs', to: '/docs/', external: true },
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
