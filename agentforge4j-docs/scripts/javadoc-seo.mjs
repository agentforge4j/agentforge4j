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
// Duplicate-content policy, applied to EVERY page in a surface, not only its overview. `/latest/`'s
// own source (see assemble-site.mjs's `latestSource`) is what every other surface's indexability is
// derived from — never a hardcoded special case, always the same `latestMirroredVersion` value this
// module itself computes below. Two lifecycle states:
//
//   NO released version exists yet (`releasedVersions` is empty — `latestMirroredVersion === null`,
//   so /latest/ mirrors /next/ byte-for-byte, per assembleSite's own fallback):
//     - /javadoc/latest/** self-canonical, indexable — the evergreen public entry point.
//     - /javadoc/next/**   self-canonical, `noindex,follow` — an exact duplicate of /latest/ in this
//                          state; suppressed so there are not two indexable copies of the same
//                          in-development content.
//
//   ONE OR MORE released versions exist (`latestMirroredVersion` is the newest one, the same value
//   assembleSite's own `latestSource` picks — /latest/ now mirrors that pinned release, not /next/):
//     - /javadoc/next/**              self-canonical, indexable — genuinely distinct in-development
//                                      content once a stable release exists to diverge from.
//     - /javadoc/latest/**            self-canonical, indexable — the evergreen tree meant to be found.
//     - /javadoc/<releasedVersions[0]>/** (the version /latest/ currently mirrors) self-canonical,
//                                      `noindex,follow` — a duplicate of /latest/'s content, same
//                                      established pattern already used for /docs/next/.
//     - every OLDER released-version surface: self-canonical, indexable — genuinely distinct
//                                      historical content, never a duplicate of /latest/.
//     Once a newer version ships, `releasedVersions[0]` changes and the OLD newest version (now
//     genuinely distinct historical content, no longer a duplicate) automatically becomes indexable
//     again on the very next deploy — no manual re-tagging, no hardcoded version string.
//
// Pinned pages are never canonicalized to /latest/ or /next/ — each stays self-canonical at its own
// URL; noindex,follow (not a cross-surface canonical) is this pass's chosen de-duplication
// mechanism, unchanged from the original design.
//
// One page in every surface is NOT maven-javadoc-plugin output at all: `surfaces.html`, hand-authored
// by `build-javadoc.mjs` itself as the three-surface landing page, ships with no
// `<meta name="description">` tag by design. Recognized by filename below and passed
// `allowMissingDescription: true` so it gets a fresh description inserted instead of tripping the
// template-drift check every other (genuine plugin-generated) page is still held to.

import { existsSync, readdirSync, readFileSync, realpathSync, statSync, writeFileSync } from 'node:fs';
import { isAbsolute, join, relative } from 'node:path';

// `build-javadoc.mjs`'s own hand-authored landing page (see this module's header comment) — the one
// recognized non-plugin page allowed to have no description meta.
const SURFACES_LANDING_FILENAME = 'surfaces.html';

// The one raw maven-javadoc-plugin page kind that NATIVELY ships a `<link rel="canonical">` tag:
// the legacy-URL redirect stub (`overview-summary.html`) emitted by `javadoc (17)`'s
// IndexRedirectWriter at each javadoc root — including one per stitched sub-surface (e.g. `mcp/`,
// `spring-boot-starter/`). Its plugin-authored canonical (`href="index.html"`) already points at
// the real overview page, which is exactly the right signal for a pure redirect shell, so these
// pages are recognized by this generator meta tag and skipped whole: left byte-identical, never
// run through `injectJavadocPageSeo` (whose already-processed guard would otherwise refuse them —
// they are the reason that guard cannot be applied sight-unseen to every raw page).
const REDIRECT_STUB_GENERATOR_TAG = '<meta name="generator" content="javadoc/IndexRedirectWriter">';

/** Whether `html` is maven-javadoc-plugin's own legacy-URL redirect stub (see
 * `REDIRECT_STUB_GENERATOR_TAG`) — the one plugin-generated page kind `applyJavadocSeo` skips
 * instead of processing. Exported so the recognition rule itself is directly testable against the
 * real stub shape. */
