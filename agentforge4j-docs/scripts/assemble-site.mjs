// SPDX-License-Identifier: Apache-2.0
//
// Pages artifact assembly (design §12, Phase 5c; SPA root added by the Assembler track, design
// §10/§13). GitHub Pages publishes ONE artifact per deploy (full replace), so a single deploy must
// compose every independently-built surface into one tree:
//
//   _site/
//     index.html     <- the agentforge4j.org SPA's own root (agentforge4j-web-ui/dist)
//     404.html        <- the SPA's own branded 404 (byte-identical to index.html, see copy-404.mjs)
//     assets/, brand/, robots.txt, favicon.ico, ... <- the rest of the SPA build, at the site root
//     docs/          <- the Docusaurus build (baseUrl /docs/, so build/* maps under /docs/)
//     javadoc/next/  <- the aggregate Javadoc surface (build-javadoc output)
//     javadoc/<v>/   <- a version-pinned Javadoc surface per active OR archived version (build-javadoc-versions.mjs)
//     javadoc/latest/<- the moving alias (newest stable once one exists, else mirrors next)
//     docs/archive/  <- carried-forward frozen versions (design §7; no-op until archives exist), plus
//                        static redirect stubs at each archived version's old active address
//     CNAME          <- the custom domain, ONLY when DOCS_CUSTOM_DOMAIN is set (default: omitted)
//     .nojekyll      <- disable Jekyll so files/dirs starting with _ are served
//
// The SPA build (agentforge4j-web-ui/dist) contains no docs/ or javadoc/ directory of its own, so
// copying it to the site root cannot collide with the docs/javadoc copies below regardless of copy
// order. Because javadoc is rebuilt from source on every deploy (for both active AND archived
// versions — `main()` below and build-javadoc-versions.mjs's own CLI entry both compute the same
// active-plus-archived union via the shared `javadocBuildVersions` helper) and the docs archive is
// carried forward here, a routine docs redeploy never drops /javadoc/** or /docs/archive/** — the
// additive-composition guarantee on a full-replace host. Fails closed if a required input is missing,
// or if the composed output is missing an expected entry file (verifyComposedArtifact).
//
// Run via `node scripts/assemble-site.mjs` (usually from the deploy workflow).

