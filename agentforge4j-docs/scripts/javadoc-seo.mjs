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
// own source (see assemble-site.mjs's `latestSource`) is what every VERSIONED surface's indexability
// is derived from — never a hardcoded special case, always the same `latestMirroredVersion` value
// this module itself computes below.
//
//   - /javadoc/latest/**  self-canonical, indexable — the evergreen public entry point, in every
//                         lifecycle state. The one Javadoc tree meant to be found in search.
//   - /javadoc/next/**    self-canonical, `noindex,follow` — UNCONDITIONALLY, in every lifecycle
//                         state. `next` tracks `main`, so it is a duplicate of /latest/ whenever
//                         main has not diverged from the newest release tag — which is the normal
//                         steady state, not an edge case (immediately after a release the two trees
//                         are byte-identical, and stay that way until the next API change lands).
//                         Deriving this from `latestMirroredVersion` instead — suppressing /next/
//                         only in the pre-release state — published two byte-identical indexable
//                         trees for as long as main matched the release tag. In-development API
//                         docs are not a search destination in EITHER state, so this never depends
//                         on lifecycle: same unconditional rule the docs surface already applies to
//                         /docs/next/.
//   - /javadoc/<releasedVersions[0]>/** (the version /latest/ currently mirrors) self-canonical,
//                         `noindex,follow` — a duplicate of /latest/'s content.
//   - every OLDER released-version surface: self-canonical, indexable — genuinely distinct
//                         historical content, never a duplicate of /latest/.
//     Once a newer version ships, `releasedVersions[0]` changes and the OLD newest version (now
//     genuinely distinct historical content, no longer a duplicate) automatically becomes indexable
//     again on the very next deploy — no manual re-tagging, no hardcoded version string.
//
// Pinned pages are never canonicalized to /latest/ or /next/ — each stays self-canonical at its own
// URL; noindex,follow (not a cross-surface canonical) is this pass's chosen de-duplication
// mechanism, unchanged from the original design.
//
// One page in every surface is NOT maven-javadoc-plugin output at all: `surfaces.html`, hand-authored
// by `build-javadoc.mjs` itself as the landing page that makes the stitched-surface split explicit.
// Recognized by filename below, it is the one page carrying TWO carve-outs, both keyed on that same
// exact filename and on nothing else:
//
//   - `allowMissingDescription: true` — it ships with no `<meta name="description">` tag by design,
//     so it gets a fresh one inserted instead of tripping the template-drift check every other
//     (genuine plugin-generated) page is still held to;
//   - its own copy (`surfacesLandingCopy`) instead of the generic per-page rule — its raw `<title>`
//     is brand-prefixed prose rather than the bare identifier every generated page carries, so it is
//     the one page in the corpus whose title, heading and description are neither surface-derived
//     nor derived from its own raw title.

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
// pages are never run through `injectJavadocPageSeo` (whose already-processed guard would
// otherwise refuse them — they are the reason that guard cannot be applied sight-unseen to every
// raw page): their canonical, title, refresh and body are all left exactly as the plugin wrote
// them.
//
// They are NOT, however, exempt from the surface's indexability policy. Skipping them whole left a
// hole in it: on a `noindex` surface every real page carried the robots tag while the stub sitting
// at that surface's own root did not, leaving a thin, indexable "index redirect" page inside a tree
// that is meant to be suppressed in full. `addRobotsNoindexTag` below therefore adds the robots tag
// — and ONLY the robots tag — to a stub on a noindex surface, so indexability cannot be escaped
// through a redirect shell while its redirect/canonical behaviour stays byte-for-byte intact.
const REDIRECT_STUB_GENERATOR_TAG = '<meta name="generator" content="javadoc/IndexRedirectWriter">';

/** Whether `html` is maven-javadoc-plugin's own legacy-URL redirect stub (see
 * `REDIRECT_STUB_GENERATOR_TAG`) — the one plugin-generated page kind `applyJavadocSeo` skips
 * instead of processing. Exported so the recognition rule itself is directly testable against the
 * real stub shape. */
export function isJavadocRedirectStub(html) {
  return html.includes(REDIRECT_STUB_GENERATOR_TAG);
}

const ROBOTS_NOINDEX_TAG = '<meta name="robots" content="noindex,follow">';

