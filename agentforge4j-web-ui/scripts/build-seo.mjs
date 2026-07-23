// SPDX-License-Identifier: Apache-2.0
//
// Generates two things from the already-built dist/index.html (run after `vite build`, before
// copy-404.mjs so 404.html stays byte-identical to the *final* index.html):
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
//         not merely a client-side runtime concern), and this file plus prerender-routes.mjs
//         themselves (the code that decides what "the rendered page" even means). A change to any
//         of these can alter every published page's actual HTML in ways no per-route `sourceFiles`
//         list could ever capture, so all are treated as applying to literally every route.
//       - `globalSourceFiles` (seo-routes.json's top level) — the actual React render surface shared
//         by every page: App.tsx (the root shell + <Routes> composition), appRoutes.ts (the
//         path -> component REGISTRY App.tsx renders from — swapping which component a path maps to
//         changes that route's entire rendered output without touching App.tsx's own text), plus the
//         header/footer/nav shell every page wraps in.
//       - a static route's own `sourceFiles` (its page component), plus its own metadata dependency
//         — the newest commit that touched *that route's own entry* in seo-routes.json, not the
//         whole file (see gitLastModifiedDateForRouteMetadata) — so one route's metadata edit never
//         bumps another route's <lastmod>.
//       - `catalogueSourceFiles` (seo-routes.json's top level) — rendering shared by every catalogue
//         detail page (CatalogueDetailPage.tsx, catalogueSeo.ts, renderWorkflowSvg.ts,
//         copy/catalogue.ts) — never applied to static routes.
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
// Per-workflow title/description formatting mirrors src/lib/catalogueSeo.ts (used by the
// client-side title/meta sync, usePageSeo) — duplicated deliberately, not imported, because this
// is plain Node ESM with no bundler step ahead of it; kept to two small, easy-to-eyeball rules.

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, isAbsolute, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WORKFLOW_ID_PATTERN } from './workflow-id-contract.mjs';
import { prerenderRoutes } from './prerender-routes.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');

const DIST_DIR = join(MODULE_ROOT, 'dist');
const SEO_ROUTES_PATH = join(MODULE_ROOT, 'src', 'config', 'seo-routes.json');
const CATALOGUE_DATA_PATH = join(MODULE_ROOT, 'src', 'generated', 'catalogue-data.json');

const MAX_DESCRIPTION_LENGTH = 157; // mirrors src/lib/catalogueSeo.ts

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

function catalogueWorkflowDescription(workflow) {
  const raw = workflow.description?.trim();
  if (!raw) {
    return `${workflow.name} — a shipped, ready-to-run AgentForge4j workflow from the workflow catalogue.`;
  }
  if (raw.length <= MAX_DESCRIPTION_LENGTH) {
    return raw;
  }
  return `${raw.slice(0, MAX_DESCRIPTION_LENGTH - 1).trimEnd()}…`;
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
    [/<meta\s+property="og:title"[\s\S]*?\/>/, `<meta property="og:title" content="${safeTitle}" />`],
    [
      /<meta\s+property="og:description"[\s\S]*?\/>/,
      `<meta property="og:description" content="${safeDescription}" />`,
    ],
    [/<meta\s+property="og:url"[\s\S]*?\/>/, `<meta property="og:url" content="${safeCanonical}" />`],
    [/<meta\s+name="twitter:title"[\s\S]*?\/>/, `<meta name="twitter:title" content="${safeTitle}" />`],
    [
      /<meta\s+name="twitter:description"[\s\S]*?\/>/,
      `<meta name="twitter:description" content="${safeDescription}" />`,
    ],
  ];

  let result = html;
  for (const [pattern, replacement] of replacements) {
    if (!pattern.test(result)) {
      throw new Error(`build-seo: expected tag not found in dist/index.html: ${pattern}`);
    }
    result = result.replace(pattern, replacement);
  }
  return result;
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
  return html.replace(EMPTY_ROOT_PATTERN, `<div id="root">${innerHtml}</div>`);
}

/** Inserts a route's JSON-LD structured-data block right before `</head>` — an addition, not a
 * replacement (unlike injectHead's tags, no shell starts with one), so only routes that declare a
 * `jsonLd` object in seo-routes.json (today: only "/") get a `<script type="application/ld+json">`
 * at all; every other shell is unaffected. A no-op when `jsonLd` is undefined.
 *
 * Every `<` in the serialized JSON is escaped to `<` before it reaches the HTML — `<` is the
 * only character that matters inside a `<script>` body (an HTML parser looks for `</script` byte-
 * for-byte, case-insensitively, regardless of JSON string-quoting), so an unescaped value
 * containing a literal `</script>` would close the tag early and let whatever followed run as live
 * markup/script. `<` is a standard JSON string escape — `JSON.parse` (or any JSON-LD consumer)
 * reads it back as the exact same `<` character, so this changes zero JSON semantics; it is not a
 * general HTML-escaping pass (`>`, `&`, quotes, etc. are untouched and do not need to be — none of
 * them can end a `<script>` body). */
export function injectJsonLd(html, jsonLd) {
  if (jsonLd === undefined || jsonLd === null) {
    return html;
  }
  const serialized = JSON.stringify(jsonLd).replace(/</g, '\\u003c');
  const script = `<script type="application/ld+json">${serialized}</script>\n  </head>`;
  if (!/<\/head>/.test(html)) {
    throw new Error('build-seo: expected a </head> closing tag in dist/index.html');
  }
  return html.replace(/<\/head>/, script);
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
 * Schema/its Java version source: traced during this design, both gate what data is *valid*, but
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

  assertDependencyFilesExist(repoRoot, artifactGenerationSourceFiles, 'artifactGenerationSourceFiles');
  assertDependencyFilesExist(repoRoot, globalSourceFiles, 'globalSourceFiles');
  assertDependencyFilesExist(repoRoot, catalogueSourceFiles, 'catalogueSourceFiles');
  assertDependencyFilesExist(repoRoot, aggregateCatalogueSourceFiles, 'aggregateCatalogueSourceFiles');
  for (const route of routes) {
    assertDependencyFilesExist(repoRoot, route.sourceFiles ?? [], `route "${route.path}"'s sourceFiles`);
  }

  const sitemapEntries = [];
  let shellsWritten = 0;

  for (const route of routes) {
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
          newestGitLastModifiedDate(repoRoot, [...everyGlobalSourceFile, ...(route.sourceFiles ?? []), ...aggregateDeps]),
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
      lastmod: newestGitLastModifiedDate(repoRoot, [
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

  return { shellsWritten, sitemapUrls: uniqueUrls };
}

async function main() {
  const snapshots = await prerenderRoutes({ distDir: DIST_DIR });
  const result = buildSeo({ snapshots });
  console.log(
    `[build-seo] wrote ${result.shellsWritten} route shell(s), ${result.sitemapUrls.length} sitemap URL(s)`,
  );
}

if (process.argv[1]?.endsWith('build-seo.mjs')) {
  main();
}