import {cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync} from 'node:fs';
import {basename, dirname, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import matter from 'gray-matter';
import sax from 'sax';
import {JAVADOC_VERSIONS_OUT, javadocBuildVersions} from './build-javadoc-versions.mjs';
import {ARCHIVE_ROOT} from './archive-transition.mjs';
import {resolveJavadocUrl} from '../src/remark/javadoc.mjs';
import {liveJavadocRefs} from './lint-javadoc-links.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = resolve(here, '..');
const REPO_ROOT = resolve(MODULE_ROOT, '..');

const BUILD_DIR = join(MODULE_ROOT, 'build');
const JAVADOC_DIR = join(REPO_ROOT, 'agentforge4j-docs-javadoc', 'build-javadoc', 'next');
const SPA_DIR = join(REPO_ROOT, 'agentforge4j-web-ui', 'dist');
const SITE_DIR = join(MODULE_ROOT, '_site');

// Custom-domain opt-in. A CNAME file claims the domain for this Pages site, and one domain can
// serve only one Pages site — so the artifact carries NO CNAME unless the deploy explicitly sets
// DOCS_CUSTOM_DOMAIN (e.g. `agentforge4j.org`) once the domain/publishing composition is settled.
const CUSTOM_DOMAIN = process.env.DOCS_CUSTOM_DOMAIN || null;

/** Read a version-list JSON file (versions.json / lts.json), or [] if absent — same as the config. */
function readVersionList(path) {
  return existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : [];
}

const RELEASED_VERSIONS = readVersionList(join(MODULE_ROOT, 'versions.json'));

// Every version whose Javadoc surface must be present: RELEASED_VERSIONS (active) plus any archived
// version (a directory under archive/) — see javadocBuildVersions. Kept separate from
// RELEASED_VERSIONS itself, which stays active-only and must not include archived versions: it also
// drives `latestSource` inside assembleSite (an archived version must never become the /latest alias
// target).
const ARCHIVED_VERSION_NAMES = existsSync(ARCHIVE_ROOT)
  ? readdirSync(ARCHIVE_ROOT, {withFileTypes: true})
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
  : [];
const JAVADOC_VERSIONS_TO_PUBLISH = javadocBuildVersions(RELEASED_VERSIONS, ARCHIVED_VERSION_NAMES);

function requireDir(path, what, hint) {
  if (!existsSync(path)) {
    console.error(`[assemble-site] missing ${what}: ${path}`);
    console.error(`  ${hint}`);
    process.exit(1);
  }
}

// Defense-in-depth on the *output*, not just the inputs above: catches the case where every input
// existed and every copy step "succeeded" but the composed result is still wrong for some reason
// requireDir cannot see (e.g. a future refactor that copies from the wrong source path, or a build
// step that wrote a truncated/zero-byte file without itself erroring).
function verifyComposedArtifact(siteDir, releasedVersions, exit) {
  // /javadoc/latest/ and one /javadoc/<v>/ per released version are real, separately-built copy
  // targets (steps 3 above) — a version whose Javadoc build silently produced an empty directory
  // (a real defect this exact check caught locally: a Windows-only Maven invocation failure in
  // `build-javadoc-versions.mjs` left `javadoc/0.1.0/` an empty, `existsSync`-true directory,
  // which `requireDir` cannot see as wrong) must fail the same way a missing entry does.
  for (const entry of [
    join(siteDir, 'index.html'),
    join(siteDir, 'docs', 'index.html'),
    join(siteDir, 'javadoc', 'next', 'index.html'),
    join(siteDir, 'javadoc', 'latest', 'index.html'),
    ...releasedVersions.map((version) => join(siteDir, 'javadoc', version, 'index.html')),
    join(siteDir, 'sitemap.xml'),
    join(siteDir, 'robots.txt'),
  ]) {
    if (!existsSync(entry)) {
      console.error(`[assemble-site] composed artifact is missing expected entry: ${entry}`);
      exit(1);
    }
    const stats = statSync(entry);
    if (!stats.isFile() || stats.size === 0) {
      console.error(`[assemble-site] composed artifact has an empty or non-file expected entry: ${entry}`);
      exit(1);
    }
  }
}

// Content defects that must never reach the composed public artifact. Each was a real bug found
// on the live site: exposed, unparsed admonition directive syntax (a pre-directive-syntax `:::note
// Title` form MDX3 does not recognise, so it renders as literal text instead of a callout), and raw
// Javadoc block tags leaking into generated reference prose. Checked against every composed HTML
// file, not just the authored MDX source, so the gate fails on the actual shipped defect, not a
// proxy for it.
const FORBIDDEN_HTML_PATTERNS = [
  {name: 'exposed admonition directive syntax (:::note/:::tip/:::warning/:::danger/:::info/:::caution)', pattern: /:::(?:note|tip|warning|danger|info|caution)\b/},
  {name: 'a standalone, unparsed admonition closing marker (:::)', pattern: /(?<![:\w]):::(?![:\w])/},
  {name: 'a raw Javadoc {@code} tag', pattern: /\{@code\b/},
  {name: 'a raw Javadoc {@link} tag', pattern: /\{@link\b/},
  {name: 'a raw Javadoc {@linkplain} tag', pattern: /\{@linkplain\b/},
  {name: 'a /docs/javadoc/ link (the composed artifact mounts Javadoc at /javadoc/, not /docs/javadoc/)', pattern: /(?:href|src)="\/docs\/javadoc\//},
  {name: 'stale "generator is wired in a later phase" placeholder copy', pattern: /wired in a later phase/i},
];

function collectHtmlFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...collectHtmlFiles(full));
    } else if (entry.name.endsWith('.html')) {
      out.push(full);
    }
  }
  return out;
}

/**
 * Scan every HTML file in the composed artifact for the forbidden content patterns above. Runs
 * against the final composed output, the same defense-in-depth philosophy as
 * `verifyComposedArtifact`: a page can be individually well-formed in its own module's build and
 * still ship a real defect once actually composed (as the admonition and Javadoc-tag bugs did).
 */
export function scanComposedHtmlForForbiddenContent(siteDir, exit = process.exit) {
  const files = collectHtmlFiles(siteDir);
  let violations = 0;
  for (const file of files) {
    const html = readFileSync(file, 'utf8');
    for (const {name, pattern} of FORBIDDEN_HTML_PATTERNS) {
      if (pattern.test(html)) {
        console.error(`[assemble-site] composed artifact contains ${name}: ${file}`);
        violations += 1;
      }
    }
  }
  if (violations > 0) {
    console.error(`[assemble-site] ${violations} forbidden-content violation(s) across ${files.length} composed HTML file(s).`);
    exit(1);
    return;
  }
  console.log(`[assemble-site] scanned ${files.length} composed HTML file(s) for forbidden content — clean.`);
}

const DOC_EXTENSIONS = ['.md', '.mdx'];

function collectDocFiles(dir) {
  if (!dir || !existsSync(dir)) {
    return [];
  }
  const out = [];
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...collectDocFiles(full));
    } else if (DOC_EXTENSIONS.some((ext) => entry.name.endsWith(ext))) {
      out.push(full);
    }
  }
  return out;
}