export function isJavadocRedirectStub(html) {
  return html.includes(REDIRECT_STUB_GENERATOR_TAG);
}

// The known maven-javadoc-plugin bug shape: a bare `lang` attribute with no `=` at all (invalid
// HTML — `lang` is not a boolean attribute), distinct from `lang=""` below.
const EMPTY_LANG_PATTERN = /<html lang>/;
// Captures the value so it can be checked for real (non-whitespace) content — matching `*` here
// (permitting an empty capture) is deliberate: an explicit `lang=""` or a whitespace-only value
// like `lang="   "` must still be recognized and repaired below, not silently accepted just
// because the regex itself matched. The distinction between "empty/whitespace" and "a real value"
// is made by inspecting the captured group's trimmed length, never by the regex shape alone.
const LANG_ATTR_PATTERN = /<html lang="([^"]*)">/;
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
 * template left it empty (in a real corpus, virtually every plugin-generated page — the overview
 * AND the nested class/package/index pages — carries the known empty-`<html lang>` bug; the repair
 * is the same everywhere, and a real value already present, as on the hand-authored
 * `surfaces.html` landing page, is left untouched, never assumed broken), replaces whatever
 * generic/mechanical description the plugin generated (or inserts a fresh one when
 * `allowMissingDescription` is set — see below), and adds (never replaces an existing one — see
 * the already-processed check below) canonical, OG, Twitter, and an optional noindex robots tag.
 *
 * Fails loudly if neither known `lang` shape is present, if `</head>` is missing, or if there is no
 * `<meta name="description">` tag at all UNLESS `allowMissingDescription` is set — a
 * maven-javadoc-plugin version bump changing its own template shape should still surface here for
 * every ordinary page; `allowMissingDescription` exists only for the one recognized non-plugin page
 * this repo generates itself (`build-javadoc.mjs`'s `surfaces.html` landing page — see
 * `applyJavadocSeo`), which genuinely ships with no description meta by design. Also fails loudly (a
 * clear "already processed" error, not silent duplicate-tag insertion) if a canonical tag is already
 * present — a reliable signal every one of this function's own insertions lands together, so
 * re-running this function on already-processed output is refused rather than silently duplicating
 * every tag it adds. That signal is only reliable because the caller never sends this function the
 * one raw page kind that natively carries a canonical of its own: the IndexRedirectWriter redirect
 * stub, recognized and skipped whole by `applyJavadocSeo` (see `isJavadocRedirectStub`).
 *
 * @param {string} html
 * @param {{title: string, description: string, canonical: string, ogImage: string, noindex?: boolean, allowMissingDescription?: boolean}} options
 */
