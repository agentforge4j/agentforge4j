// SPDX-License-Identifier: Apache-2.0
//
// Generates three things from the already-built dist/index.html (run after `vite build` and after
// copy-404.mjs — 404.html must stay the *pre-prerender* empty shell, so an unmatched path served
// under a real HTTP 404 boots the SPA and renders NotFoundPage, never a static copy of the full
// prerendered home page body; verify-seo.mjs gates that ordering on every real build):
//
//  1. A per-route static HTML shell for every real SPA route (dist/<route>/index.html) with
//     <title>/<meta description>/<link canonical> (and matching OG/Twitter tags), and now also the
//     route's real prerendered body content (see prerender-routes.mjs), baked in as real static
//     text/markup. There is still no SSR/hydration — main.tsx's `createRoot(...).render()` simply
//     replaces this markup with a fresh, visually-identical client render once the bundle loads —
//     but the *initial* static response is no longer a bare, contentless mount point.
//  2. This module's own sitemap.xml fragment: real absolute HTTPS agentforge4j.org URLs (the exact
//     trailing-slash form GitHub Pages serves directly, with no redirect — see withTrailingSlash)
//     for every route in seo-routes.json marked `sitemap: true` (the default), plus one per real
//     shipped catalogue workflow (src/generated/catalogue-data.json), each with a real git-derived
//     <lastmod> scoped to a curated, explicitly-declared, and audited set of dependencies for that
//     page — NOT derived by AST/import-graph inference (a change elsewhere in the source tree that
//     this declared set does not name is invisible to this mechanism by construction; see the
//     "Deliberately excluded" note below for what was traced and ruled out, not merely overlooked):
//       - `artifactGenerationSourceFiles` (seo-routes.json's top level) — the build/prerender
//         PIPELINE itself, not page content: index.html (the one shared shell template every
//         route's final HTML derives from — vite build product though it is, its own committed
//         source controls every route's `<head>`/`<body>` structure outside the parts injectHead/
//         injectRoot/injectJsonLd explicitly rewrite), main.tsx (the render entrypoint executed
//         INSIDE the headless browser that produces the prerendered snapshot every shell ships —
//         not merely a client-side runtime concern), vite.config.ts (the bundler configuration that
//         shapes the built index.html and every emitted asset the prerender then executes), and
//         this file plus prerender-routes.mjs themselves (the code that decides what "the rendered
//         page" even means). A change to any of these can alter every published page's actual HTML
//         in ways no per-route `sourceFiles` list could ever capture, so all are treated as applying
//         to literally every route.
//       - `globalSourceFiles` (seo-routes.json's top level) — the actual React render surface shared
//         by every page: App.tsx (the root shell + <Routes> composition), appRoutes.ts (the
//         path -> component REGISTRY App.tsx renders from — swapping which component a path maps to
//         changes that route's entire rendered output without touching App.tsx's own text), plus the
//         header/footer/nav shell every page wraps in and the theme machinery rendered inside that
//         shell on every page (ThemeToggle.tsx, theme/ThemeContext.tsx, theme/theme.ts — the toggle
//         button and its initial icon/aria state are part of every page's captured header markup).
//       - a static route's own `sourceFiles` (its page component), plus its own metadata dependency
//         — the newest commit that touched *that route's own entry* in seo-routes.json, not the
//         whole file (see gitLastModifiedDateForRouteMetadata) — so one route's metadata edit never
//         bumps another route's <lastmod>.
//       - `catalogueSourceFiles` (seo-routes.json's top level) — rendering shared by every catalogue
//         detail page (CatalogueDetailPage.tsx, catalogueSeo.ts, renderWorkflowSvg.ts,
//         copy/catalogue.ts, plus catalogueData.ts and scripts/build-catalogue-data.mjs — the loader
//         and generator shaping the catalogue data every detail page renders from) — never applied
//         to static routes.
//       - a catalogue workflow's own committed workflow.json (workflowSourceFile).
//       - the one static route flagged `aggregatesCatalogueWorkflows: true` (/catalogue itself)
//         additionally depends on `aggregateCatalogueSourceFiles` (build-catalogue-data.mjs,
//         catalogueData.ts), the shipped-workflows `index` file (addition/removal/reordering), and
//         every currently-indexed workflow's own workflow.json (name/description) — see
//         aggregateCatalogueDependencies.
//     `null` only when none of a page's dependencies resolve to a real, committed file.
//     assemble-site.mjs (agentforge4j-docs) merges this fragment with the Docusaurus-generated
//     docs/sitemap.xml into the one final sitemap.xml at the composed site root — this script knows
//     nothing about docs pages, and assemble-site.mjs knows nothing about SPA routes.
//
//     Deliberately excluded, traced and ruled out (not merely overlooked):
//       - verify-seo.mjs, copy-404.mjs — verification/post-processing only; neither one's own logic
//         changes what any *tracked* route's shell contains (copy-404.mjs's own output, dist/404.html,
//         is not itself a sitemap-tracked route at all).
//       - usePageSeo.ts (and the `@/config/seo.ts` it reads) — purely a client-side, post-hydration
//         concern for in-app SPA navigation (keeping the browser tab's title/meta in sync after the
//         *first* page load); it only ever mutates `document.head`, never `#root`, so it has zero
//         effect on the captured prerendered markup or the build-time static shell either script here
//         produces.
//       - src/styles/*.css — visual styling only; a class name string inside `#root`'s captured
//         markup is unaffected by what rules that class resolves to.
//       - package.json/package-lock.json globally — deliberately scoped to `/builder` alone (that
//         route embeds a fast-moving third-party UI component with its own visible rendering
//         surface); treating every dependency bump everywhere (eslint, vitest, a transitive patch
//         bump with no rendering effect) as globally material would defeat the point of tracking
//         real per-page dependencies at all.
//
//  3. The not-found head of the already-copied dist/404.html (see injectNotFoundHead). copy-404.mjs
//     copies dist/index.html verbatim, which is right for the BODY and wrong for the HEAD — the
//     catch-all shell would otherwise describe itself as the home page on every mistyped address,
//     and on /404.html itself, which is served at 200. Only the head is rewritten; the empty
//     `<div id="root"></div>` mount point the copy carries is preserved by construction.
//
// Per-workflow title/description formatting mirrors src/lib/catalogueSeo.ts (used by the
// client-side title/meta sync, usePageSeo) — duplicated deliberately, not imported, because this
// is plain Node ESM with no bundler step ahead of it. The truncation rule is far too big to keep
// in sync by eye, so nothing here relies on that: tests/usePageSeo.test.tsx drives BOTH copies of
// every duplicated unit over the real shipped catalogue data and a shared corpus of hard cases,
// and requires identical results.

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, isAbsolute, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WORKFLOW_ID_PATTERN } from './workflow-id-contract.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');

const DIST_DIR = join(MODULE_ROOT, 'dist');
const SEO_ROUTES_PATH = join(MODULE_ROOT, 'src', 'config', 'seo-routes.json');
const CATALOGUE_DATA_PATH = join(MODULE_ROOT, 'src', 'generated', 'catalogue-data.json');

/** The published meta-description budget. Exported, and imported by the test suites rather than
 * re-typed there — the same "one opaque constant, no second place to drift" reasoning
 * JSON_LD_SCRIPT_ID's own comment sets out below. src/lib/catalogueSeo.ts holds the one
 * unavoidable copy (it cannot import this module), bound to this one by tests/usePageSeo.test.tsx. */
export const MAX_DESCRIPTION_LENGTH = 157;

// Every generated route shell is a directory (dist/<path>/index.html), which GitHub Pages only
// serves without a redirect at its trailing-slash address — the non-slash form 301s there. The
// root path is already its own trailing slash and needs no change.
export function withTrailingSlash(routePath) {
  return routePath === '/' ? '/' : `${routePath.replace(/\/+$/, '')}/`;
}