/** `versioned_docs/version-0.1.0/...` -> `'0.1.0'`; anything under the live `docs/` tree -> `null`
 * (meaning the moving `next` surface), matching `javadocRemarkPlugin`'s own default. */
function pinnedVersionOf(versionedDocsSourceDir, file) {
  if (!file.startsWith(versionedDocsSourceDir)) {
    return null;
  }
  const rel = file.slice(versionedDocsSourceDir.length + 1);
  const match = /^version-([^/\\]+)[/\\]/.exec(rel.split('\\').join('/'));
  return match ? match[1] : null;
}

/**
 * Re-resolves every live `javadoc:<fqcn>` reference across both the live docs source and every
 * versioned snapshot, then asserts the target it resolves to actually exists in THIS composed
 * artifact (not just the raw `agentforge4j-docs-javadoc` build output `lint-javadoc-links.mjs`
 * checks against, and not just the live `docs/` tree that gate scans — a version-pinned reference
 * inside `versioned_docs/` resolves to `/javadoc/<version>/...`, which only the composed artifact,
 * with every version's Javadoc surface actually copied in, can prove exists).
 */
/** True if `html` contains a real element with this exact anchor id — used to verify a URL fragment
 *  resolves to something real on the target page, not just that the page itself exists. Matches
 *  `id="anchor"`, `id='anchor'`, AND the unquoted `id=anchor` form: confirmed against a real
 *  production Docusaurus build that its minifier drops attribute quotes whenever the value is a
 *  safe unquoted-HTML5 token (which every heading-slug id always is) — a naive quoted-only check
 *  would false-positive-fail on every real page in a minified build. */
function htmlHasAnchorId(html, anchor) {
  const escaped = anchor.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`id=["']?${escaped}(?=["'\\s/>])`).test(html);
}

export function verifyComposedJavadocLinks(siteDir, docsSourceDir, versionedDocsSourceDir, exit = process.exit) {
  const files = [...collectDocFiles(docsSourceDir), ...collectDocFiles(versionedDocsSourceDir)];
  let checked = 0;
  let failures = 0;
  for (const file of files) {
    const version = pinnedVersionOf(versionedDocsSourceDir, file) || 'next';
    const source = readFileSync(file, 'utf8');
    for (const {fqcn, line} of liveJavadocRefs(source)) {
      checked += 1;
      let resolved;
      try {
        resolved = resolveJavadocUrl(fqcn, version);
      } catch (err) {
        console.error(`[assemble-site] ${file}:${line} — ${err.message}`);
        failures += 1;
        continue;
      }
      // The role never emits a fragment today, but resolve one correctly if it ever does — a
      // fragment must resolve to a real anchor on the target page, not just an existing file.
      const [urlNoFragment, fragment] = resolved.url.split('#');
      const prefix = 'pathname:///';
      const relTarget = urlNoFragment.startsWith(prefix) ? urlNoFragment.slice(prefix.length) : urlNoFragment;
      const targetPath = join(siteDir, ...relTarget.split('/'));
      if (!existsSync(targetPath)) {
        console.error(
          `[assemble-site] ${file}:${line} — javadoc:${fqcn} resolves to a target missing from the composed artifact: ${targetPath}`,
        );
        failures += 1;
        continue;
      }
      if (fragment && !htmlHasAnchorId(readFileSync(targetPath, 'utf8'), fragment)) {
        console.error(
          `[assemble-site] ${file}:${line} — javadoc:${fqcn} resolves to ${targetPath}, but anchor '#${fragment}' does not exist there`,
        );
        failures += 1;
      }
    }
  }
  if (failures > 0) {
    console.error(`[assemble-site] ${failures} dead javadoc: reference(s) against the composed artifact, out of ${checked} checked.`);
    exit(1);
    return;
  }
  console.log(`[assemble-site] verified ${checked} javadoc: reference(s) against the composed artifact — all present.`);
}

/** Reads a doc file's frontmatter `id` (via gray-matter), or `null` if unset. */
function frontmatterId(filePath) {
  const {data} = matter(readFileSync(filePath, 'utf8'));
  return typeof data.id === 'string' ? data.id : null;
}

