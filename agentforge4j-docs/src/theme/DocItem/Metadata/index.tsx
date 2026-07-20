// SPDX-License-Identifier: Apache-2.0
//
// Thin "wrap" swizzle of @docusaurus/theme-classic's DocItem/Metadata — the component every doc
// page already uses to render its <title>/description/canonical head tags (via Docusaurus's own
// PageMetadata). This adds nothing to that existing behavior except one conditional tag: every
// page belonging to the "current" (unreleased, forthcoming-release) docs version — served at
// /docs/next/** per docusaurus.config.ts's `versions.current.path: 'next'` — gets
// <meta name="robots" content="noindex,follow">.
//
// `useDocsVersion().version` is Docusaurus's own internal version name: it is always the literal
// string "current" for the un-versioned, actively-edited docs set, independent of the `path`/
// `label` configured for it — checking this rather than matching the URL keeps this correct even
// if the configured `path` (currently "next") ever changes. In archive mode
// (docusaurus.config.ts's `archiveVersion` branch), the only included version is the archived one
// itself, never "current", so this never fires there — archive indexability is unaffected.
//
// `follow` (not `nofollow`) deliberately keeps these pages crawlable: robots.txt must not gain a
// Disallow for them, since a crawler that cannot fetch the page can never see the noindex
// directive it is meant to honour.
import React, { type ReactElement } from 'react';
import Head from '@docusaurus/Head';
import { useDocsVersion } from '@docusaurus/plugin-content-docs/client';
import DocItemMetadata from '@theme-original/DocItem/Metadata';

const UNRELEASED_VERSION_NAME = 'current';

export default function DocItemMetadataWrapper(): ReactElement {
  const { version } = useDocsVersion();
  return (
    <>
      <DocItemMetadata />
      {version === UNRELEASED_VERSION_NAME && (
        <Head>
          <meta name="robots" content="noindex,follow" />
        </Head>
      )}
    </>
  );
}
