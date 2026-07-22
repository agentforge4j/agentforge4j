// SPDX-License-Identifier: Apache-2.0
//
// Javadoc SEO post-processing. Raw `maven-javadoc-plugin` output carries none of this site's usual
// SEO metadata at all — no canonical, no consistent lang, no OG/Twitter — because it is generated
// straight from the plugin's own templates, never touched by this repo's own build. Applied to
// EVERY generated `.html` page in each surface (the overview/module-index page, every package
// summary, every class page, every generated index/tree/help page) — not only the one overview
// page a page-tree audit found this previously missed: a duplicate-content/indexability policy
// applied only at the surface's front door left every nested page underneath it independently
// indexable even when the whole surface is a byte-identical mirror of another one.
//
// Applied centrally here, against the COMPOSED artifact (assemble-site.mjs calls this once per
// javadoc mount: next, latest, and each entry in `releasedVersions`), rather than inside
// build-javadoc.mjs itself: a version-pinned surface is built by checking out that OLD release tag
// and running ITS OWN historical copy of build-javadoc.mjs (see build-javadoc-versions.mjs), which
// does not carry this fix — so patching build-javadoc.mjs alone would leave every already-tagged
// version (today: 0.1.0) permanently unfixed. Post-processing the copied-in output here instead
// applies uniformly to every surface on every deploy, regardless of which historical script
// produced the raw content.
//
// Duplicate-content policy (the task's own preferred option, chosen because today there is only one
// released version and /latest/ mirrors it exactly — see assemble-site.mjs's own `latestSource`),
// applied to EVERY page in the surface, not only its overview:
//   - /javadoc/next/**    always self-canonical, indexable (this pass's own audit did not raise
//                         /next/'s own indexability as a question — left exactly as-is).
//   - /javadoc/latest/**  always self-canonical, indexable — the evergreen tree meant to be found.
//   - /javadoc/<v>/**     self-canonical, indexable... EXCEPT the one version /latest/ currently
//                         mirrors (releasedVersions[0], the same index assembleSite itself already
//                         treats as "latest's source") — every page under that one specific
//                         version-pinned surface gets `noindex,follow` instead (same established
//                         pattern already used for /docs/next/), to avoid duplicate indexable
//                         copies of the same content. Once a newer version ships,
//                         `releasedVersions[0]` changes and the OLD newest version (now genuinely
//                         distinct historical content, no longer a duplicate) automatically becomes
//                         indexable again on the very next deploy — no manual re-tagging, no
//                         hardcoded version string.
// Pinned pages are never canonicalized to /latest/ — each stays self-canonical at its own pinned
// URL; noindex,follow (not a cross-surface canonical) is this pass's chosen de-duplication
// mechanism, unchanged from the original design.

import { existsSync, readdirSync, readFileSync, realpathSync, statSync, writeFileSync } from 'node:fs';
import { isAbsolute, join, relative } from 'node:path';

const EMPTY_LANG_PATTERN = /<html lang>/;
const LANG_WITH_VALUE_PATTERN = /<html lang="[^"]*">/;
const DESCRIPTION_TAG_PATTERN = /<meta name="description" content="[^"]*">/;
const CANONICAL_TAG_PATTERN = /<link rel="canonical"/;
const TITLE_TAG_PATTERN = /<title>([^<]*)<\/title>/;