/**
 * The composed HTML page a doc source file builds to, e.g.
 * `docs/reference/schemas/workflow.mdx` -> `<siteDir>/docs/next/reference/schemas/reference-schema-workflow/index.html`.
 *
 * Empirically grounded against a real build of this exact site (not assumed): Docusaurus uses an
 * explicit frontmatter `id` as the route's LAST segment, replacing the file's own basename, while
 * the containing directory still follows the file's location on disk; `index.md(x)` maps to its own
 * directory regardless of `id`; a page with no explicit `id` uses its file path unchanged. This
 * mirrors every one of `generate-references.mjs`'s own generated pages (each sets an explicit,
 * globally-unique `id: reference-<kind>-<name>`) and every hand-authored page (none set `id`).
 */
function composedHtmlPathFor(fileAbsPath, docsRootAbsPath, siteDir, versionSegment) {
  const relFromRoot = relative(docsRootAbsPath, fileAbsPath).split(sep).join('/');
  const relDir = dirname(relFromRoot) === '.' ? '' : dirname(relFromRoot);
  const base = basename(fileAbsPath).replace(/\.mdx?$/, '');
  const slugSegments = base === 'index' ? [] : [frontmatterId(fileAbsPath) || base];
  return join(siteDir, 'docs', versionSegment, ...relDir.split('/').filter(Boolean), ...slugSegments, 'index.html');
}

// Matches an in-repo relative markdown link carrying a URL fragment: either a same-page anchor
// `(#anchor)`, or a relative-file-plus-anchor `(./file.mdx#anchor)` / `(../dir/file.mdx#anchor)`.
// Deliberately excludes absolute/external links (http://, pathname://, mailto:) — those are handled
// by the javadoc-specific checker above, or are genuinely external and out of scope here.
const ANCHOR_LINK_PATTERN = /\]\((\.{1,2}\/[.\w/-]+\.mdx)?#([a-zA-Z0-9_-]+)\)/g;

/**
 * Verifies every in-repo relative markdown link carrying a URL fragment (the schema/config
 * reference cross-links `generate-references.mjs` emits, e.g. `[StepDefinition](./workflow.mdx#stepdefinition)`)
 * resolves to a real anchor on the real composed page — not just that the target FILE exists (a
 * page can exist and still not carry the specific heading id a stale cross-link expects, exactly
 * the class of defect a same-file-only `refName()` produced before it was fixed to be cross-file-
 * and self-`$ref`-aware).
 */
export function verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, exit = process.exit) {
  const files = [...collectDocFiles(docsSourceDir), ...collectDocFiles(versionedDocsSourceDir)];
  let checked = 0;
  let failures = 0;
  for (const file of files) {
    const version = pinnedVersionOf(versionedDocsSourceDir, file) || 'next';
    const docsRoot = version === 'next' ? docsSourceDir : join(versionedDocsSourceDir, `version-${version}`);
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(ANCHOR_LINK_PATTERN)) {
      const [, relFilePart, anchor] = match;
      checked += 1;
      const targetFile = relFilePart ? resolve(dirname(file), relFilePart) : file;
      if (!existsSync(targetFile)) {
        console.error(`[assemble-site] ${file} — anchor link to '#${anchor}' targets a source file that does not exist: ${targetFile}`);
        failures += 1;
        continue;
      }
      const htmlPath = composedHtmlPathFor(targetFile, docsRoot, siteDir, version);
      if (!existsSync(htmlPath)) {
        console.error(`[assemble-site] ${file} — anchor link target page missing from the composed artifact: ${htmlPath}`);
        failures += 1;
        continue;
      }
      if (!htmlHasAnchorId(readFileSync(htmlPath, 'utf8'), anchor)) {
        console.error(`[assemble-site] ${file} — anchor '#${anchor}' not found on the composed page: ${htmlPath}`);
        failures += 1;
      }
    }
  }
  if (failures > 0) {
    console.error(`[assemble-site] ${failures} broken anchor link(s) against the composed artifact, out of ${checked} checked.`);
    exit(1);
    return;
  }
  console.log(`[assemble-site] verified ${checked} in-page anchor link(s) against the composed artifact — all present.`);
}

const SITEMAP_URL_PREFIX = 'https://agentforge4j.org/';