/** Real, reproducible git-derived last-modified date for a source file, as a plain W3C `date`
 * (`YYYY-MM-DD`, `%cs` — committer date, short form). The same commit always reproduces the same
 * value, so this never invents a fresh timestamp on a build where the file did not change. Returns
 * `null` (no `<lastmod>` emitted for that URL) when `relFile` is undefined, or does not exist on
 * disk at all — some fixture/test routes and synthetic catalogue-workflow ids intentionally carry
 * no real backing file, and inventing a date for those would be worse than omitting it. Fails
 * loudly (not silently) only when a file that DOES exist has no git history at all — every real
 * shipped page/workflow source is a committed file, so that specific combination means the mapping
 * itself is wrong, not that the file is new.
 */
export function gitLastModifiedDate(repoRoot, relFile) {
  if (!relFile || !existsSync(join(repoRoot, relFile))) {
    return null;
  }
  const output = execFileSync('git', ['log', '-1', '--format=%cs', '--', relFile], {
    cwd: repoRoot,
    encoding: 'utf8',
  }).trim();
  if (!output) {
    throw new Error(`build-seo: ${relFile} exists but has no git history — is it a committed file?`);
  }
  return output;
}

/** `YYYY-MM-DD` (zero-padded ISO form) sorts identically whether compared as strings or as real
 * dates, so no `Date` parsing is ever needed to find the most recent of several. Returns `null`
 * only when every entry is `null`/`undefined` — same "never invent a date" contract as
 * `gitLastModifiedDate` itself, just applied across a set of already-resolved dates instead of one
 * file. */
function pickNewestDate(dates) {
  const real = dates.filter((date) => date !== null && date !== undefined);
  if (real.length === 0) {
    return null;
  }
  return real.reduce((newest, date) => (date > newest ? date : newest));
}

/** A page's generated HTML is rarely the product of one file: this computes the newest
 * git-derived date across every file in `relFiles`, so a `<lastmod>` reflects whichever real
 * dependency actually changed last, instead of silently understating freshness by looking at only
 * one of them. */
export function newestGitLastModifiedDate(repoRoot, relFiles) {
  return pickNewestDate((relFiles ?? []).map((relFile) => gitLastModifiedDate(repoRoot, relFile)));
}

// Every route object in seo-routes.json's `routes` array closes with a lone `}` (optionally
// followed by a comma) at this exact 4-space indentation — the one boundary pattern every route's
// own JSON block shares, regardless of how much content sits between its opening `"path"` line and
// this line. Not per-route special-casing: the same general rule applied identically to every
// route.
const ROUTE_METADATA_BLOCK_END_PATTERN = '/^    \\}/';

function escapeGitLineRegex(literal) {
  return literal.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
}

/** The newest commit that touched *this route's own entry* in seo-routes.json — not the whole
 * file's history, which would incorrectly bump every other route's `<lastmod>` the moment any one
 * route's title/description changes (the exact over-inclusion this function exists to avoid). Uses
 * git's own line-range history (`git log -L`), anchored on the route's unique
 * `"path": "<routePath>"` line through the next real route-block boundary
 * (`ROUTE_METADATA_BLOCK_END_PATTERN`) — no JSON parsing of the diff, no AST, just git's built-in
 * per-line-range primitive, the same tier of tool `gitLastModifiedDate` already uses for whole-file
 * history.
 *
 * `seoRoutesPath` is expressed relative to `repoRoot` for this git invocation; when it resolves
 * outside `repoRoot` entirely (fixture tests writing seo-routes.json to a temp directory unrelated
 * to the repository whose history `repoRoot` actually holds) this returns `null` immediately rather
 * than asking git to search a nonsensical pathspec — fixture routes with no real committed
 * seo-routes.json backing get no invented metadata date, same "never invent" contract as every
 * other function here. Also returns `null` (rather than throwing) when the route's `"path"` line
 * genuinely is not found in that file's current content at all — a best-effort line-history lookup,
 * not a hard existence guarantee the way `gitLastModifiedDate`'s whole-file check is. */