/**
 * Adds the `noindex,follow` robots tag to `html` and changes NOTHING else — the minimal edit that
 * brings a page under its surface's indexability policy without touching its canonical, title,
 * refresh or body. Used only for the redirect stubs `applyJavadocSeo` otherwise skips whole (see
 * `REDIRECT_STUB_GENERATOR_TAG`), which must keep their plugin-authored redirect behaviour exactly
 * as generated.
 *
 * Idempotent by refusal, matching this module's fail-closed style elsewhere: a page that already
 * carries a robots tag that ALREADY achieves noindex is returned untouched rather than gaining a
 * second, so a double pass can never emit contradictory directives.
 *
 * An existing robots tag that does NOT say `noindex` is a hard error, never a silent pass. Treating
 * "some robots tag is present" as "already suppressed" would let a directive such as
 * `index,follow` survive on a surface the caller has decided must be suppressed — exactly the
 * indexability hole this function exists to close, reintroduced through the guard meant to make it
 * safe to re-run.
 *
 * @param {string} html
 * @returns {string}
 */
export function addRobotsNoindexTag(html) {
  if (!/<\/head>/.test(html)) {
    throw new Error('javadoc-seo: expected a </head> closing tag on a redirect stub');
  }
  const existing = /<meta name="robots" content="([^"]*)">/.exec(html);
  if (existing) {
    if (/\bnoindex\b/i.test(existing[1])) {
      return html;
    }
    throw new Error(
      `javadoc-seo: refusing to leave a redirect stub indexable on a suppressed surface — it already ` +
        `carries a conflicting robots directive ("${existing[1]}") that does not say noindex`,
    );
  }
  // A robots tag in some other shape (attribute order, quoting) is not something this function can
  // safely reason about — fail loudly rather than adding a second, possibly contradictory tag.
  if (/<meta[^>]+name="robots"/i.test(html)) {
    throw new Error('javadoc-seo: a redirect stub carries an unrecognized robots tag shape — template drift?');
  }
  return html.replace(/<\/head>/, () => `${ROBOTS_NOINDEX_TAG}\n</head>`);
}

// Every page maven-javadoc-plugin generates ends its `<title>` with the configured `-windowtitle`
// in parentheses — `Overview (AgentForge4j API (next))`, `agentforge4j.core (AgentForge4j API
// (next))`, and so on for every class/package/index/tree/help page. That window title is a single
// fixed string baked in at GENERATION time (the `<windowtitle>`/`<doctitle>` in
// agentforge4j-docs-javadoc/pom.xml), so it says `next` on every page of every surface — including
// /latest/ and the version-pinned trees, which are copies of a surface built from a release tag by
// that tag's own build (see build-javadoc-versions.mjs). The generator cannot know which lifecycle
// mount its output will be published at, so the correction belongs here, in the one pass that does:
// the raw suffix is stripped and the page's real, lifecycle-correct label is applied instead.
//
// A surface's window title is NOT one fixed string across the published corpus — the three
// surfaces `build-javadoc.mjs` stitches together are generated by two different Maven
// configurations, so both of these shapes ship on a normal publication path:
//
//   aggregate (javadoc:aggregate, explicit pom <windowtitle>):
//     `Overview (AgentForge4j API (next))`
//   the two intentionally-unnamed modules (javadoc:javadoc, NO explicit windowtitle, so
//   maven-javadoc-plugin's own `${project.name} ${project.version} API` default applies):
//     `All Classes and Interfaces (AgentForge4J MCP 0.1.0 API)`
//     `AgentForge4jProperties (AgentForge4J Spring Boot Starter 0.1.0 API)`
//
// Note the capital `J` in the project-name form — matching only the aggregate's literal
// `AgentForge4j API` silently left ~95 pages per tree (mcp + spring-boot-starter) carrying a stale
// window title into their title, description and og:title, and left the sub-surface overview
// headings uncorrected.
//
// Still anchored on the brand, case-insensitively on the `j`/`J`, rather than on "a trailing
// parenthesised group" in general: a page whose own title legitimately ends in parentheses
// (`Foo (deprecated)`) must never have that silently eaten. The optional inner group absorbs the
// aggregate's nested `(next)`; the flat form covers the plugin default, including any future
// version string.
const WINDOW_TITLE_SUFFIX_PATTERN = /^(.*?)\s*\(\s*AgentForge4[jJ]\b[^()]*(?:\([^()]*\)[^()]*)?\)$/;