/** Extracts every `{url, lastmod}` pair from a sitemap.xml file, in document order (`lastmod` is
 * `null` when a `<url>` block has none). Uses `sax` (a real, standards-based streaming XML parser
 * — already present in this package's own dependency graph via `@docusaurus/plugin-sitemap`'s
 * `sitemap` package, see package.json for why it is now a direct devDependency here too) in strict
 * mode, rather than a regex/tag-count heuristic: a prior regex-plus-count-comparison approach could
 * not distinguish "zero matches because nothing is here" from "zero matches because a `<url>` is
 * self-closing/empty" — both produced the identical zero-vs-zero-vs-zero count agreement, so a
 * malformed self-closing `<url/>` (and other unusual-but-technically-well-formed shapes) silently
 * disappeared from the merged sitemap instead of failing the build. A real parser distinguishes
 * these unambiguously by construction, rather than by adding another special-case count.
 *
 * Fails closed (via `exit`, not silently) whenever:
 *  - the XML itself is not well-formed (sax's own strict-mode error reporting: unclosed tags,
 *    mismatched/orphan closing tags, invalid markup elsewhere in the document — even after an
 *    earlier, individually well-formed `<url>` entry was already parsed; a document that goes bad
 *    partway through must still fail the whole file, not silently publish only the entries seen
 *    before the corruption);
 *  - the document's outermost element is not `<urlset>`;
 *  - a `<url>` entry has no `<loc>` at all, or an empty one (this is also what makes a self-closing
 *    `<url/>` fail: it opens and closes with no children, so it can never have a `<loc>`);
 *  - a `<url>` entry has more than one `<loc>`, or more than one `<lastmod>`;
 *  - a `<url>` entry has any child element other than `<loc>`/`<lastmod>` (a sibling to them), or
 *    `<loc>`/`<lastmod>` themselves contain a nested element (rather than plain text) — either
 *    shape means the entry is not the narrow, deliberately-accepted structure this function
 *    contracts to.
 * The relative order of `<loc>` and `<lastmod>` within a `<url>` entry is NOT significant — both
 * are identified by tag name, not position, so accepting either order is a deliberate, tested
 * choice, not an oversight. A structurally valid `<urlset>` with zero `<url>` children is valid and
 * contributes zero entries — it is not itself a malformed-input case. */
function extractSitemapEntries(xmlPath, exit) {
  const xml = readFileSync(xmlPath, 'utf8');
  const entries = [];

  let failed = false;
  function fail(message) {
    if (failed) {
      return;
    }
    failed = true;
    console.error(`[assemble-site] ${xmlPath}: ${message}`);
    exit(1);
  }

  const parser = sax.parser(true);
  let sawRoot = false;
  let currentEntry = null; // {loc: string|null, lastmod: string|null} while inside a <url> element
  let currentChildTag = null; // 'loc' | 'lastmod' | null — the currently-open leaf element inside <url>
  let currentText = '';

  function localName(name) {
    return name.includes(':') ? name.slice(name.lastIndexOf(':') + 1) : name;
  }

  parser.onerror = (err) => {
    fail(`sitemap XML is not well-formed — ${err.message.split('\n')[0]}`);
  };

  parser.onopentag = (node) => {
    const name = localName(node.name);
    if (!sawRoot) {
      sawRoot = true;
      if (name !== 'urlset') {
        fail(`expected <urlset> as the document root, found <${node.name}>`);
        return;
      }
    }
    if (name === 'urlset') {
      return;
    }
    if (currentChildTag !== null) {
      // Already inside a <loc>/<lastmod> leaf element — those must contain plain text only.
      fail(`<${currentChildTag}> contains a nested element (<${node.name}>) instead of plain text`);
      return;
    }
    if (name === 'url') {
      if (currentEntry !== null) {
        fail('nested <url> element');
        return;
      }
      currentEntry = {loc: null, lastmod: null};
      return;
    }
    if (currentEntry === null) {
      // A stray element at the top level, alongside <urlset>'s own children, that is neither
      // <urlset> nor <url> — not part of the accepted contract either.
      fail(`unexpected top-level element <${node.name}>`);
      return;
    }
    if (name !== 'loc' && name !== 'lastmod') {
      fail(`unexpected child element <${node.name}> inside <url>`);
      return;
    }
    if (currentEntry[name] !== null) {
      fail(`multiple <${name}> elements inside one <url> entry`);
      return;
    }
    currentChildTag = name;
    currentText = '';
  };

  parser.ontext = (text) => {
    if (currentChildTag !== null) {
      currentText += text;
    }
  };
  parser.oncdata = parser.ontext;

  parser.onclosetag = (rawName) => {
    const name = localName(rawName);
    if (name === 'loc' || name === 'lastmod') {
      if (currentChildTag === name) {
        currentEntry[name] = currentText;
        currentChildTag = null;
        currentText = '';
      }
      return;
    }
    if (name === 'url') {
      if (currentEntry === null) {
        // sax's own strict-mode well-formedness check already reports an orphan/mismatched closing
        // tag as a parse error before this could be reached in practice; guarded defensively anyway.
        return;
      }
      if (!currentEntry.loc) {
        fail('a <url> entry has no <loc> (or it is present but empty)');
        currentEntry = null;
        return;
      }
      entries.push({url: currentEntry.loc, lastmod: currentEntry.lastmod ?? null});
      currentEntry = null;
    }
  };

  try {
    parser.write(xml).close();
  } catch (err) {
    // sax's own strict-mode well-formedness errors (mismatched/orphan/unclosed tags) throw
    // synchronously from write()/close() in addition to firing onerror first. If onerror (or one of
    // the semantic checks above, e.g. multiple <loc>) already called fail() — which itself calls
    // `exit`, and `exit` may itself throw to unwind the stack (every caller in this codebase's own
    // test suite does exactly that, mirroring process.exit's real termination) — that thrown error
    // must keep propagating, not be swallowed here: re-throw it. Only genuinely call fail() fresh
    // in the (believed unreachable, based on direct testing of every malformed shape this function
    // is contracted to reject) case where something threw before onerror/a semantic check ever ran.
    if (!failed) {
      fail('sitemap XML is not well-formed');
    } else {
      throw err;
    }
  }
  if (!sawRoot) {
    fail('missing <urlset> root element — the file is empty or not XML at all');
  }

  return entries;
}