export function gitLastModifiedDateForRouteMetadata(repoRoot, seoRoutesPath, routePath) {
  const relFile = relative(repoRoot, seoRoutesPath);
  if (relFile.startsWith('..') || isAbsolute(relFile) || !existsSync(join(repoRoot, relFile))) {
    return null;
  }
  const startPattern = `/${escapeGitLineRegex(`"path": "${routePath}"`)}/`;
  try {
    const output = execFileSync(
      'git',
      ['log', '-1', '--format=%cs', `-L${startPattern},${ROUTE_METADATA_BLOCK_END_PATTERN}:${relFile}`],
      { cwd: repoRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
    ).trim();
    const firstLine = output.split('\n', 1)[0];
    return /^\d{4}-\d{2}-\d{2}$/.test(firstLine) ? firstLine : null;
  } catch {
    return null;
  }
}

// Shared HTML-attribute escaping — every value interpolated into an HTML attribute in this file
// (title, description, and canonical alike) must pass through this one function. There is no
// second, ad hoc escaping path: a value that reaches an attribute unescaped is a bug in the
// caller, not an intentionally-exempted case.
export function escapeHtml(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Defense-in-depth re-check of the one workflow-id contract (build-catalogue-data.mjs is the
 * real enforcement point — every id it writes to catalogue-data.json already satisfies this — but
 * this function's own unit tests exercise fixture catalogue data directly, bypassing that
 * generator, so this file cannot simply trust its input unconditionally). A valid id needs no
 * encoding to serve as a route segment, a filesystem directory segment, a canonical URL segment,
 * and a sitemap URL segment all at once — there is exactly one representation of it, used
 * unchanged in every one of those contexts.
 *
 * `RegExp.prototype.test` coerces a non-string argument via `String(...)` before matching, so
 * `undefined`, `null`, a number, or a boolean could otherwise pass by coincidentally stringifying
 * into something the pattern accepts (e.g. `String(123)` === "123", `String(null)` === "null",
 * both of which match the slug pattern's character class). The explicit `typeof` check closes
 * that gap: only a real string is ever accepted, regardless of what the pattern alone would say
 * about its coerced form. */
function assertValidWorkflowId(id) {
  if (typeof id !== 'string' || !WORKFLOW_ID_PATTERN.test(id)) {
    throw new Error(`build-seo: refusing an unsafe catalogue workflow id: ${id}`);
  }
}

function catalogueWorkflowTitle(workflow) {
  return `${workflow.name} — AgentForge4j Catalogue`;
}

/** Below this a truncated description stops being a useful snippet — see catalogueSeo.ts. Exported
 * for the same reason MAX_DESCRIPTION_LENGTH is: a test whose fixture puts a sentence end BELOW
 * this floor proves nothing about the sentence rule, because the floor rejects the cut whatever the
 * rule decides. Binding fixtures to this number is what stops that happening silently. */
export const MIN_USEFUL_DESCRIPTION_LENGTH = 80;
/** A CANDIDATE sentence end; `endsSentence` decides whether it is a real one. See catalogueSeo.ts. */
const SENTENCE_END_PATTERN = /(\S+)[.!?](?=\s|$)/g;
/** Dangling clause punctuation, and a `...` run that would otherwise publish `thought...…` — see
 * catalogueSeo.ts. A single trailing dot is kept: it belongs to an abbreviation. */
const TRAILING_CLAUSE_PUNCTUATION = /(\.{2,}|[\s,;:—–-])+$/;
/** The mirror of the above, for `describesCompleteWords` — see catalogueSeo.ts. */
const DROPPED_CLAUSE_PUNCTUATION = /^(\.{2,}|[,;:—–-])+/;
/** The last whitespace of any kind in a window — see catalogueSeo.ts. */
const LAST_WORD_BOUNDARY_PATTERN = /\s\S*$/;

/** Mirrors src/lib/catalogueSeo.ts's `endsSentence` — an abbreviation (`e.g.`, `etc. and`) or a
 * trailing `...` is not a sentence end, and taking one for a sentence discards most of the budget
 * and reads as broken. See that copy for the reasoning behind both tests. */
function endsSentence(raw, word, end) {
  if (word.includes('.')) {
    return false;
  }
  const rest = raw.slice(end);
  return rest.trim().length === 0 || /^\s+\p{Lu}/u.test(rest);
}

/** Mirrors src/lib/catalogueSeo.ts's `truncateDescription` exactly — the same deliberate
 * duplication (not import) this file's header documents for the other catalogue rules, bound to
 * that copy by tests/usePageSeo.test.tsx, which drives BOTH implementations over the real shipped
 * workflow data and a shared corpus of hard cases and requires identical output.
 *
 * Never a fixed-offset slice: that is what published `…and a verification starter). Sin…` and
 * `…tool invoc…` as this site's own meta descriptions. Sentences first, whole words otherwise. */
export function truncateDescription(raw) {
  if (raw.length <= MAX_DESCRIPTION_LENGTH) {
    return raw;
  }
  let lastSentenceEnd = -1;
  for (const match of raw.matchAll(SENTENCE_END_PATTERN)) {
    const end = match.index + match[0].length;
    if (end > MAX_DESCRIPTION_LENGTH) {
      break;
    }
    if (endsSentence(raw, match[1], end)) {
      lastSentenceEnd = end;
    }
  }
  if (lastSentenceEnd >= MIN_USEFUL_DESCRIPTION_LENGTH) {
    return raw.slice(0, lastSentenceEnd);
  }
  const window = raw.slice(0, MAX_DESCRIPTION_LENGTH - 1);
  const boundary = window.search(LAST_WORD_BOUNDARY_PATTERN);
  const words = (boundary === -1 ? window : window.slice(0, boundary)).replace(TRAILING_CLAUSE_PUNCTUATION, '');
  return `${words}…`;
}

/** Mirrors src/lib/catalogueSeo.ts's `describesCompleteWords` — the mechanical statement of "no word
 * was cut in half", checked against the source text rather than by inspecting the result. */
export function describesCompleteWords(raw, description) {
  const trimmed = raw.trim();
  if (description === trimmed) {
    return true;
  }
  const body = description.replace(/…$/, '');
  if (body.length === 0 || !trimmed.startsWith(body)) {
    return false;
  }
  // The one unavoidable case: the source's first word is longer than everything that fits, so no
  // word-boundary cut exists. See catalogueSeo.ts's copy for why accepting it masks nothing.
  const firstBoundary = trimmed.search(/\s/);
  if (firstBoundary === -1 || firstBoundary >= body.length) {
    return true;
  }
  const remainder = trimmed.slice(body.length).replace(DROPPED_CLAUSE_PUNCTUATION, '');
  return remainder.length === 0 || /^\s/.test(remainder);
}

/** The published `<meta name="description">` for one catalogue workflow, and the point at which a
 * description that ends mid-word becomes impossible to publish silently: the rule's output is
 * checked on every real build, for every shipped workflow — not only for the ones anyone thought
 * to look at. Exported so tests/usePageSeo.test.tsx can bind it to src/lib/catalogueSeo.ts's copy,
 * fallback sentence and all. */
export function catalogueWorkflowDescription(workflow) {
  const raw = workflow.description?.trim();
  if (!raw) {
    return `${workflow.name} — a shipped, ready-to-run AgentForge4j workflow from the workflow catalogue.`;
  }
  const description = truncateDescription(raw);
  // Reported separately from the word-boundary failure below, because the cause is different and
  // the fix is different: nothing survived truncation at all (a source that is entirely clause
  // punctuation), so the workflow's own text is what needs attention, not the rule.
  if (description.replace(/…$/, '').length === 0) {
    throw new Error(
      `build-seo: the description generated for catalogue workflow "${workflow.id}" is empty — its source ` +
        `text has no words to keep: ${JSON.stringify(raw)}`,
    );
  }
  // The specification of "no word was cut in half", re-checked against the source on every build.
  // No INPUT reaches this branch while the rule above is correct — every one of its three paths
  // ends on a real boundary — so it carries no negative test of its own, and that is the point: it
  // is what stops a future REGRESSION of the rule from publishing, not a filter on bad workflows.
  // Restoring the fixed-offset slice makes it fire here, inside buildSeo, for the real shipped
  // agent-creator and workflow-execution-estimator descriptions. The empty-source refusal above is
  // the reachable one, and the test that drives it end-to-end is what proves this whole block is
  // wired into buildSeo at all rather than merely present.
  if (!describesCompleteWords(raw, description)) {
    throw new Error(
      `build-seo: the description generated for catalogue workflow "${workflow.id}" does not end on a word ` +
        `boundary of its source text: ${JSON.stringify(description)}`,
    );
  }
  // Same standing as the check above, and the same reason for having no negative test:
  // `truncateDescription` cannot exceed the budget on any of its three branches.
  if (description.length > MAX_DESCRIPTION_LENGTH) {
    throw new Error(
      `build-seo: the description generated for catalogue workflow "${workflow.id}" is ${description.length} ` +
        `characters, over the ${MAX_DESCRIPTION_LENGTH} limit`,
    );
  }
  return description;
}

/**
 * The social tags whose value is a pure function of the route's own title/description/canonical,
 * and therefore the exact set that must be re-derived on every client-side route change as well as
 * baked into every static shell.
 *
 * This table is the single source for both. `injectHead` below builds its replacements from it, and
 * `src/lib/usePageSeo.ts` re-declares it (it cannot import this module — build-seo.mjs pulls in
 * node:child_process) with `tests/usePageSeo.test.tsx` importing this constant to bind the two
 * copies together. That binding is the point: the audited defect was precisely a divergence between
 * these two surfaces — the static shell rewrote all five of these while the client hook rewrote
 * none of them, so every one went stale the moment a visitor navigated within the app, and no gate
 * on either side could see it because each was individually self-consistent.
 *
 * Deliberately NOT included: the site-constant social tags (`og:type`, `og:site_name`, `og:image`
 * and its dimensions/alt, `twitter:card`, `twitter:image`). Those carry the same value on every
 * page, so index.html is their one home and there is nothing for a route change to re-derive —
 * `verify-seo.mjs` proves every built shell actually carries them.
 */
export const ROUTE_SCOPED_SOCIAL_TAGS = [
  { attribute: 'property', key: 'og:title', source: 'title' },
  { attribute: 'property', key: 'og:description', source: 'description' },
  { attribute: 'property', key: 'og:url', source: 'canonical' },
  { attribute: 'name', key: 'twitter:title', source: 'title' },
  { attribute: 'name', key: 'twitter:description', source: 'description' },
];

/** Escapes a literal so it can be embedded in a RegExp source and match itself. The keys below are
 * committed constants today, but they arrive through an exported table other modules read and
 * extend — an unescaped `.` or `+` in a future key would silently change what the pattern matches
 * rather than failing, which is the worst possible failure mode for a gate. */
function escapeRegExpLiteral(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** The pattern matching one route-scoped social tag exactly as index.html writes it. `\s*` after
 * the tag is included only for a REMOVAL, so deleting a tag does not leave the blank line (and the
 * trailing spaces on it) that the tag used to occupy. */
function socialTagPattern(attribute, key, { consumeTrailingWhitespace = false } = {}) {
  return new RegExp(
    `<meta\\s+${escapeRegExpLiteral(attribute)}="${escapeRegExpLiteral(key)}"[\\s\\S]*?/>` +
      (consumeTrailingWhitespace ? '\\s*' : ''),
  );
}

/**
 * The `[pattern, replacement]` pairs for every route-scoped social tag, derived from one route's
 * resolved values — the single place any producer turns `ROUTE_SCOPED_SOCIAL_TAGS` into edits.
 *
 * Every producer of a `<head>` in this module goes through here: ordinary route shells
 * (`injectHead`), the not-found shell (`injectNotFoundHead`) and redirect stubs
 * (`injectRedirectStub`). Any further one must too, deriving its social replacements from this
 * function rather than hand-copying the five tags, because a copy is a copy whether it lives in
 * another module or in the function next door — and a divergence between two such copies is the
 * defect this whole table exists to close. `build-seo.test.mjs`'s `PRODUCERS` list is where each
 * producer is registered for the mutation test that enforces it.
 *
 * The three differ only in what they feed this function, which is the point: a route shell derives
 * `og:url` from its own canonical, a redirect stub from its destination (the stub is not a page
 * about itself), and a not-found page from nothing at all.
 *
 * The replacement half of each pair is a plain string carrying `escapeHtml`ed route data, and
 * `escapeHtml` deliberately does not escape `$`. Every consumer must therefore apply it through a
 * REPLACER FUNCTION, never as a bare replacement string — see `injectHead`'s own loop for why.
 *
 * A `source` whose value is `null` REMOVES that tag instead of rewriting it, taking the whitespace
 * it occupied with it. That is not a convenience: a not-found page must carry no `og:url`, because
 * that tag makes a claim about which URL the content belongs to, and the address does not exist.
 * Expressing "remove" in the same table-driven pass is what keeps that page from needing its own
 * copy of the list just to differ in one entry — `injectNotFoundHead` is the producer that relies
 * on it.
 */
export function routeScopedSocialReplacements(values) {
  return ROUTE_SCOPED_SOCIAL_TAGS.map(({ attribute, key, source }) => {
    const value = values[source];
    if (value === null || value === undefined) {
      return [socialTagPattern(attribute, key, { consumeTrailingWhitespace: true }), ''];
    }
    return [socialTagPattern(attribute, key), `<meta ${attribute}="${key}" content="${escapeHtml(value)}" />`];
  });
}

/** Replaces the title/description/canonical/OG/Twitter tags already present in the built
 * index.html shell — never adds new tags, so a template drift (a tag renamed/removed from
 * index.html) fails loudly here instead of silently no-op'ing. */
export function injectHead(html, { title, description, canonical }) {
  const safeTitle = escapeHtml(title);
  const safeDescription = escapeHtml(description);
  const safeCanonical = escapeHtml(canonical);
  const replacements = [
    [/<title>[\s\S]*?<\/title>/, `<title>${safeTitle}</title>`],
    [/<meta\s+name="description"[\s\S]*?\/>/, `<meta name="description" content="${safeDescription}" />`],
    [/<link\s+rel="canonical"[\s\S]*?\/>/, `<link rel="canonical" href="${safeCanonical}" />`],
    ...routeScopedSocialReplacements({ title, description, canonical }),
  ];

  let result = html;
  for (const [pattern, replacement] of replacements) {
    if (!pattern.test(result)) {
      throw new Error(`build-seo: expected tag not found in dist/index.html: ${pattern}`);
    }
    // The replacement is supplied via a function, never as a bare replacement string — the same
    // guard injectRoot and injectJsonLd already carry, and load-bearing here for the same reason.
    // `String.prototype.replace` expands `$&`, "$`", `$'` and `$$` inside a replacement STRING
    // *after* escapeHtml has run (escapeHtml handles `& < > "` and deliberately not `$`), so a
    // `$`-token anywhere in a route title, description or a shipped workflow's name/description
    // would reach the shell as document text rather than as itself. `$$` is the dangerous one: it
    // collapses to a single `$` IDENTICALLY on the title, the description meta and all five
    // route-scoped social tags, so verify-seo.mjs's social-consistency pass — which compares those
    // tags against each other — sees a perfectly self-consistent page and the corrupted copy ships.
    // `$&` splices the matched tag's own source into the value, and `$'` splices the entire rest of
    // the document into it. A replacer function is never scanned for those tokens.
    result = result.replace(pattern, () => replacement);
  }
  return result;
}

/**
 * Turns a route's shell into a redirect stub: it forwards to `target`, says so, and carries no
 * content of its own.
 *
 * The alternative this replaces was a second, fully-rendered copy of the destination page at a
 * second address, distinguished only by a `canonicalPath` hint. A canonical is advice, not a rule —
 * so the site was publishing genuine duplicate content and asking search engines to be
 * understanding about it. A stub has nothing to duplicate.
 *
 * `noindex, follow` (not just the canonical) because this address should not be a search result at
 * all; `follow` so the link to the destination still carries signal. The canonical names the
 * destination, which is the standard "the real page is over there" pairing for a redirect shell.
 *
 * The body is replaced with a plain link rather than left empty: with JavaScript disabled the meta
 * refresh still fires, but a client that honours neither must still have a way through, and a
 * crawler that reads the body sees where this address leads.
 *
 * On a fresh load with JavaScript enabled this address is forwarded TWICE: the SPA mounts, the
 * router's REDIRECT_ROUTES entry navigates client-side, and then the refresh the parser already
 * scheduled performs a full document load of the same destination. That is deliberate and not a
 * defect to fix by conditioning the refresh on `<noscript>`. The refresh is the only fallback that
 * still works when JavaScript is enabled but the bundle never arrives (network failure, a blocked
 * script, an unsupported browser); moving it behind `<noscript>` would strand exactly those
 * visitors on the anchor. The cost of keeping it unconditional is one redundant same-origin
 * document request on a low-traffic redirect address, which is the cheaper side of that trade.
 *
 * GitHub Pages serves static files with no redirect configuration of any kind (see
 * .github/workflows/deploy.yml), so a real 301 is not implementable on this host — a meta refresh
 * plus the canonical is the strongest available equivalent, and it is the same mechanism the docs
 * archive stubs (agentforge4j-docs/scripts/assemble-site.mjs) already use for the same reason.
 *
 * `destination` and `canonical` are two different things and must not be collapsed into one value,
 * even though they name the same page. `canonical` is a *claim* about where this content really
 * lives on the public web, so it is absolute (`https://agentforge4j.org/community/`) like every
 * other canonical this site publishes. `destination` is an *instruction* telling the browser where
 * to go next, so it must be root-relative (`/community/`) and resolve against whatever origin is
 * actually serving the page. An absolute destination forwards every non-production origin — the
 * e2e preview server, a local `npm run preview`, the documented local Docker build, a self-hosted
 * deployment, a fork's Pages origin — off the artifact under test and onto the live public site.
 * Both docs-side producers of this same meta refresh (assemble-site.mjs's `writeRedirectStubs` and
 * the plugin stubs redirect-stub-seo.mjs annotates) already emit root-relative targets; this is the
 * same rule, not a new one.
 */
export function injectRedirectStub(html, { destination, canonical, title, description, linkText = 'Continue' }) {
  if (!destination.startsWith('/') || destination.startsWith('//')) {
    // `//host/path` is protocol-relative — an absolute URL wearing a relative shape, and the one
    // form a `startsWith('/')` check alone would let through onto a foreign origin.
    throw new Error(
      `build-seo: a redirect stub's destination must be root-relative, got "${destination}" — ` +
        'an absolute or protocol-relative target forwards non-production origins off the artifact',
    );
  }
  const safeDestination = escapeHtml(destination);
  const safeCanonical = escapeHtml(canonical);
  const replacements = [
    [/<title>[\s\S]*?<\/title>/, `<title>${escapeHtml(title)}</title>`],
    [
      /<meta\s+name="description"[\s\S]*?\/>/,
      `<meta name="description" content="${escapeHtml(description)}" />\n` +
        // Carries ROBOTS_META_ID for the same reason the not-found head does: it marks this tag as
        // written by this build, so usePageSeo's setRobots(null) can remove it on a client-side
        // navigation away from the stub. Without the id the lookup misses and a `noindex` written
        // here would ride along onto the destination route — precisely the failure setRobots's own
        // docblock is written against.
        `    <meta name="robots" id="${ROBOTS_META_ID}" content="noindex, follow" />\n` +
        `    <meta http-equiv="refresh" content="0; url=${safeDestination}" />`,
    ],
    [/<link\s+rel="canonical"[\s\S]*?\/>/, `<link rel="canonical" href="${safeCanonical}" />`],
    // The five social tags from the one shared table. A redirect stub's `canonical` source IS the
    // destination — this page's whole claim is "the content is over there" — so unlike the
    // not-found head it rewrites all five rather than removing one. Listing them here instead would
    // be a third copy of the list the table exists to keep single. These are claims about the
    // public web address, so they take the absolute `canonical`, not the relative destination.
    ...routeScopedSocialReplacements({ title, description, canonical }),
  ];
  let result = html;
  for (const [pattern, replacement] of replacements) {
    if (!pattern.test(result)) {
      throw new Error(`build-seo: expected tag not found while building a redirect stub: ${pattern}`);
    }
    result = result.replace(pattern, () => replacement);
  }
  if (!EMPTY_ROOT_PATTERN.test(result)) {
    throw new Error('build-seo: expected an empty <div id="root"></div> mount point while building a redirect stub');
  }
  return result.replace(
    EMPTY_ROOT_PATTERN,
    // Root-relative for the same reason the refresh is: this anchor is the way through for a client
    // that honours neither the refresh nor JavaScript, and it must lead to this deployment's own
    // copy of the destination, not to the public site.
    () => `<div id="root"><p><a href="${safeDestination}">${escapeHtml(linkText)}</a></p></div>`,
  );
}

/**
 * Rewrites the copied `dist/404.html` head so the catch-all shell describes itself as a not-found
 * page instead of as the home page.
 *
 * copy-404.mjs copies `dist/index.html` verbatim before any per-route rewriting, which is right for
 * the BODY (it must stay the empty pre-prerender mount point — see that script and verify-seo.mjs)
 * but leaves the HEAD saying the home page's title, description, canonical and social tags. Served
 * for every mistyped address, that made a 404 present itself as a second home page, with only the
 * HTTP 404 status keeping it out of an index — and `/404.html` itself is served at 200, where no
 * status protects anything and the mismatch between "looks like the home page" and "is not the home
 * page" is exactly a soft-404 signal.
 *
 * Three things change relative to `injectHead`'s per-route treatment, each for its own reason:
 *  - the canonical link is REMOVED, not repointed. Naming the home page asks a crawler to fold a
 *    nonexistent URL into a real one; naming itself asserts an arbitrary address is a real page.
 *    Neither is true, and a 404 is entitled to say nothing.
 *  - `og:url` is removed for the same reason — it is the canonical's Open Graph counterpart, and
 *    leaving it pointing at the home page would keep making the claim the canonical no longer does.
 *  - a `robots` directive is added, since this is the one shell whose own address (`/404.html`) is
 *    genuinely served at 200. It carries `ROBOTS_META_ID`, which marks it as this build's own: the
 *    client-side hook adopts and updates THIS node on a direct load instead of appending a second
 *    one beside it, and — on every other route, where the directive must be cleared — removes only
 *    a node bearing that id rather than any `meta[name="robots"]` the page happens to have. See
 *    that constant's own comment.
 *
 * Never touches the body, so the empty `<div id="root"></div>` verify-seo.mjs gates on is preserved
 * by construction rather than by care.
 */
export function injectNotFoundHead(html, { title, description, robots }) {
  const safeTitle = escapeHtml(title);
  const safeDescription = escapeHtml(description);
  const replacements = [
    [/<title>[\s\S]*?<\/title>/, `<title>${safeTitle}</title>`],
    [/<meta\s+name="description"[\s\S]*?\/>/, `<meta name="description" content="${safeDescription}" />`],
    // The canonical link is REMOVED, whitespace and all, rather than repointed — see this function's
    // own doc comment. `\s*` keeps the line it occupied from becoming a blank one.
    [/<link\s+rel="canonical"[\s\S]*?\/>\s*/, ''],
    // The five social tags come from the one shared table, with `canonical: null` expressing the one
    // way this page differs from an ordinary route: no og:url. Listing them here instead — which is
    // what this function used to do — would be a second copy of exactly the list the table exists to
    // keep single, and the next tag added to it would reach ordinary routes and silently skip 404s.
    ...routeScopedSocialReplacements({ title, description, canonical: null }),
  ];

  let result = html;
  for (const [pattern, replacement] of replacements) {
    // Same fail-loudly contract as injectHead: these tags all exist in the committed index.html
    // template, so one going missing is template drift to surface, not a silent no-op.
    if (!pattern.test(result)) {
      throw new Error(`build-seo: expected tag not found in dist/404.html: ${pattern}`);
    }
    result = result.replace(pattern, () => replacement);
  }
  if (!/<\/head>/.test(result)) {
    throw new Error('build-seo: expected a </head> closing tag in dist/404.html');
  }
  return result.replace(
    /<\/head>/,
    () => `<meta name="robots" id="${ROBOTS_META_ID}" content="${escapeHtml(robots)}" />\n  </head>`,
  );
}

const EMPTY_ROOT_PATTERN = /<div id="root"><\/div>/;

/** Splices a route's real prerendered content (prerender-routes.mjs's captured `#root` innerHTML)
 * into the empty mount point every shell starts with. `innerHtml` is already-serialized real DOM
 * markup — not user input, not re-escaped. A no-op when `innerHtml` is undefined (no snapshot was
 * captured for this route, e.g. a fixture test that never runs the browser-based prerenderer),
 * preserving today's contentless shell rather than failing — only the real CLI build path is
 * expected to supply a snapshot for every route (enforced separately, by the post-build
 * verify-seo.mjs check against real dist/ output, not by this pure function). */
export function injectRoot(html, innerHtml) {
  if (innerHtml === undefined || innerHtml === null) {
    return html;
  }
  if (!EMPTY_ROOT_PATTERN.test(html)) {
    throw new Error('build-seo: expected an empty <div id="root"></div> mount point in dist/index.html');
  }
  // The replacement is supplied via a function, never as a bare replacement string: serialized DOM
  // markup can legitimately contain `$`-sequences (`$&`, `$'`, "$`", `$$`) that
  // String.prototype.replace would otherwise interpret as substitution patterns and silently
  // corrupt the shell with — deterministically, so the prerenderer's double-capture equality check
  // would never catch it.
  return html.replace(EMPTY_ROOT_PATTERN, () => `<div id="root">${innerHtml}</div>`);
}

// The identical id usePageSeo.ts's client-side `setJsonLd` looks for via `document.getElementById`.
// The two must never drift apart: if this static shell's script carried a different id (or none),
// a fresh load's hydration would find no existing match, create a *second* JSON-LD script of its
// own, and then only ever remove that second one on navigation — permanently stranding this static
// one on every subsequent route. Sharing the id makes hydration adopt and update this exact node
// instead of duplicating it.
//
// Exported, and imported by verify-seo.mjs rather than re-typed there — a deliberate exception to
// the "read the same source-of-truth config independently, never import the other script's
// internals" convention verify-seo.mjs's own withTrailingSlash comment documents. The convention
// governs DERIVATIONS: two scripts computing the same answer from the same committed config should
// compute it separately, so a bug in one cannot make the other agree with it. This is the opposite
// case — an opaque literal with no config to re-derive from, whose entire contract is "these bytes
// are identical everywhere". Duplicating it would not create an independent check; it would create
// a third place to drift. usePageSeo.ts holds the one unavoidable copy (it cannot import this
// module — build-seo.mjs pulls in node:child_process), and tests/usePageSeo.test.tsx imports this
// constant to bind that copy to this one.
export const JSON_LD_SCRIPT_ID = 'seo-json-ld';

/**
 * The id stamped on the `<meta name="robots">` this build writes into dist/404.html, and the one
 * `usePageSeo.ts`'s `setRobots` looks for — an OWNERSHIP marker, exactly as JSON_LD_SCRIPT_ID is
 * for the structured-data block, and shared with that constant's own "opaque literal, imported
 * rather than re-derived" rationale above.
 *
 * Ownership is the whole point, and it is not cosmetic. The robots directive is the one head tag
 * this site adds on ONE page and removes on every other, so the client-side hook has to delete a
 * tag on routes that never declared one — and a deletion keyed on `meta[name="robots"]` alone
 * deletes whatever it finds, including a directive this build never wrote. A host embedding the
 * SPA, a future `index.html` line, or an injected `max-image-preview:large` would be served in the
 * static HTML, then silently vanish from the DOM the moment the bundle hydrated, with every gate
 * green: verify-seo.mjs reads served HTML (where the tag is still there) and
 * verify-client-nav-seo.mjs compares direct load against client navigation (which agree, because
 * the hook erases it on both). Keyed on this id instead, the hook removes only its own node and
 * anything else on the page survives untouched.
 */
export const ROBOTS_META_ID = 'seo-robots';

/** Inserts a route's JSON-LD structured-data block right before `</head>` — an addition, not a
 * replacement (unlike injectHead's tags, no shell starts with one), so only routes that declare a
 * `jsonLd` object in seo-routes.json (today: only "/") get a `<script type="application/ld+json">`
 * at all; every other *static* shell is unaffected (see usePageSeo.ts for the client-side runtime
 * behaviour, which also covers unmatched/404 routes — a materially wider scope than this function's
 * own). A no-op when `jsonLd` is undefined.
 *
 * Carries `id="${JSON_LD_SCRIPT_ID}"` so the client-side hook (usePageSeo.ts's `setJsonLd`) adopts
 * and updates this exact node on hydration rather than creating a duplicate — see the constant's
 * own doc comment above.
 *
 * Every `<` in the serialized JSON is escaped to `\u003c` before it reaches the HTML — `<` is the
 * only character that matters inside a `<script>` body (an HTML parser looks for `</script` byte-
 * for-byte, case-insensitively, regardless of JSON string-quoting), so an unescaped value
 * containing a literal `</script>` would close the tag early and let whatever followed run as live
 * markup/script. `\u003c` is a standard JSON string escape — `JSON.parse` (or any JSON-LD consumer)
 * reads it back as the exact same `<` character, so this changes zero JSON semantics; it is not a
 * general HTML-escaping pass (`>`, `&`, quotes, etc. are untouched and do not need to be — none of
 * them can end a `<script>` body).
 *
 * That escaping only holds because the replacement is supplied via a function, never as a bare
 * replacement string — the same guard injectRoot already documents above, and load-bearing here
 * for the same reason: `String.prototype.replace` expands `$&`, "$`", `$'` and `$$` inside a
 * replacement STRING *after* any escaping has already run, so a `$'` anywhere in a config value
 * would splice the rest of the document — the body's own `</script>` included — straight into this
 * script's body, defeating the escaping above entirely. A replacer function is never scanned for
 * those tokens, so the escaped text reaches the HTML exactly as written. */
export function injectJsonLd(html, jsonLd) {
  if (jsonLd === undefined || jsonLd === null) {
    return html;
  }
  const serialized = JSON.stringify(jsonLd).replace(/</g, '\\u003c');
  const script = `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${serialized}</script>\n  </head>`;
  if (!/<\/head>/.test(html)) {
    throw new Error('build-seo: expected a </head> closing tag in dist/index.html');
  }
  return html.replace(/<\/head>/, () => script);
}

// Route paths are trusted, committed build-time data (seo-routes.json, catalogue workflow ids)
// rather than runtime input — but defense-in-depth is cheap, matches this repo's own
// isSafeManifestPath guard in assemble-site.mjs, and closes the gap for good rather than relying
// on every future caller staying well-behaved. A bare `.` segment is rejected alongside `..`: left
// unchecked, `path.join` collapses it away (`join(distDir, 'catalogue', '.')` === `join(distDir,
// 'catalogue')`), so a `.` id would silently overwrite `/catalogue`'s own shell instead of getting
// its own — the workflow-id contract (workflow-id-contract.mjs) already excludes `.` from every
// real catalogue id, but this check stands on its own for any other route path too.
function assertSafeRoutePath(routePath) {
  const segments = routePath.split('/').filter(Boolean);
  if (segments.some((segment) => segment === '..' || segment === '.' || segment.includes('\\'))) {
    throw new Error(`build-seo: refusing an unsafe route path: ${routePath}`);
  }
  return segments;
}

function writeShell(distDir, routePath, html) {
  if (routePath === '/') {
    writeFileSync(join(distDir, 'index.html'), html, 'utf8');
    return;
  }
  const segments = assertSafeRoutePath(routePath);
  const dir = join(distDir, ...segments);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'index.html'), html, 'utf8');
}

/** The shipped-workflow's real source document (build-catalogue-data.mjs's own
 * SHIPPED_WORKFLOWS_DIR + WORKFLOW_DIR_SUFFIX, expressed relative to REPO_ROOT for `git log`) — a
 * real, committed, meaningfully-versioned file per workflow, unlike the generated
 * catalogue-data.json that aggregates them. */
function workflowSourceFile(id) {
  return `agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/${id}.workflow/workflow.json`;
}

// The one committed file that decides which shipped workflows are "in" the catalogue at all —
// the exact same file build-catalogue-data.mjs's own readIndexIds treats as the sole source of
// membership (never a directory scan: see that script's own listOnDiskWorkflowIds, which only
// exists there as a *cross-check* against this file, not a primary source). A workflow's
// addition, removal, or reordering can only ever happen by editing this file — build-catalogue-
// data.mjs fails the whole build otherwise (its own crossCheckBundles) — so this file's own commit
// history is exactly the signal /catalogue/'s aggregate <lastmod> needs for those three cases.
const SHIPPED_WORKFLOWS_INDEX_PATH = 'agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/index';

/** Every shipped workflow id currently listed in `SHIPPED_WORKFLOWS_INDEX_PATH`, read
 * independently here — not imported from build-catalogue-data.mjs (this file already follows the
 * house convention of reading shared source-of-truth config independently rather than importing
 * another script's internals — see this module's own header comment on catalogueSeo.ts), and not
 * the gitignored, machine-generated catalogue-data.json (which would make /catalogue/'s own
 * <lastmod> depend on a file with no real git history of its own). A plain per-line list, not a
 * filesystem scan — mirrors readIndexIds' own parsing exactly. Returns `[]` when the index file
 * does not exist at this repoRoot at all (a fixture/test repoRoot with no real shipped-workflows
 * tree), same "gracefully degrade, never invent" contract as every other lastmod input here. This
 * is also precisely how a newly added workflow automatically participates in /catalogue/'s
 * dependency set without any second, manually maintained id list: the next build simply re-reads
 * this file fresh. */
function readShippedWorkflowIds(repoRoot) {
  const indexPath = join(repoRoot, SHIPPED_WORKFLOWS_INDEX_PATH);
  if (!existsSync(indexPath)) {
    return [];
  }
  return readFileSync(indexPath, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

/** The complete dependency set for the one aggregate catalogue-list route (seo-routes.json's
 * `/catalogue` entry, flagged `aggregatesCatalogueWorkflows: true`) — everything that can change
 * what it visibly renders: `aggregateCatalogueSourceFiles` (seo-routes.json's top level —
 * build-catalogue-data.mjs's own projection/generation logic, and catalogueData.ts, the typed
 * adapter every render of the list goes through — both are *also* real dependencies of every
 * catalogue detail page, hence appearing in `catalogueSourceFiles` too; this is a genuine shared
 * dependency, not a copy-paste), the index file itself (captures addition, removal, and
 * reordering), and every currently-indexed workflow's own committed workflow.json (captures a
 * name/description edit on any shipped workflow, since the aggregate list renders every one of
 * them). Deliberately excludes the catalogue *manifest* (agentforge4j-catalog.json —
 * catalogVersion/min/maxAgentForge4jVersion/workflowSchemaVersion) and the workflow JSON
 * Schema/its Java version source: traced and ruled out deliberately — both gate what data is *valid*, but
 * neither field is ever rendered by CataloguePage.tsx, so a schema-only or manifest-only change
 * produces byte-identical rendered output and correctly contributes nothing here. Also
 * deliberately excludes the catalogue-*detail*-page-only files in `catalogueSourceFiles`
 * (CatalogueDetailPage.tsx, catalogueSeo.ts, renderWorkflowSvg.ts, copy/catalogue.ts) —
 * CataloguePage.tsx's own real imports are `catalogueData` and `CATALOGUE_COPY` only (the latter
 * already lives in /catalogue's own `sourceFiles`); none of the remaining detail-page-only files
 * are genuine dependencies of the list page, so that scope is never blindly reused wholesale. */
function aggregateCatalogueDependencies(repoRoot, aggregateCatalogueSourceFiles) {
  return [
    ...aggregateCatalogueSourceFiles,
    SHIPPED_WORKFLOWS_INDEX_PATH,
    ...readShippedWorkflowIds(repoRoot).map((id) => workflowSourceFile(id)),
  ];
}

/** Every entry a route/scope *declares* must actually exist — a typo'd or stale sourceFiles path
 * must fail the build loudly, not silently degrade to a missing/incomplete `<lastmod>` the way
 * `gitLastModifiedDate`'s own "never invent" contract otherwise allows. That contract exists for
 * routes that legitimately declare *no* dependency at all (an empty/absent `sourceFiles`, e.g. a
 * fixture route with nothing to track) — it was never meant to also swallow a real declared entry
 * that simply doesn't resolve, which is a configuration bug, not a legitimate "no data" case. */
function assertDependencyFilesExist(repoRoot, relFiles, context) {
  for (const relFile of relFiles) {
    if (!existsSync(join(repoRoot, relFile))) {
      throw new Error(
        `build-seo: ${context} declares "${relFile}", which does not exist at ${join(repoRoot, relFile)} — ` +
          'fix the typo or remove the stale entry',
      );
    }
  }
}

/** A route's `jsonLd` (seo-routes.json) is either absent, or a genuine schema.org structured-data
 * object — never a typo'd non-object value, and never an empty placeholder that would silently
 * ship as valid-looking-but-useless structured data. Requires a non-empty `@context` string and at
 * least one of a non-empty `@type` string or a non-empty `@graph` array whose every node itself
 * declares a non-empty `@type` — the two shapes `injectJsonLd` actually ever needs to serialize.
 *
 * `@type` and `@graph` are each validated independently whenever the *key* is present, rather than
 * one validated shape being allowed to paper over the other: an object with both a valid `@type`
 * and a malformed `@graph` (or vice versa) is still rejected, not silently accepted because *some*
 * shape happened to be valid — a plain `hasType || hasValidGraph` check would let either key's own
 * garbage value through undetected as long as the other key looked fine.
 *
 * Called at build time so a malformed config fails the build loudly, the same guarantee
 * `assertDependencyFilesExist` gives a typo'd `sourceFiles` entry. */
function assertValidJsonLd(jsonLd, routePath) {
  if (jsonLd === undefined) {
    return;
  }
  if (jsonLd === null || typeof jsonLd !== 'object' || Array.isArray(jsonLd)) {
    throw new Error(`build-seo: route "${routePath}"'s jsonLd must be a plain object (or omitted) — got ${JSON.stringify(jsonLd)}`);
  }
  if (typeof jsonLd['@context'] !== 'string' || jsonLd['@context'].length === 0) {
    throw new Error(`build-seo: route "${routePath}"'s jsonLd is missing a non-empty "@context" string`);
  }
  const hasTypeKey = Object.hasOwn(jsonLd, '@type');
  if (hasTypeKey && !(typeof jsonLd['@type'] === 'string' && jsonLd['@type'].length > 0)) {
    throw new Error(`build-seo: route "${routePath}"'s jsonLd declares "@type" but it is not a non-empty string — got ${JSON.stringify(jsonLd['@type'])}`);
  }
  const hasGraphKey = Object.hasOwn(jsonLd, '@graph');
  const graph = jsonLd['@graph'];
  const isValidGraph =
    Array.isArray(graph) &&
    graph.length > 0 &&
    graph.every((node) => node !== null && typeof node === 'object' && !Array.isArray(node) && typeof node['@type'] === 'string' && node['@type'].length > 0);
  if (hasGraphKey && !isValidGraph) {
    throw new Error(
      `build-seo: route "${routePath}"'s jsonLd declares "@graph" but it is not a non-empty array whose every entry ` +
        'itself declares a non-empty "@type"',
    );
  }
  // Every key that IS present has already been proven valid above (each throws on its own if not) —
  // the only remaining failure is neither key being declared at all.
  if (!hasTypeKey && !hasGraphKey) {
    throw new Error(
      `build-seo: route "${routePath}"'s jsonLd must declare a non-empty "@type" string, or a non-empty "@graph" ` +
        'array whose every entry itself declares a non-empty "@type" — got neither',
    );
  }
}

function sitemapXml(entries) {
  const body = entries
    .map(({ url, lastmod }) => {
      const lastmodTag = lastmod ? `\n    <lastmod>${escapeHtml(lastmod)}</lastmod>` : '';
      return `  <url>\n    <loc>${escapeHtml(url)}</loc>${lastmodTag}\n  </url>`;
    })
    .join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    `${body}\n</urlset>\n`
  );
}

/**
 * @param {{distDir?: string, seoRoutesPath?: string, catalogueDataPath?: string,
 *          repoRoot?: string, snapshots?: Record<string, string>}} [options] `snapshots` maps a
 *        route path (exactly as written in seo-routes.json / `/catalogue/<id>`) to its real
 *        prerendered `#root` markup (prerender-routes.mjs) — omitted entirely (`{}`, the default)
 *        for fixture-based unit tests that have no headless browser available; every real CLI
 *        build (see `main` below) always supplies one entry per route.
 * @returns {{shellsWritten: number, sitemapUrls: string[]}}
 */
export function buildSeo({
  distDir = DIST_DIR,
  seoRoutesPath = SEO_ROUTES_PATH,
  catalogueDataPath = CATALOGUE_DATA_PATH,
  repoRoot = REPO_ROOT,
  snapshots = {},
} = {}) {
  const indexPath = join(distDir, 'index.html');
  if (!existsSync(indexPath)) {
    throw new Error(`build-seo: ${indexPath} does not exist — run "vite build" first`);
  }
  const baseHtml = readFileSync(indexPath, 'utf8');

  const {
    siteUrl,
    notFound,
    artifactGenerationSourceFiles = [],
    globalSourceFiles = [],
    catalogueSourceFiles = [],
    aggregateCatalogueSourceFiles = [],
    routes,
  } = JSON.parse(readFileSync(seoRoutesPath, 'utf8'));
  const catalogueData = existsSync(catalogueDataPath)
    ? JSON.parse(readFileSync(catalogueDataPath, 'utf8'))
    : { workflows: [] };

  // Combined once: `artifactGenerationSourceFiles` (the build/prerender pipeline itself) and
  // `globalSourceFiles` (the shared React render surface) are declared as two separate, clearly
  // named scopes in seo-routes.json for readability, but they apply identically everywhere — every
  // SPA sitemap route, static or catalogue — so every lastmod computation below uses this one
  // combined list rather than re-deriving it per call site.
  const everyGlobalSourceFile = [...artifactGenerationSourceFiles, ...globalSourceFiles];

  // One whole-file git date per (file, invocation): the two global scopes alone are dependencies
  // of every sitemap URL, so resolving them per route would spawn O(routes × files) sequential
  // git subprocesses for identical answers — slow enough on a real checkout (whole seconds per
  // buildSeo call) to time out consumers that call this synchronously. Deliberately scoped to
  // this invocation rather than the module: callers (tests especially) legitimately commit to the
  // same repository between buildSeo() calls and must observe the new dates on the next call.
  const dateByRelFile = new Map();
  const newestDeclaredDate = (relFiles) =>
    pickNewestDate(
      relFiles.map((relFile) => {
        if (!dateByRelFile.has(relFile)) {
          dateByRelFile.set(relFile, gitLastModifiedDate(repoRoot, relFile));
        }
        return dateByRelFile.get(relFile);
      }),
    );

  assertDependencyFilesExist(repoRoot, artifactGenerationSourceFiles, 'artifactGenerationSourceFiles');
  assertDependencyFilesExist(repoRoot, globalSourceFiles, 'globalSourceFiles');
  assertDependencyFilesExist(repoRoot, catalogueSourceFiles, 'catalogueSourceFiles');
  assertDependencyFilesExist(repoRoot, aggregateCatalogueSourceFiles, 'aggregateCatalogueSourceFiles');
  for (const route of routes) {
    assertDependencyFilesExist(repoRoot, route.sourceFiles ?? [], `route "${route.path}"'s sourceFiles`);
    assertValidJsonLd(route.jsonLd, route.path);
  }

  const sitemapEntries = [];
  let shellsWritten = 0;

  for (const route of routes) {
    if (route.redirectTo) {
      // A permanent forward, not a page — no prerendered body, no structured data, and never a
      // sitemap entry regardless of what the entry says (asserted rather than assumed below, since
      // a redirect listed for indexing is a config error, not a preference).
      if (route.sitemap !== false) {
        throw new Error(
          `build-seo: route "${route.path}" declares redirectTo but is not marked \`"sitemap": false\` — ` +
            'a redirect has no content to index and must never be submitted for indexing',
        );
      }
      writeShell(
        distDir,
        route.path,
        injectRedirectStub(baseHtml, {
          // Relative: where the browser goes next, resolved against the serving origin.
          destination: withTrailingSlash(route.redirectTo),
          // Absolute: what this address claims about itself on the public web.
          canonical: `${siteUrl}${withTrailingSlash(route.redirectTo)}`,
          title: route.title,
          description: route.description,
          // Human-readable, not the raw URL: this body is what a visitor with JavaScript disabled
          // actually reads, and "Continue to https://agentforge4j.org/community/" is an address,
          // not a sentence. The destination route's own declared title is the name the rest of the
          // site already uses for that page.
          linkText: `Continue to ${routes.find((entry) => entry.path === route.redirectTo)?.title ?? route.redirectTo}`,
        }),
      );
      shellsWritten += 1;
      continue;
    }
    const canonicalPath = route.canonicalPath ?? route.path;
    const canonical = `${siteUrl}${withTrailingSlash(canonicalPath)}`;
    let html = injectHead(baseHtml, { title: route.title, description: route.description, canonical });
    html = injectRoot(html, snapshots[route.path]);
    html = injectJsonLd(html, route.jsonLd);
    writeShell(distDir, route.path, html);
    shellsWritten += 1;
    if (route.sitemap !== false) {
      const aggregateDeps = route.aggregatesCatalogueWorkflows
        ? aggregateCatalogueDependencies(repoRoot, aggregateCatalogueSourceFiles)
        : [];
      sitemapEntries.push({
        url: `${siteUrl}${withTrailingSlash(route.path)}`,
        lastmod: pickNewestDate([
          newestDeclaredDate([...everyGlobalSourceFile, ...(route.sourceFiles ?? []), ...aggregateDeps]),
          gitLastModifiedDateForRouteMetadata(repoRoot, seoRoutesPath, route.path),
        ]),
      });
    }
  }

  for (const workflow of catalogueData.workflows ?? []) {
    // One id, one representation, used unchanged everywhere: the route segment, the filesystem
    // directory segment, and the canonical/sitemap URL segment are all this exact string —
    // assertValidWorkflowId guarantees it is safe as all four before any of them are built.
    assertValidWorkflowId(workflow.id);
    const routePath = `/catalogue/${workflow.id}`;
    const canonical = `${siteUrl}${withTrailingSlash(routePath)}`;
    let html = injectHead(baseHtml, {
      title: catalogueWorkflowTitle(workflow),
      description: catalogueWorkflowDescription(workflow),
      canonical,
    });
    html = injectRoot(html, snapshots[routePath]);
    writeShell(distDir, routePath, html);
    shellsWritten += 1;
    sitemapEntries.push({
      url: canonical,
      lastmod: newestDeclaredDate([
        ...everyGlobalSourceFile,
        ...catalogueSourceFiles,
        workflowSourceFile(workflow.id),
      ]),
    });
  }

  const urls = sitemapEntries.map((entry) => entry.url);
  const uniqueUrls = [...new Set(urls)];
  if (uniqueUrls.length !== urls.length) {
    throw new Error(
      'build-seo: duplicate URL(s) computed for the sitemap fragment — check seo-routes.json/catalogue ids',
    );
  }

  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml(sitemapEntries), 'utf8');

  // The catch-all shell copy-404.mjs already wrote (it runs BEFORE this script, deliberately — see
  // its header). Rewriting its head here rather than there keeps that script's one job — produce a
  // byte-identical, pre-prerender copy — intact and separately verifiable, and puts the head
  // rewriting in the one module that already owns it. Deliberately not counted in `shellsWritten`:
  // 404.html is not a route shell and is not a sitemap URL.
  const notFoundPath = join(distDir, '404.html');
  let notFoundShellWritten = false;
  if (notFound && existsSync(notFoundPath)) {
    writeFileSync(notFoundPath, injectNotFoundHead(readFileSync(notFoundPath, 'utf8'), notFound), 'utf8');
    notFoundShellWritten = true;
  }

  return { shellsWritten, sitemapUrls: uniqueUrls, notFoundShellWritten };
}

async function main() {
  // Imported lazily, only on the real CLI build path: prerender-routes.mjs pulls in playwright at
  // module load, and buildSeo/injectRoot/injectHead must stay importable (unit tests, and any
  // consumer of the pure functions) without loading a headless-browser dependency at all.
  const { prerenderRoutes } = await import('./prerender-routes.mjs');
  const snapshots = await prerenderRoutes({ distDir: DIST_DIR });
  const result = buildSeo({ snapshots });
  console.log(
    `[build-seo] wrote ${result.shellsWritten} route shell(s), ${result.sitemapUrls.length} sitemap URL(s)` +
      `${result.notFoundShellWritten ? ', and gave dist/404.html its own not-found head' : ''}`,
  );
}

if (process.argv[1]?.endsWith('build-seo.mjs')) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