export function injectJavadocPageSeo(
  html,
  { title, description, canonical, ogImage, noindex = false, allowMissingDescription = false },
) {
  if (!/<\/head>/.test(html)) {
    throw new Error('javadoc-seo: expected a </head> closing tag');
  }
  if (CANONICAL_TAG_PATTERN.test(html)) {
    throw new Error(
      'javadoc-seo: this page already has a <link rel="canonical"> tag — refusing to insert duplicate SEO tags ' +
        '(has this output already been processed?)',
    );
  }
  const hasDescriptionTag = DESCRIPTION_TAG_PATTERN.test(html);
  if (!hasDescriptionTag && !allowMissingDescription) {
    throw new Error('javadoc-seo: expected a <meta name="description" content="..."> tag — template drift?');
  }

  let result = html;
  if (EMPTY_LANG_PATTERN.test(result)) {
    // The bare `<html lang>` shape (no `=` at all) — always repaired, never a real value to check.
    result = result.replace(EMPTY_LANG_PATTERN, () => '<html lang="en">');
  } else {
    const langMatch = LANG_ATTR_PATTERN.exec(result);
    if (!langMatch) {
      throw new Error('javadoc-seo: expected either `<html lang>` or `<html lang="...">` — template drift?');
    }
    // An explicit but empty (`lang=""`) or whitespace-only (`lang="   "`) value is the same known
    // missing-language condition as the bare `<html lang>` shape above, just spelled differently —
    // repaired identically, rather than being mistaken for "a real value already present, leave
    // untouched" merely because the regex itself matched a (vacuous) capture.
    if (langMatch[1].trim().length === 0) {
      result = result.replace(LANG_ATTR_PATTERN, () => '<html lang="en">');
    }
    // Else: a real, non-empty lang value is already present — left untouched, per this function's
    // own "never assumed broken" contract for nested pages that already carry a correct lang.
  }

  const safeTitle = escapeHtmlAttribute(title);
  const safeDescription = escapeHtmlAttribute(description);
  const safeCanonical = escapeHtmlAttribute(canonical);
  const safeOgImage = escapeHtmlAttribute(ogImage);

  if (hasDescriptionTag) {
    result = result.replace(DESCRIPTION_TAG_PATTERN, () => `<meta name="description" content="${safeDescription}">`);
  }

  // Function replacers throughout (never a plain-string second argument to String.replace): a
  // value containing a literal `$&`/`` $` ``/`$'` sequence would otherwise be interpreted as a
  // replacement-pattern token instead of literal text, silently corrupting the output.
  const missingDescriptionTag = hasDescriptionTag ? '' : `<meta name="description" content="${safeDescription}">\n`;
  const robotsTag = noindex ? '<meta name="robots" content="noindex,follow">\n' : '';
  const addedTags =
    `${missingDescriptionTag}` +
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

  result = result.replace(/<\/head>/, () => `${addedTags}</head>`);
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
 * `root` itself: a symlinked directory is only ever followed when it resolves to a real path still
 * inside `root` AND that real path has not already been visited via some other route (a symlink
 * pointing outside the surface is refused, not silently skipped — this walk must never read content
 * the surface itself does not actually contain; a symlink pointing back at an already-visited real
 * directory, whether an ancestor — a cycle — or an unrelated alias of the same content, is refused
 * too, rather than recursing forever or silently double-processing every page beneath it).
 *
 * A symlinked HTML *file* is refused outright, unconditionally: `injectJavadocPageSeo` enforces a
 * single-processing ("already processed") invariant per real file, and two distinct walked paths
 * that both resolve to the same underlying file would violate it the moment either path is
 * processed — there is also no principled way to pick which of the two paths is that page's one
 * real canonical URL, so this never attempts to guess. */
function walkHtmlFiles(root) {
  const results = [];
  // Resolved once, up front: a symlink target (always realpath'd below) must be compared against
  // `root` in the same, also-resolved form — otherwise a `siteDir` that itself sits under a
  // symlinked/junctioned parent would spuriously fail a legitimate in-root symlink (the resolved
  // target would no longer string-relate to the unresolved root).
  const realRoot = realpathSync(root);
  // Every real directory this walk has already descended into, keyed by its resolved real path.
  // Closes two distinct failure modes a naive recursive walk would otherwise hit: a directory
  // symlink pointing back at an ancestor (unbounded recursion), and two different walked paths
  // (e.g. a real directory and a symlink alias of it) resolving to the same underlying directory,
  // which would silently double-process every page beneath it exactly like the file-symlink case
  // above.
  const visitedRealDirs = new Set([realRoot]);

  function visitDirectory(full, realDir) {
    if (visitedRealDirs.has(realDir)) {
      throw new Error(
        `javadoc-seo: refusing to walk a directory whose real path was already visited via another route ` +
          `(a symlink cycle, or two paths aliasing the same directory): ${full} -> ${realDir}`,
      );
    }
    visitedRealDirs.add(realDir);
    walk(full);
  }

  function walk(dir) {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isSymbolicLink()) {
        const target = realpathSync(full);
        if (!isWithinRoot(realRoot, target)) {
          throw new Error(`javadoc-seo: refusing to follow a symlink that escapes the surface root: ${full} -> ${target}`);
        }
        if (statSync(target).isDirectory()) {
          visitDirectory(full, target);
        } else if (full.endsWith('.html')) {
          throw new Error(`javadoc-seo: refusing to follow a symlinked HTML file (ambiguous canonical URL): ${full} -> ${target}`);
        }
        // A symlinked non-.html file (e.g. a stylesheet asset) is harmlessly ignored, exactly like
        // a real one — only .html files are ever read or written by this module.
      } else if (entry.isDirectory()) {
        visitDirectory(full, realpathSync(full));
      } else if (full.endsWith('.html')) {
        results.push(full);
      }
    }
  }
  walk(root);
  return results;
}