// The one round-trip hazard a real XML parser introduces that a raw-regex extractor never had:
// `sax` correctly decodes standard XML entities in text content (e.g. `&amp;` -> `&`), so a `<loc>`
// value read back out of `extractSitemapEntries` is the real, decoded URL string, not its escaped
// XML representation. Serializing it back verbatim would emit a literal, unescaped `&` — invalid
// XML the moment any merged URL ever contains one (this site's own routes don't today, but nothing
// about this module's contract limits it to hosts that never will). `&` is the only character that
// needs escaping here: a real URL cannot contain a literal `<`, `>`, or `"` unencoded (RFC 3986)
// the way it legitimately can contain `&` in a query string.
function escapeAmpersand(value) {
  return value.replace(/&/g, '&amp;');
}

function sitemapXml(entries) {
  const body = entries
    .map(({ url, lastmod }) => {
      const lastmodTag = lastmod ? `\n    <lastmod>${escapeAmpersand(lastmod)}</lastmod>` : '';
      return `  <url>\n    <loc>${escapeAmpersand(url)}</loc>${lastmodTag}\n  </url>`;
    })
    .join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    `${body}\n</urlset>\n`
  );
}

/**
 * Merges the SPA's own sitemap.xml fragment (agentforge4j-web-ui/scripts/build-seo.mjs, already
 * copied to the site root in step 1) with the Docusaurus-generated docs/sitemap.xml (already
 * copied to `docs/` in step 2) into the one final sitemap.xml the composed artifact serves at
 * `/sitemap.xml`. Fails closed on a missing fragment, a non-HTTPS/wrong-domain URL (a
 * misconfigured `siteConfig.url` would otherwise silently publish the wrong host), or a
 * duplicate URL across the two fragments.
 */
function mergeSitemaps(siteDir, exit) {
  const spaSitemapPath = join(siteDir, 'sitemap.xml');
  const docsSitemapPath = join(siteDir, 'docs', 'sitemap.xml');
  requireDir(spaSitemapPath, 'SPA sitemap fragment', 'Run `npm run build` in agentforge4j-web-ui first.');
  requireDir(
    docsSitemapPath,
    'Docusaurus sitemap fragment',
    'Run `npm run build` in agentforge4j-docs first (the sitemap plugin runs in postBuild).',
  );

  const entries = [...extractSitemapEntries(spaSitemapPath, exit), ...extractSitemapEntries(docsSitemapPath, exit)];

  for (const { url } of entries) {
    if (!url.startsWith(SITEMAP_URL_PREFIX)) {
      console.error(`[assemble-site] refusing a sitemap URL outside ${SITEMAP_URL_PREFIX}: ${url}`);
      exit(1);
    }
  }

  const seen = new Set();
  for (const { url } of entries) {
    if (seen.has(url)) {
      console.error(`[assemble-site] duplicate sitemap URL across the SPA and docs fragments: ${url}`);
      exit(1);
    }
    seen.add(url);
  }

  writeFileSync(join(siteDir, 'sitemap.xml'), sitemapXml(entries), 'utf8');
  console.log(`[assemble-site] merged sitemap.xml: ${entries.length} URL(s) (SPA + docs)`);
}

/** A static meta-refresh redirect page. */
function redirectHtml(to) {
  return (
    `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">` +
    `<meta http-equiv="refresh" content="0; url=${to}">` +
    `<link rel="canonical" href="${to}"><title>AgentForge4j</title></head>` +
    `<body><a href="${to}">Continue to the documentation</a></body></html>\n`
  );
}