// Every value interpolated into an HTML attribute below (title, description, canonical, image)
// must pass through this — there is no second, ad hoc escaping path.
function escapeHtmlAttribute(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * Rewrites one raw maven-javadoc-plugin page's `<head>` in place: fixes `lang` only if the raw
 * template left it empty (nested class/package/index pages already carry a correct `lang="en"` —
 * only the overview/module-index page has the known empty-`<html lang>` bug, so a real value already
 * present is left untouched, never assumed broken), replaces whatever generic/mechanical description
 * the plugin generated, and adds (never replaces an existing one — see the already-processed check
 * below) canonical, OG, Twitter, and an optional noindex robots tag.
 *
 * Fails loudly if neither known `lang` shape is present, if there is no `<meta name="description">`
 * tag at all, or if `</head>` is missing — a maven-javadoc-plugin version bump changing its own
 * template shape should surface here, not silently no-op. Also fails loudly (a clear
 * "already processed" error, not silent duplicate-tag insertion) if a canonical tag is already
 * present — the one reliable signal every one of this function's own insertions lands together,
 * so re-running this function on already-processed output is refused rather than silently
 * duplicating every tag it adds.
 *
 * @param {string} html
 * @param {{title: string, description: string, canonical: string, ogImage: string, noindex?: boolean}} options
 */
export function injectJavadocPageSeo(html, { title, description, canonical, ogImage, noindex = false }) {
  if (!/<\/head>/.test(html)) {
    throw new Error('javadoc-seo: expected a </head> closing tag');
  }
  if (CANONICAL_TAG_PATTERN.test(html)) {
    throw new Error(
      'javadoc-seo: this page already has a <link rel="canonical"> tag — refusing to insert duplicate SEO tags ' +
        '(has this output already been processed?)',
    );
  }
  if (!DESCRIPTION_TAG_PATTERN.test(html)) {
    throw new Error('javadoc-seo: expected a <meta name="description" content="..."> tag — template drift?');
  }

  let result = html;
  if (EMPTY_LANG_PATTERN.test(result)) {
    result = result.replace(EMPTY_LANG_PATTERN, '<html lang="en">');
  } else if (!LANG_WITH_VALUE_PATTERN.test(result)) {
    throw new Error('javadoc-seo: expected either `<html lang>` or `<html lang="...">` — template drift?');
  }

  const safeTitle = escapeHtmlAttribute(title);
  const safeDescription = escapeHtmlAttribute(description);
  const safeCanonical = escapeHtmlAttribute(canonical);
  const safeOgImage = escapeHtmlAttribute(ogImage);

  result = result.replace(DESCRIPTION_TAG_PATTERN, `<meta name="description" content="${safeDescription}">`);

  const robotsTag = noindex ? '<meta name="robots" content="noindex,follow">\n' : '';
  const addedTags =
    `${robotsTag}` +
    `<link rel="canonical" href="${safeCanonical}">\n` +
    `<meta property="og:type" content="website">\n` +
    `<meta property="og:url" content="${safeCanonical}">\n` +
    `<meta property="og:title" content="${safeTitle}">\n` +
    `<meta property="og:description" content="${safeDescription}">\n` +
    `<meta property="og:image" content="${safeOgImage}">\n` +
    `<meta name="twitter:card" content="summary">\n` +
    `<meta name="twitter:title" content="${safeTitle}">\n` +
    `<meta name="twitter:description" content="${safeDescription}">\n` +
    `<meta name="twitter:image" content="${safeOgImage}">\n`;

  result = result.replace(/<\/head>/, `${addedTags}</head>`);
  return result;
}

/** Whether `candidate` is genuinely `root` itself or a real descendant of it — never a bare
 * string-prefix comparison (which a sibling directory sharing the same prefix would incorrectly
 * pass). Used to decide whether a symlink's resolved target may be followed; exported so the
 * containment rule itself is directly testable without needing a real symlink (creating one
 * requires elevated privileges on Windows, an environment concern this logic must not depend on to
 * be verifiable). */
export function isWithinRoot(root, candidate) {
  const rel = relative(root, candidate);
  return rel === '' || (!rel.startsWith('..') && !isAbsolute(rel));
}

/** Recursively collects every real `*.html` file under `root`, in no particular order — bounded to
 * `root` itself: a symlinked directory or file is only ever followed when it resolves to a real
 * path still inside `root` (a symlink pointing outside the surface is refused, not silently
 * skipped — this walk must never read content the surface itself does not actually contain). */
function walkHtmlFiles(root) {
  const results = [];
  function walk(dir) {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      let isDirectory = entry.isDirectory();
      let target = full;
      if (entry.isSymbolicLink()) {
        target = realpathSync(full);
        if (!isWithinRoot(root, target)) {
          throw new Error(`javadoc-seo: refusing to follow a symlink that escapes the surface root: ${full} -> ${target}`);
        }
        isDirectory = statSync(target).isDirectory();
      }
      if (isDirectory) {
        walk(full);
      } else if (full.endsWith('.html')) {
        results.push(full);
      }
    }
  }
  walk(root);
  return results;
}