// The same generated doc title rendered as a visible heading. Matched only when the `<h1>`'s text
// STARTS with the brand and mentions `API` — the shape both generators produce for a surface
// overview (`AgentForge4j API (next)`, `AgentForge4J MCP 0.1.0 API`). A real page heading
// ("Class Foo", "Package com.example", "Module agentforge4j.core") starts with its own kind word
// and can never match, so it is never rewritten.
const DOC_TITLE_HEADING_PATTERN = /(<h1[^>]*>)\s*AgentForge4[jJ]\b[^<]*\bAPI\b[^<]*(<\/h1>)/;

/**
 * A raw Javadoc `<title>` with maven-javadoc-plugin's generated window-title suffix removed —
 * `Overview (AgentForge4j API (next))` -> `Overview`. Returned unchanged when the suffix is absent
 * (a hand-authored page, or a future window title that no longer matches), never guessed at.
 * Exported so the stripping rule is directly testable against real generated titles.
 *
 * @param {string} rawTitle
 * @returns {string}
 */
export function stripJavadocWindowTitle(rawTitle) {
  const match = WINDOW_TITLE_SUFFIX_PATTERN.exec(rawTitle);
  if (!match) {
    return rawTitle;
  }
  const bare = match[1].trim();
  // A page whose entire title IS the window title (the redirect stub's `AgentForge4j API (next)`)
  // has no page-specific part to keep — leave it to the caller's own no-title fallback rather than
  // returning an empty string.
  return bare.length > 0 ? bare : rawTitle;
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

// Element-text escaping for the one value written between tags rather than into an attribute (the
// rewritten `<title>`): `"` needs no escaping in text content and must stay a real quote character,
// so this is deliberately not `escapeHtmlAttribute`.
function escapeHtmlText(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
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
 * `heading` overrides the text written into a matched doc-title `<h1>`, and defaults to `title` —
 * byte-identical behaviour to before for every page that does not pass it. It exists for the one
 * page whose best `<title>` and best visible heading are genuinely different strings: a `<title>`
 * carries the site and lifecycle context a search result needs, while an `<h1>` is read with the
 * page already in front of you, so repeating that context in it produces the doubled, clumsy
 * heading this option was added to fix (see `surfacesLandingCopy`).
 *
 * @param {string} html
 * @param {{title: string, description: string, canonical: string, ogImage: string, heading?: string, noindex?: boolean, allowMissingDescription?: boolean}} options
 */
export function injectJavadocPageSeo(
  html,
  { title, description, canonical, ogImage, heading = title, noindex = false, allowMissingDescription = false },
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

  // The `<title>` is rewritten to the SAME lifecycle-correct text used for og:title/twitter:title,
  // never left as maven-javadoc-plugin generated it. The raw title ends in the generation-time
  // window title (`... (AgentForge4j API (next))`), which is fixed at generation and therefore says
  // `next` even on /latest/ and the version-pinned trees — see `WINDOW_TITLE_SUFFIX_PATTERN`. It is
  // the strongest on-page signal there is, so leaving it untouched published the whole stable API
  // reference under an in-development label. Fails closed on a page with no `<title>` at all, in
  // keeping with this function's other template-drift checks — every page kind in a real corpus,
  // plugin-generated or hand-authored, has one.
  if (!TITLE_TAG_PATTERN.test(result)) {
    throw new Error('javadoc-seo: expected a <title> tag — template drift?');
  }
  result = result.replace(TITLE_TAG_PATTERN, () => `<title>${escapeHtmlText(title)}</title>`);

  // The same generation-time doc title is ALSO rendered as the visible `<h1>` on a surface's
  // overview page (maven-javadoc-plugin's `<h1 class="title">`) and on build-javadoc.mjs's own
  // hand-authored surfaces.html landing page — so /latest/ and every version-pinned tree displayed
  // "AgentForge4j API (next)" as their on-page heading, not only in the `<head>`. Corrected to the
  // same lifecycle-correct text, so the rendered page and its metadata agree.
  //
  // Deliberately narrow: matches ONLY an `<h1>` whose entire text IS the brand doc title. A heading
  // with real page-specific content ("Module agentforge4j.core", "All Classes and Interfaces") can
  // never match and is never touched.
  result = result.replace(
    DOC_TITLE_HEADING_PATTERN,
    (_match, open, close) => `${open}${escapeHtmlText(heading)}${close}`,
  );

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

/**
 * Copy for `surfaces.html` — `build-javadoc.mjs`'s own hand-authored landing page, the one page in
 * a surface that is not maven-javadoc-plugin output at all.
 *
 * It needs its own entry because the generic nested-page rule derives a page's copy from its raw
 * `<title>`, and this page's raw title is `AgentForge4j API — surfaces`: brand-prefixed prose, not
 * the "Foo" / "com.example" / "All Classes and Interfaces" identifier every generated page carries.
 * Appending the lifecycle suffix to it produced
 * `AgentForge4j API — surfaces — AgentForge4j API Reference (latest stable, 0.1.0)` — accurate, and
 * unreadable, with the brand stated twice and two em-dash clauses.
 *
 * Narrow by construction: this is chosen by matching one exact filename in `applyJavadocSeo`, and it
 * changes nothing about how any other page is titled. In particular it does NOT relax
 * `stripJavadocWindowTitle` or `nestedPageCopy` — the general normalization, and the guarantee that
 * no page of any tree is ever labelled with the generator's `(next)` window title, both apply to
 * this page exactly as before. What changes is only which words this one page uses.
 *
 * `heading` differs from `title` deliberately: the `<title>` is read in a search result, where the
 * brand and the lifecycle state are the context that makes it meaningful, while the `<h1>` is read
 * with the page already open, where repeating them is the noise that made the old heading clumsy.
 *
 * The description deliberately does NOT name or count the surfaces. That list is authored in a
 * DIFFERENT module (`build-javadoc.mjs`'s landing-page template) and, for every version-pinned
 * surface, by THAT RELEASE TAG's own historical copy of it (see build-javadoc-versions.mjs) — this
 * pass runs against the already-composed output and cannot see which surfaces a given tag's build
 * actually produced. Enumerating them here would be this module inventing a claim about content it
 * does not own, which is precisely what `nestedPageCopy` below exists to avoid: a future release
 * that adds or drops a surface would silently publish a description its own page body contradicts,
 * with no test able to fail. What is said instead holds for any surface set — the page explains the
 * split and links each one.
 *
 * Kept inside the 157-character meta-description budget the rest of this site already publishes to
 * (`agentforge4j-web-ui/scripts/build-seo.mjs`'s `MAX_DESCRIPTION_LENGTH`): a longer description is
 * cut in the search result, and at 157 the first wording of this one was cut mid-word. Asserted for
 * every lifecycle label in this module's tests rather than enforced at runtime — `nestedPageCopy`'s
 * descriptions are as long as the page title they quote, so a hard limit in `injectJavadocPageSeo`
 * would fail the whole site build on one long class name. The bound is knowable in advance only
 * here, where the wording is fixed and only the label varies.
 */
function surfacesLandingCopy(label) {
  return {
    title: `API Surfaces — AgentForge4j API Reference (${label})`,
    heading: `AgentForge4j API Surfaces (${label})`,
    description:
      `How the AgentForge4j API reference (${label}) is split across its independently generated ` +
      'surfaces, with a link to each one.',
  };
}

/** A nested page's own `<title>` (extracted from its still-unmodified raw HTML) grounds its
 * title/description in real content already present on the page, rather than inventing per-page
 * copy this script has no way to derive correctly for every one of maven-javadoc-plugin's own page
 * kinds (class, package summary, package tree, all-classes index, help, ...). Falls back to the
 * surface label alone only in the (unexpected) case a page carries no `<title>` at all. */
function nestedPageCopy(html, label) {
  const match = TITLE_TAG_PATTERN.exec(html);
  // Stripped BEFORE the label is applied: the raw title carries the generation-time window title
  // (`... (AgentForge4j API (next))`), which would otherwise be propagated verbatim into this
  // page's title, description AND og:title — labelling a /latest/ or version-pinned page `next`.
  const pageTitle = match ? stripJavadocWindowTitle(decodeHtmlEntities(match[1].trim())) : null;
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
 * Every published Javadoc surface, with the mount it lives at, the lifecycle label its pages carry,
 * the version it presents (`null` for `next`, and for `latest` before any release), and whether the
 * whole surface is suppressed from indexing — the duplicate-content policy in this module's header
 * comment, expressed once as data.
 *
 * `applyJavadocSeo` below stamps the robots tags from this; `assemble-site.mjs` builds the composed
 * sitemap's Javadoc entries from the SAME list, taking exactly the surfaces this says are
 * indexable. That shared derivation is the point: a sitemap that advertises a surface the robots
 * tag suppresses (or omits one it does not) is a self-contradiction no separate check would
 * necessarily catch, and it is impossible to express here.
 *
 * @param {string[]} releasedVersions newest first, as `versions.json` holds them
 * @returns {{mountPath: string, label: string, version: string|null, noindex: boolean}[]}
 */
export function javadocSurfaces(releasedVersions) {
  const latestMirroredVersion = releasedVersions.length > 0 ? releasedVersions[0] : null;
  return [
    {
      mountPath: 'javadoc/next',
      label: 'next, in-development',
      version: null,
      // Unconditional, in every lifecycle state — NOT derived from latestMirroredVersion. /next/
      // tracks main and is byte-identical to /latest/ for as long as main has not diverged from the
      // newest release tag, which is the steady state rather than an edge case. See this module's
      // header comment.
      noindex: true,
    },
    {
      mountPath: 'javadoc/latest',
      label: latestMirroredVersion ? `latest stable, ${latestMirroredVersion}` : 'latest (pre-release)',
      // The version whose surface /latest/ is a copy of — what dates it, and what its content
      // actually is. `null` pre-release, when it mirrors `next` instead.
      version: latestMirroredVersion,
      // /latest/ is always the evergreen public entry point, indexable in both lifecycle states.
      noindex: false,
    },
    ...releasedVersions.map((version) => ({
      mountPath: `javadoc/${version}`,
      label: version,
      version,
      // The version /latest/ currently mirrors byte-for-byte gets noindex,follow instead of a
      // second indexable copy of the same content — see this module's header comment. Once a newer
      // release ships, this one becomes genuinely distinct historical content, turns indexable
      // again on the next deploy, and joins the sitemap on that same deploy for the same reason.
      noindex: version === latestMirroredVersion,
    })),
  ];
}

/**
 * Applies `injectJavadocPageSeo` to every generated `.html` page in the composed artifact's
 * javadoc surfaces: `javadoc/next/`, `javadoc/latest/`, and one per entry in `releasedVersions` —
 * the surface's own overview page and every nested class/package/index/tree/help page beneath it,
 * all under the one indexability policy that surface has (see this module's header comment).
 *
 * The plugin's own legacy-URL redirect stubs (see `isJavadocRedirectStub`) are the one exception:
 * they are never run through `injectJavadocPageSeo`, so their plugin-authored `href="index.html"`
 * canonical, title, meta refresh and body are left exactly as generated, and they are never counted
 * in the returned total. They are NOT exempt from the surface's indexability policy though — on a
 * `noindex` surface a stub receives the robots tag, and only the robots tag (`addRobotsNoindexTag`),
 * so the policy cannot be escaped through a redirect shell. On an indexable surface a stub is left
 * byte-identical.
 *
 * @param {{siteDir: string, siteUrl: string, ogImage: string, releasedVersions: string[]}} options
 * @returns {number} the number of pages updated, across every surface
 */
export function applyJavadocSeo({ siteDir, siteUrl, ogImage, releasedVersions }) {
  const surfaces = javadocSurfaces(releasedVersions);

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
        // page — never rewritten (see `isJavadocRedirectStub`). On a noindex surface it still gets
        // the robots tag, and nothing else, so the surface's indexability policy has no hole at the
        // one page kind this pass otherwise leaves alone. Deliberately not counted in `updated`,
        // which reports fully SEO-processed pages.
        if (surface.noindex) {
          // Same failure contract as the ordinary-page path below: a malformed stub must name the
          // file that failed, or a corpus-wide build failure gives no way to find the one page.
          let stubHtml;
          try {
            stubHtml = addRobotsNoindexTag(html);
          } catch (error) {
            throw new Error(`javadoc-seo: failed processing ${pageInput}: ${error.message}`);
          }
          if (stubHtml !== html) {
            writeFileSync(pageInput, stubHtml, 'utf8');
          }
        }
        continue;
      }
      const canonical = canonicalFor(siteUrl, surface.mountPath, surfaceRoot, pageInput);
      const isOverview = pageInput === indexPath;
      const isSurfacesLandingPage = pageInput === join(surfaceRoot, SURFACES_LANDING_FILENAME);
      // Three page kinds, decided by what the page IS, not by guessing from its content: the
      // surface's own overview page, this repo's own hand-authored landing page, and every genuine
      // maven-javadoc-plugin page (whose copy is derived from its own title).
      let copy;
      if (isSurfacesLandingPage) {
        copy = surfacesLandingCopy(surface.label);
      } else if (isOverview) {
        copy = surfaceCopy(surface.label);
      } else {
        copy = nestedPageCopy(html, surface.label);
      }
      let updatedHtml;
      try {
        updatedHtml = injectJavadocPageSeo(html, {
          title: copy.title,
          description: copy.description,
          heading: copy.heading,
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