// A manifest entry is trusted to become a filesystem path segment, so it is held to the same
// fail-closed path-safety standard as every other version/path input in these scripts
// (release-paths.mjs's `validateVersion`): rooted at /docs/, no `..` traversal segment. The only
// production writer (redirectManifest, from a validateVersion'd version + real page routes) already
// satisfies this — the check guards `archive/*.redirects.json` being a plain committed JSON file a
// future hand-edit or merge-conflict resolution could otherwise corrupt undetected.
function isSafeManifestPath(path) {
  return (
    typeof path === 'string' &&
    path.startsWith('/docs/') &&
    !path.includes('\\') &&
    !path.split('/').includes('..')
  );
}

/**
 * Publish an archived version's redirect manifest as static stub pages: every page route of the
 * version's old active address (`/docs/<v>/...`) permanently forwards to its archive mount
 * (`/docs/archive/<v>/...`). Written AFTER the docs copy so a stub can never be overwritten by it.
 *
 * @param {(code: number) => void} [exit] injectable seam for the fail-closed collision guard
 *        (tests; default `process.exit`), mirroring the `builder` seam on `buildJavadocVersions`.
 */
function writeRedirectStubs(siteDir, manifestPath, exit = process.exit) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  for (const {from, to} of manifest) {
    if (!isSafeManifestPath(from) || !isSafeManifestPath(to)) {
      console.error(`[assemble-site] refusing an unsafe redirect manifest entry: ${JSON.stringify({from, to})}`);
      console.error('  Both `from` and `to` must be rooted at /docs/ with no `..` segments.');
      exit(1);
    }
    const dir = join(siteDir, ...from.split('/').filter(Boolean));
    const stub = join(dir, 'index.html');
    // A stub must never shadow a live page. The transition removes the version from versions.json
    // in the same run that freezes the artifact, so its old routes cannot exist in the live build;
    // if one does, archive/ and versions.json are inconsistent — stop rather than publish a site
    // where a redirect silently replaced real content.
    if (existsSync(stub)) {
      console.error(`[assemble-site] redirect stub would overwrite a live page: ${from}`);
      console.error('  The archived version is still part of the live build — archive/ and versions.json disagree.');
      exit(1);
    }
    mkdirSync(dir, {recursive: true});
    writeFileSync(stub, redirectHtml(`${to}/`), 'utf8');
  }
  return manifest.length;
}

/**
 * Assemble the Pages artifact into `siteDir` from the given inputs. Pure with respect to module
 * location (every path is a parameter), so it is directly unit-testable against fixture
 * directories; `main()` below is the real CLI entry, computing the live paths.
 *
 * @param {{spaDir: string, buildDir: string, javadocDir: string, javadocVersionsDir?: string,
 *          releasedVersions?: string[], archiveDir: string, siteDir: string,
 *          docsSourceDir?: string, versionedDocsSourceDir?: string,
 *          customDomain: string|null,
 *          exit?: (code: number) => void}} options `exit` is an injectable seam for the
 *        redirect-stub collision guard and the composed-output verification (tests; default
 *        `process.exit`). `docsSourceDir`/`versionedDocsSourceDir` are undefined by default (the
 *        composed-Javadoc-link check is then a no-op over zero files) — `main()` below passes this
 *        module's real `docs/`/`versioned_docs/`; fixture-based tests need not supply them.
 */