function surfaceCopy(label) {
  return {
    title: `AgentForge4j API Reference — ${label}`,
    description: `Generated Javadoc API reference for the AgentForge4j framework (${label}).`,
  };
}

/** A nested page's own `<title>` (extracted from its still-unmodified raw HTML) grounds its
 * title/description in real content already present on the page, rather than inventing per-page
 * copy this script has no way to derive correctly for every one of maven-javadoc-plugin's own page
 * kinds (class, package summary, package tree, all-classes index, help, ...). Falls back to the
 * surface label alone only in the (unexpected) case a page carries no `<title>` at all. */
function nestedPageCopy(html, label) {
  const match = TITLE_TAG_PATTERN.exec(html);
  const pageTitle = match ? match[1].trim() : null;
  if (!pageTitle) {
    return surfaceCopy(label);
  }
  return {
    title: `${pageTitle} — AgentForge4j API Reference (${label})`,
    description: `Generated Javadoc API reference for the AgentForge4j framework (${label}) — ${pageTitle}.`,
  };
}

/** `htmlFilePath`'s canonical URL within its surface: the surface's own overview page
 * (`index.html` at the surface root) keeps the existing trailing-slash form
 * (`.../javadoc/<mount>/`); every other page canonicalizes to its own full relative path within
 * the surface, forward-slash normalized regardless of host OS
 * (`.../javadoc/<mount>/com/example/Foo.html`) — never rewritten to point at `/latest/` or any
 * other surface: each page is self-canonical, full stop. */
function canonicalFor(siteUrl, mountPath, surfaceRoot, htmlFilePath) {
  const relPath = relative(surfaceRoot, htmlFilePath).split('\\').join('/');
  if (relPath === 'index.html') {
    return `${siteUrl}/${mountPath}/`;
  }
  return `${siteUrl}/${mountPath}/${relPath}`;
}

/**
 * Applies `injectJavadocPageSeo` to every generated `.html` page in the composed artifact's
 * javadoc surfaces: `javadoc/next/`, `javadoc/latest/`, and one per entry in `releasedVersions` —
 * the surface's own overview page and every nested class/package/index/tree/help page beneath it,
 * all under the one indexability policy that surface has (see this module's header comment).
 *
 * @param {{siteDir: string, siteUrl: string, ogImage: string, releasedVersions: string[]}} options
 * @returns {number} the number of pages updated, across every surface
 */
export function applyJavadocSeo({ siteDir, siteUrl, ogImage, releasedVersions }) {
  const latestMirroredVersion = releasedVersions.length > 0 ? releasedVersions[0] : null;

  const surfaces = [
    { mountPath: 'javadoc/next', label: 'next, in-development', noindex: false },
    {
      mountPath: 'javadoc/latest',
      label: latestMirroredVersion ? `latest stable, ${latestMirroredVersion}` : 'latest (pre-release)',
      noindex: false,
    },
    ...releasedVersions.map((version) => ({
      mountPath: `javadoc/${version}`,
      label: version,
      // The version /latest/ currently mirrors byte-for-byte gets noindex,follow instead of a
      // second indexable copy of the same content — see this module's header comment.
      noindex: version === latestMirroredVersion,
    })),
  ];

  let updated = 0;
  for (const surface of surfaces) {
    const surfaceRoot = join(siteDir, ...surface.mountPath.split('/'));
    const indexPath = join(surfaceRoot, 'index.html');
    if (!existsSync(indexPath)) {
      throw new Error(`javadoc-seo: expected surface overview page missing: ${indexPath}`);
    }

    for (const pageInput of walkHtmlFiles(surfaceRoot)) {
      const html = readFileSync(pageInput, 'utf8');
      const canonical = canonicalFor(siteUrl, surface.mountPath, surfaceRoot, pageInput);
      const isOverview = pageInput === indexPath;
      const copy = isOverview ? surfaceCopy(surface.label) : nestedPageCopy(html, surface.label);
      const updatedHtml = injectJavadocPageSeo(html, {
        title: copy.title,
        description: copy.description,
        canonical,
        ogImage,
        noindex: surface.noindex,
      });
      writeFileSync(pageInput, updatedHtml, 'utf8');
      updated += 1;
    }
  }
  return updated;
}
