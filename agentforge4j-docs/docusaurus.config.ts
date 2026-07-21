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

// Archive static-export mode (design §7/§12). `AF4J_ARCHIVE_VERSION=<v>` builds exactly ONE released
// version as a self-contained static artifact mounted at `/docs/archive/<v>/`: only that version is
// included, it serves at the archive root (path ''), the root/`latest` redirect toggle is dropped
// (an archive owns no moving alias), and the navbar/footer point inside the archived version itself.
// Driven by scripts/archive-transition.mjs; absent (the default), the config is the live site's.
const archiveVersion = process.env.AF4J_ARCHIVE_VERSION || null;
// Route prefix for navbar/footer targets: inside an archive the archived version IS the site root.
const entryBase = archiveVersion ? '' : `/${docsEntry}`;

const config: Config = {
  title: 'AgentForge4j',
  tagline: 'An embeddable Java framework for governed AI workflows',
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
  // In archive mode the artifact is mounted under its own frozen subpath (design §7), so every
  // generated asset/route reference resolves inside `/docs/archive/<v>/` — self-contained by build.
  baseUrl: archiveVersion ? `/docs/archive/${archiveVersion}/` : '/docs/',

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
          // `/docs/next`, labelled "Next — Unreleased" with a persistent banner. Released versions
          // join this list via the release cut (release-cut.mjs); archived versions leave it via the
          // archive transition (archive-transition.mjs).
          //
          // Archive mode instead includes exactly the one archived version, served at the artifact
          // root with the unmaintained banner (design §7). `lastVersion` is set explicitly so the
          // intent does not ride on Docusaurus's post-filter default.
          ...(archiveVersion
            ? {
                onlyIncludeVersions: [archiveVersion],
                lastVersion: archiveVersion,
                versions: {
                  [archiveVersion]: {
                    label: archiveVersion,
                    path: '',
                    banner: 'unmaintained',
                  },
                },
              }
            : {
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
              }),
        },
        // No blog surface in the OSS docs.
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
        // `changefreq`/`priority` are set to `null` (omitted from the XML) rather than the
        // plugin's own default uniform `weekly`/`0.5` for every page — real crawlers ignore
        // both (see the plugin's own type-comments), and inventing the same value for every
        // page would be exactly the fabricated metadata this pass is meant to avoid.
        // Sitemap plugin routes are recorded baseUrl-inclusive (confirmed against a real build:
        // an unqualified `/next/**` pattern matched nothing), so these patterns are rooted at
        // `/docs/`, not relative to the docs plugin's own routeBasePath.
        // `/docs/next/**` (the unreleased, constantly-changing development docs) is excluded: it
        // is publicly reachable but not the intended stable indexable target — the release cut
        // moves a version out of `next` into its own real `/next`-free path once it ships.
        // `/docs/search` (the local-search plugin's results page) is excluded: it has no unique
        // indexable content of its own.
        sitemap: {
          changefreq: null,
          priority: null,
          ignorePatterns: ['/docs/next/**', '/docs/search'],
        },
      } satisfies Preset.Options,
    ],
  ],

  // An archive artifact owns no moving alias: the root/`latest` redirect toggle belongs to the live
  // site only, so archive mode drops the redirects plugin entirely.
  plugins: archiveVersion
    ? []
    : [
        [
          '@docusaurus/plugin-client-redirects',
          {
            // Routing computed from the support window (design §3). Pre-first-release the docs root and
            // the moving `latest` alias both resolve to `next`; once a stable version exists this flips to
            // `/` -> `/latest` -> newest stable. See scripts/redirect-config.mjs.
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
        // Byte-identical copy of the canonical mark at agentforge4j-web-ui/public/brand/logo-horizontal.svg
        // (single source of truth, per the .org site design's brand requirement) — not independently maintained.
        src: 'img/logo-horizontal.svg',
        // The brand links to the current effective docs entry (see docsEntry above): `next`
        // pre-release, the newest supported stable version once one exists, or the archived
        // version itself (the artifact root) in archive mode.
        href: `${entryBase}/`,
      },
      items: [
        {
          // Back-link to the agentforge4j.org SPA root, composed alongside /docs/ by the
          // Assembler track (design §10/§13). A fully-qualified absolute URL, not a bare
          // path: Docusaurus's Link component baseUrl-prepends any href starting with `/`
          // regardless of the href/to distinction, and its broken-link checker then treats
          // the result as an internal route to validate — a bare `href: '/'` resolves to
          // `/docs/`, which only exists as a static file plugin-client-redirects writes in
          // postBuild (after the checker runs), so the build fails closed. An absolute URL
          // has a protocol, so Docusaurus treats it as external: no baseUrl-prepending, no
          // broken-link check. `target: '_self'` overrides the external-link default of
          // opening a new tab — this is the same site, just outside the /docs/ baseUrl.
          href: 'https://agentforge4j.org/',
          label: '← agentforge4j.org',
          position: 'left',
          target: '_self',
        },
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
            // Targets the current effective docs entry (see entryBase above), so these follow the
            // same version the navbar logo and the `/latest` redirect resolve to.
            {label: 'Get Started', to: `${entryBase}/get-started/evaluating`},
            {label: 'Reference', to: `${entryBase}/reference/config`},
            {label: 'Examples', to: `${entryBase}/examples`},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: 'https://github.com/agentforge4j/agentforge4j'},
            {label: 'Contributing', to: `${entryBase}/contributing`},
            {label: 'Release Notes', to: `${entryBase}/release-notes`},
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