export function assembleSite({
  spaDir,
  buildDir,
  javadocDir,
  javadocVersionsDir,
  releasedVersions = [],
  archiveDir,
  siteDir,
  docsSourceDir,
  versionedDocsSourceDir,
  customDomain,
  exit = process.exit,
}) {
  requireDir(spaDir, 'SPA build', 'Run `npm run build` in agentforge4j-web-ui first.');
  requireDir(buildDir, 'Docusaurus build', 'Run `npm run build` first.');
  requireDir(javadocDir, 'Javadoc surface', 'Run `npm run javadoc` first.');

  rmSync(siteDir, {recursive: true, force: true});
  mkdirSync(siteDir, {recursive: true});

  // 1. The SPA at the site root (index.html, 404.html, assets/, brand/, robots.txt, favicon.ico,
  //    etc.). The SPA owns the root; everything else below is additive under it — placed first so
  //    that reading order matches that intent, though the non-overlapping subtrees (see the header
  //    comment) mean the actual copy order does not affect correctness.
  cpSync(spaDir, siteDir, {recursive: true});

  // 2. Docs at /docs/ (the Docusaurus build already prefixes every route with baseUrl /docs/).
  cpSync(buildDir, join(siteDir, 'docs'), {recursive: true});

  // 3. Javadoc at /javadoc/next/, one version-pinned surface per entry in `releasedVersions` (the
  //    frozen docs snapshots link these), and the moving /javadoc/latest/ alias: the newest stable's
  //    surface once one exists, else mirrors next. `main()` below passes the union of active AND
  //    archived versions here (via build-javadoc-versions.mjs's `javadocBuildVersions`) so an
  //    archived version's Javadoc is carried forward exactly like its docs archive is in step 4 —
  //    kept as a caller-supplied list (not re-derived from `archiveDir` in here) so this function
  //    stays independently testable against fixtures that don't care about Javadoc at all.
  cpSync(javadocDir, join(siteDir, 'javadoc', 'next'), {recursive: true});
  const archiveEntries = existsSync(archiveDir) ? readdirSync(archiveDir, {withFileTypes: true}) : [];
  for (const version of releasedVersions) {
    const src = join(javadocVersionsDir, version);
    requireDir(
      src,
      `version-pinned Javadoc surface for ${version}`,
      'Run `npm run javadoc:versions` first — the frozen docs snapshot links /javadoc/' + version + '/.',
    );
    cpSync(src, join(siteDir, 'javadoc', version), {recursive: true});
  }
  const latestSource = releasedVersions.length > 0 ? join(javadocVersionsDir, releasedVersions[0]) : javadocDir;
  cpSync(latestSource, join(siteDir, 'javadoc', 'latest'), {recursive: true});

  // 4. Carry archived versions forward so a redeploy never drops them (no-op until they exist): each
  //    frozen artifact mounts at /docs/archive/<v>/, and its redirect manifest (if present) is
  //    published as static stubs at the version's old active address (design §7 — links never die).
  if (existsSync(archiveDir)) {
    let archived = 0;
    let stubs = 0;
    for (const entry of archiveEntries) {
      if (entry.isDirectory()) {
        cpSync(join(archiveDir, entry.name), join(siteDir, 'docs', 'archive', entry.name), {recursive: true});
        archived += 1;
      } else if (entry.name.endsWith('.redirects.json')) {
        stubs += writeRedirectStubs(siteDir, join(archiveDir, entry.name), exit);
      }
    }
    console.log(`[assemble-site] carried forward ${archived} archived version(s), ${stubs} redirect stub(s)`);
  }

  // 5. Custom domain (opt-in only) and Jekyll opt-out. The site root itself (index.html/404.html)
  //    is already the SPA's own, copied in step 1 — no separate redirect file is written here.
  if (customDomain) {
    writeFileSync(join(siteDir, 'CNAME'), `${customDomain}\n`, 'utf8');
    console.log(`[assemble-site] custom domain opted in: CNAME ${customDomain}`);
  } else {
    console.log('[assemble-site] no custom domain configured — CNAME omitted (set DOCS_CUSTOM_DOMAIN to opt in)');
  }
  writeFileSync(join(siteDir, '.nojekyll'), '', 'utf8');

  // 6. Merge the SPA's own sitemap.xml fragment (copied to the site root in step 1) with the
  //    Docusaurus-generated docs/sitemap.xml (copied in step 2) into the one final sitemap.xml
  //    the composed artifact serves at /sitemap.xml.
  mergeSitemaps(siteDir, exit);

  verifyComposedArtifact(siteDir, releasedVersions, exit);
  scanComposedHtmlForForbiddenContent(siteDir, exit);
  verifyComposedJavadocLinks(siteDir, docsSourceDir, versionedDocsSourceDir, exit);
  verifyComposedAnchorLinks(siteDir, docsSourceDir, versionedDocsSourceDir, exit);

  console.log(`[assemble-site] composed ${siteDir}: SPA root, /docs, /javadoc/{next,latest}`);
}

function main() {
  assembleSite({
    spaDir: SPA_DIR,
    buildDir: BUILD_DIR,
    javadocDir: JAVADOC_DIR,
    javadocVersionsDir: JAVADOC_VERSIONS_OUT,
    releasedVersions: JAVADOC_VERSIONS_TO_PUBLISH,
    archiveDir: ARCHIVE_ROOT,
    siteDir: SITE_DIR,
    docsSourceDir: join(MODULE_ROOT, 'docs'),
    versionedDocsSourceDir: join(MODULE_ROOT, 'versioned_docs'),
    customDomain: CUSTOM_DOMAIN,
  });
}

// CLI entry. Guarded so assembleSite() can be unit-tested against fixture directories without
// requiring a real Docusaurus build / Javadoc surface.
if (process.argv[1]?.endsWith('assemble-site.mjs')) {
  main();
}
