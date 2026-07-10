// SPDX-License-Identifier: Apache-2.0
import {existsSync, readFileSync} from 'fs';
import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
// Plain ESM remark plugins (no types): vocabulary-tag validation, source-backed includes, Javadoc links.
import vocabRemarkPlugin from './src/remark/vocab.mjs';
import includeRemarkPlugin from './src/remark/include.mjs';
import javadocRemarkPlugin from './src/remark/javadoc.mjs';
// The docs redirect toggle is computed from the released-version support window, not hardcoded, so it
// flips from pre-release (`/`,`/latest` -> `/next`) to post-release (`/` and `/latest` -> newest
// stable) automatically at the first cut. Pre-`0.1.0` `versions.json` is absent, so this is inert.
import {supportWindow} from './scripts/support-window.mjs';
import {redirectConfig, docsEntryPath} from './scripts/redirect-config.mjs';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

function readVersionList(path: string): string[] {
  return existsSync(path) ? (JSON.parse(readFileSync(path, 'utf8')) as string[]) : [];
}

const releasedVersions = readVersionList('./versions.json');
const supportedVersions = supportWindow(releasedVersions, readVersionList('./lts.json'));
const docsRedirects = redirectConfig(supportedVersions);
// The navbar/footer targets are not real routes when `next` is not the answer post-release, so they
// are derived from the same support window as the redirects above: `next` pre-release, the newest
// supported stable version once one exists. Inert today (resolves to `next`, byte-identical to before).
const docsEntry = docsEntryPath(supportedVersions);

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
          // Source-backed includes (resolve first) + vocabulary-tag validation + Javadoc links.
          remarkPlugins: [includeRemarkPlugin, vocabRemarkPlugin, javadocRemarkPlugin],
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
            // Every released version is served at its own explicit `/<version>/` path. Without
            // this, Docusaurus serves the newest release at the docs root (its default for the
            // last version), which collides with the computed `/`,`/latest` -> `/<version>/`
            // routing above and leaves every redirect/navbar target pointing at a route that
            // does not exist. Inert pre-release (`releasedVersions` is empty).
            ...Object.fromEntries(releasedVersions.map((version) => [version, {path: version}])),
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
        // Routing computed from the support window. Pre-first-release the docs root and
        // the moving `latest` alias both resolve to `next`; once a stable version exists both flip to
        // the newest stable version. See scripts/redirect-config.mjs.
        redirects: docsRedirects,
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
        // The brand links to the current effective docs entry (see docsEntry above): `next`
        // pre-release, or the newest supported stable version once one exists.
        href: `/${docsEntry}/`,
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
            // Targets the current effective docs entry (see docsEntry above), so these follow the
            // same version the navbar logo and the `/latest` redirect resolve to.
            {label: 'Get Started', to: `/${docsEntry}/get-started/evaluating`},
            {label: 'Reference', to: `/${docsEntry}/reference/config`},
            {label: 'Examples', to: `/${docsEntry}/examples`},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: 'https://github.com/agentforge4j/agentforge4j'},
            {label: 'Contributing', to: `/${docsEntry}/contributing`},
            {label: 'Release Notes', to: `/${docsEntry}/release-notes`},
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
