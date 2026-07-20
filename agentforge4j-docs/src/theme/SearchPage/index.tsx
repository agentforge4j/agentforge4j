// SPDX-License-Identifier: Apache-2.0
//
// Thin "wrap" swizzle of @easyops-cn/docusaurus-search-local's SearchPage. The results page has
// no unique indexable content of its own (already excluded from the sitemap in
// docusaurus.config.ts's `sitemap.ignorePatterns` for the same reason) — this adds the matching
// <meta name="robots" content="noindex,follow"> so search engines are told the same thing
// directly, rather than only omitting it from the sitemap. `follow` (not `nofollow`) keeps the
// page crawlable — no robots.txt Disallow, since a crawler must be able to fetch the page to read
// the noindex directive at all.
import React, { type ReactElement } from 'react';
import Head from '@docusaurus/Head';
import SearchPage from '@theme-original/SearchPage';

export default function SearchPageWrapper(): ReactElement {
  return (
    <>
      <SearchPage />
      <Head>
        <meta name="robots" content="noindex,follow" />
      </Head>
    </>
  );
}
