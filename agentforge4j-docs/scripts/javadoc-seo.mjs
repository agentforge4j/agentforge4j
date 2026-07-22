// SPDX-License-Identifier: Apache-2.0
//
// Javadoc SEO post-processing (design decision, this pass). Raw `maven-javadoc-plugin` output
// carries none of this site's usual SEO metadata at all — no canonical, no lang, no OG/Twitter, and
// a generic `content="module index"` description — because it is generated straight from the
// plugin's own templates, never touched by this repo's own build.
//
// Applied centrally here, against the COMPOSED artifact (assemble-site.mjs calls this once per
// javadoc mount: next, latest, and each entry in `releasedVersions`), rather than inside
// build-javadoc.mjs itself: a version-pinned surface is built by checking out that OLD release tag
// and running ITS OWN historical copy of build-javadoc.mjs (see build-javadoc-versions.mjs), which
// does not carry this fix — so patching build-javadoc.mjs alone would leave every
// already-tagged version (today: 0.1.0) permanently unfixed. Post-processing the copied-in output
// here instead applies uniformly to every surface on every deploy, regardless of which historical
// script produced the raw content.
//
// Scope: only each surface's own overview/index page (`javadoc/<mount>/index.html`) — not the
// hundreds of individual generated class/package pages underneath it. That index page is the one
// real, shared, sitemap-adjacent, socially-shareable entry point for the whole surface (exactly
// what this pass's audit checked); touching every generated class page for no added benefit would
// be unrelated-change scope creep and a real risk to Javadoc's own generated search/navigation.
//
// Duplicate-content policy (the task's own preferred option, chosen because today there is only one
// released version and /latest/ mirrors it exactly — see assemble-site.mjs's own `latestSource`):
//   - /javadoc/next/    always self-canonical, indexable.
//   - /javadoc/latest/  always self-canonical, indexable — the evergreen URL meant to be found.
//   - /javadoc/<v>/     self-canonical, indexable... EXCEPT the one version /latest/ currently
//                       mirrors (releasedVersions[0], the same index assembleSite itself already
//                       treats as "latest's source") — that one specific version-pinned surface
//                       gets `noindex,follow` instead (same established pattern already used for
//                       /docs/next/), to avoid two indexable URLs with byte-identical content. Once
//                       a newer version ships, `releasedVersions[0]` changes and the OLD newest
//                       version (now genuinely distinct historical content, no longer a duplicate)
//                       automatically becomes indexable again on the very next deploy — no manual
//                       re-tagging, no hardcoded version string.
//
// /javadoc/next/'s own indexability is intentionally left as-is here (no noindex added) — that is a
// separate policy question this pass's audit did not raise, out of scope for this change.

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const EMPTY_LANG_PATTERN = /<html lang>/;
const DEFAULT_DESCRIPTION_PATTERN = /<meta name="description" content="module index">/;

/**
 * Rewrites one javadoc overview page's raw maven-javadoc-plugin `<head>` in place: fixes `lang`,
 * replaces the generic description, and adds (not replaces — none of these exist in the raw output)
 * canonical, OG, Twitter, and an optional noindex robots tag. Fails loudly if the two known-fragile
 * upstream patterns (lang, generic description) are not found — a maven-javadoc-plugin version bump
 * changing its own template shape should surface here, not silently no-op.
 *
 * @param {string} html
 * @param {{title: string, description: string, canonical: string, ogImage: string, noindex?: boolean}} options
 */
export function injectJavadocOverviewSeo(html, { title, description, canonical, ogImage, noindex = false }) {
  if (!EMPTY_LANG_PATTERN.test(html)) {
    throw new Error('javadoc-seo: expected the raw `<html lang>` (no value) maven-javadoc-plugin emits — template drift?');
  }
  if (!DEFAULT_DESCRIPTION_PATTERN.test(html)) {
    throw new Error('javadoc-seo: expected the raw `content="module index"` description maven-javadoc-plugin emits — template drift?');
  }
  if (!/<\/head>/.test(html)) {
    throw new Error('javadoc-seo: expected a </head> closing tag');
  }

  let result = html
    .replace(EMPTY_LANG_PATTERN, '<html lang="en">')
    .replace(DEFAULT_DESCRIPTION_PATTERN, `<meta name="description" content="${description}">`);

  const robotsTag = noindex ? '<meta name="robots" content="noindex,follow">\n' : '';
  const addedTags =
    `${robotsTag}` +
    `<link rel="canonical" href="${canonical}">\n` +
    `<meta property="og:type" content="website">\n` +
    `<meta property="og:url" content="${canonical}">\n` +
    `<meta property="og:title" content="${title}">\n` +
    `<meta property="og:description" content="${description}">\n` +
    `<meta property="og:image" content="${ogImage}">\n` +
    `<meta name="twitter:card" content="summary">\n` +
    `<meta name="twitter:title" content="${title}">\n` +
    `<meta name="twitter:description" content="${description}">\n` +
    `<meta name="twitter:image" content="${ogImage}">\n`;

  result = result.replace(/<\/head>/, `${addedTags}</head>`);
  return result;
}

function surfaceCopy(label) {
  return {
    title: `AgentForge4j API Reference — ${label}`,
    description: `Generated Javadoc API reference for the AgentForge4j framework (${label}).`,
  };
}

/**
 * Applies `injectJavadocOverviewSeo` to every javadoc surface's own overview page in the composed
 * artifact: `javadoc/next/`, `javadoc/latest/`, and one per entry in `releasedVersions`.
 *
 * @param {{siteDir: string, siteUrl: string, ogImage: string, releasedVersions: string[]}} options
 * @returns {number} the number of surfaces updated
 */
export function applyJavadocSeo({ siteDir, siteUrl, ogImage, releasedVersions }) {
  const latestMirroredVersion = releasedVersions.length > 0 ? releasedVersions[0] : null;

  const surfaces = [
    { mountPath: 'javadoc/next', ...surfaceCopy('next, in-development'), noindex: false },
    {
      mountPath: 'javadoc/latest',
      ...surfaceCopy(latestMirroredVersion ? `latest stable, ${latestMirroredVersion}` : 'latest (pre-release)'),
      noindex: false,
    },
    ...releasedVersions.map((version) => ({
      mountPath: `javadoc/${version}`,
      ...surfaceCopy(version),
      // The version /latest/ currently mirrors byte-for-byte gets noindex,follow instead of a
      // second indexable copy of the same content — see this module's header comment.
      noindex: version === latestMirroredVersion,
    })),
  ];

  let updated = 0;
  for (const surface of surfaces) {
    const indexPath = join(siteDir, ...surface.mountPath.split('/'), 'index.html');
    if (!existsSync(indexPath)) {
      throw new Error(`javadoc-seo: expected surface overview page missing: ${indexPath}`);
    }
    const html = readFileSync(indexPath, 'utf8');
    const canonical = `${siteUrl}/${surface.mountPath}/`;
    const updatedHtml = injectJavadocOverviewSeo(html, {
      title: surface.title,
      description: surface.description,
      canonical,
      ogImage,
      noindex: surface.noindex,
    });
    writeFileSync(indexPath, updatedHtml, 'utf8');
    updated += 1;
  }
  return updated;
}
