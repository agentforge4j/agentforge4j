// SPDX-License-Identifier: Apache-2.0
import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'AgentForge4j',
  tagline: 'An embeddable Java framework for building agentic LLM workflows',
  favicon: 'img/favicon.svg',

  // Improve compatibility with the upcoming Docusaurus v4.
  future: {
    v4: true,
  },

  // Production URL and base path. The site is built to mount under `/docs` on the
  // future agentforge4j.org. `baseUrl` (deployment subpath) and the docs plugin's
  // `routeBasePath` (below) are independent, so routes resolve as
  // baseUrl + routeBasePath + version-path + slug — yielding `/docs/next/...`
  // with no `/docs/docs/...` duplication.
  url: 'https://agentforge4j.org',
  baseUrl: '/docs/',

  organizationName: 'agentforge4j',
  projectName: 'agentforge4j',

  // Drift is a build failure: dead internal links and anchors fail the build.
  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',

  markdown: {
    hooks: {
      // Dead Markdown links fail the build (3.10 home for this option).
      onBrokenMarkdownLinks: 'throw',
    },
  },

  // Translation-readiness only: English authored, structure does not block future locales.
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          // routeBasePath '/' makes the docs the site root under baseUrl, so the
          // public route is `/docs/<version-path>/<slug>` (no `/docs/docs`).
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/agentforge4j/agentforge4j/tree/main/agentforge4j-docs/',
          // The current (editable) docs set is the forthcoming release: served at
          // `/docs/next`, labelled "Next — Unreleased" with a persistent banner.
          // Released/archived versions are added by the release sequence (later phase).
          versions: {
            current: {
              label: 'Next — Unreleased',
              path: 'next',
              banner: 'unreleased',
            },
          },
        },
        // No blog surface in the OSS docs.
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        // Pre-first-release routing (design §3): before any stable version exists,
        // the docs root and the moving `latest` alias both resolve to `next`.
        // The post-first-release toggle (`/` -> `/latest` -> newest stable) lands
        // with the versioning/release phase.
        redirects: [
          {from: '/', to: '/next/'},
          {from: '/latest', to: '/next/'},
        ],
      },
    ],
  ],

  themes: [
    [
      // Offline/local search (no Algolia dependency). Indexes the active docs.
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexDocs: true,
        indexBlog: false,
        docsRouteBasePath: '/',
        highlightSearchTermsOnTargetPage: true,
      },
    ],
  ],

  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'AgentForge4j',
      logo: {
        alt: 'AgentForge4j',
        src: 'img/logo.svg',
        // The brand links to the current-docs home. Theme links are not version-aware,
        // so this targets `next` explicitly; revisited post-first-release.
        href: '/next/',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docs',
          position: 'left',
          label: 'Documentation',
        },
        {
          type: 'docsVersionDropdown',
          position: 'right',
        },
        {
          href: 'https://github.com/agentforge4j/agentforge4j',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            // Theme links are not version-aware, so they target the current `next`
            // version explicitly. The post-first-release phase revisits these to point
            // at the moving `latest` alias.
            {label: 'Get Started', to: '/next/get-started/evaluating'},
            {label: 'Reference', to: '/next/reference/config'},
            {label: 'Examples', to: '/next/examples'},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: 'https://github.com/agentforge4j/agentforge4j'},
            {label: 'Contributing', to: '/next/contributing'},
            {label: 'Release Notes', to: '/next/release-notes'},
          ],
        },
      ],
      copyright:
        'Copyright © AgentForge4j contributors. Licensed under Apache-2.0. Built with Docusaurus.',
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'json', 'bash', 'properties'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
