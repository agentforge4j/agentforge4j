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

export const GITHUB_URL = 'https://github.com/agentforge4j/agentforge4j';

export interface NavLink {
  readonly label: string;
  readonly to: string;
}

export const PRIMARY_NAV: readonly NavLink[] = [
  { label: 'Docs', to: '/docs' },
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
      { label: 'Docs', to: '/docs' },
      { label: 'API', to: '/api' },
      { label: 'Catalogue', to: '/catalogue' },
      { label: 'Builder', to: '/builder' },
      { label: 'Architecture', to: '/architecture' },
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