// The raw <title> text extracted below is still HTML-entity-encoded exactly as maven-javadoc-plugin
// wrote it (e.g. a generic class like `List<String>` renders its title as `List&lt;String&gt;`) —
// decoded back to real characters here so `escapeHtmlAttribute` (applied once, downstream, when this
// text is written into an HTML attribute) is the only encoding pass. Without this, the already-encoded
// `&lt;` would itself be re-escaped to `&amp;lt;`, and a reader would see the literal text "&lt;"
// instead of "<". Decoded in a SINGLE left-to-right pass (one regex, one replacer — never a chain
// of sequential replaces): each source entity decodes exactly once and the replacer's own output is
// never rescanned, so `&amp;lt;` yields the literal text "&lt;" the source genuinely intended (the
// leading `&amp;` consumes the ampersand) and `&#38;amp;` yields the literal text "&amp;" — a
// sequential chain would misread its own first pass's output and double-decode both.
const NAMED_ENTITY_VALUES = { lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', amp: '&' };

function decodeHtmlEntities(value) {
  return value.replace(
    /&(?:(lt|gt|quot|apos|nbsp|amp)|#x([0-9a-fA-F]+)|#(\d+));/g,
    (_match, named, hex, dec) => {
      if (named !== undefined) {
        return NAMED_ENTITY_VALUES[named];
      }
      if (hex !== undefined) {
        return String.fromCodePoint(parseInt(hex, 16));
      }
      return String.fromCodePoint(parseInt(dec, 10));
    },
  );
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
  const pageTitle = match ? decodeHtmlEntities(match[1].trim()) : null;
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
 * all under the one indexability policy that surface has (see this module's header comment). The
 * one exception: the plugin's own legacy-URL redirect stubs (see `isJavadocRedirectStub`) are
 * skipped whole — left byte-identical with their plugin-authored `href="index.html"` canonical
 * intact, and never counted in the returned total.
 *
 * @param {{siteDir: string, siteUrl: string, ogImage: string, releasedVersions: string[]}} options
 * @returns {number} the number of pages updated, across every surface
 */
export function applyJavadocSeo({ siteDir, siteUrl, ogImage, releasedVersions }) {
  const latestMirroredVersion = releasedVersions.length > 0 ? releasedVersions[0] : null;

  const surfaces = [
    {
      mountPath: 'javadoc/next',
      label: 'next, in-development',
      // /next/ mirrors /latest/ byte-for-byte only in the no-release state (assembleSite's own
      // fallback: releasedVersions.length === 0 => latestSource is javadocDir, the same tree /next/
      // itself is) — derived from the same latestMirroredVersion value every other surface's
      // indexability comes from, never a standalone special case.
      noindex: latestMirroredVersion === null,
    },
    {
      mountPath: 'javadoc/latest',
      label: latestMirroredVersion ? `latest stable, ${latestMirroredVersion}` : 'latest (pre-release)',
      // /latest/ is always the evergreen public entry point, indexable in both lifecycle states.
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
      if (isJavadocRedirectStub(html)) {
        // A pure redirect shell whose plugin-authored canonical already points at the real overview
        // page — skipped whole, never rewritten (see `isJavadocRedirectStub`).
        continue;
      }
      const canonical = canonicalFor(siteUrl, surface.mountPath, surfaceRoot, pageInput);
      const isOverview = pageInput === indexPath;
      const copy = isOverview ? surfaceCopy(surface.label) : nestedPageCopy(html, surface.label);
      const isSurfacesLandingPage = pageInput === join(surfaceRoot, SURFACES_LANDING_FILENAME);
      let updatedHtml;
      try {
        updatedHtml = injectJavadocPageSeo(html, {
          title: copy.title,
          description: copy.description,
          canonical,
          ogImage,
          noindex: surface.noindex,
          allowMissingDescription: isSurfacesLandingPage,
        });
      } catch (error) {
        throw new Error(`javadoc-seo: failed processing ${pageInput}: ${error.message}`);
      }
      writeFileSync(pageInput, updatedHtml, 'utf8');
      updated += 1;
    }
  }
  return updated;
}
