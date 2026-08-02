// SPDX-License-Identifier: Apache-2.0
//
// Javadoc link-target existence gate. The `javadoc:<fqcn>` role (src/remark/javadoc.mjs) throws on
// an FQCN outside com.agentforge4j.* or a package no surface owns — but a real package with a
// MISSPELLED CLASS NAME resolves to a well-formed `pathname://` URL with nothing checking that the
// target `.html` file actually exists. Docusaurus does not catch this either: `pathname://` links
// are explicitly exempt from its broken-link checking (that is the whole reason the role uses that
// prefix — to link to the sibling, non-/docs-routed Javadoc artifact). So a dead class-level link can
// ship silently. This gate closes that gap: it parses the same live directive the role resolves
// (fence-aware, via the same AST approach as dematerialize.mjs, so documentation *about* the
// `javadoc:` syntax inside a fenced code block is correctly not checked) and asserts every resolved
// target exists in the built Javadoc surface.
//
// Requires the Javadoc surface to already be built (`npm run javadoc`) — true in CI (the composite
// action builds it before `npm run check`) and documented for local runs.

import {existsSync, readFileSync, readdirSync, statSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {unified} from 'unified';
import remarkParse from 'remark-parse';
import remarkMdx from 'remark-mdx';
import remarkFrontmatter from 'remark-frontmatter';
import {visit} from 'unist-util-visit';
import {resolveJavadocUrl, PATTERN as JAVADOC_PATTERN} from '../src/remark/javadoc.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');
const DOCS_DIR = join(MODULE_ROOT, 'docs');
const JAVADOC_BUILD_DIR = join(REPO_ROOT, 'agentforge4j-docs-javadoc', 'build-javadoc', 'next');
const DOC_EXTENSIONS = ['.md', '.mdx'];

const parser = unified().use(remarkParse).use(remarkMdx).use(remarkFrontmatter, ['yaml']);

function collectDocs(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...collectDocs(full));
    } else if (DOC_EXTENSIONS.some((ext) => entry.endsWith(ext))) {
      out.push(full);
    }
  }
  return out;
}

/** Every LIVE `javadoc:<fqcn>` inline-code tag in a document, with its source line. */
export function liveJavadocRefs(source) {
  const tree = parser.parse(source);
  const refs = [];
  visit(tree, 'inlineCode', (node) => {
    const match = JAVADOC_PATTERN.exec(node.value);
    if (match !== null) {
      refs.push({fqcn: match[1], line: node.position.start.line});
    }
  });
  return refs;
}

/** Convert a resolved `pathname:///javadoc/<version>/...` URL to the built file it should be. */
export function urlToBuildPath(url, javadocBuildDir = JAVADOC_BUILD_DIR) {
  const prefix = 'pathname:///javadoc/next/';
  if (!url.startsWith(prefix)) {
    return null; // pinned to a released version, not the live `next` surface — nothing built to check
  }
  return join(javadocBuildDir, url.slice(prefix.length));
}

function main() {
  if (!existsSync(JAVADOC_BUILD_DIR)) {
    console.error(`lint-javadoc-links: no Javadoc build at ${JAVADOC_BUILD_DIR}.`);
    console.error('Run `npm run javadoc` first (the composite action does this before `npm run check`).');
    process.exit(1);
  }

  const docs = collectDocs(DOCS_DIR);
  let checked = 0;
  let failures = 0;
  for (const file of docs) {
    const rel = file.slice(MODULE_ROOT.length + 1).split('\\').join('/');
    const source = readFileSync(file, 'utf8');
    for (const {fqcn, line} of liveJavadocRefs(source)) {
      checked += 1;
      let url;
      try {
        url = resolveJavadocUrl(fqcn).url;
      } catch (err) {
        console.error(`✗ ${rel}:${line} — ${err.message}`);
        failures += 1;
        continue;
      }
      const buildPath = urlToBuildPath(url);
      if (buildPath !== null && !existsSync(buildPath)) {
        console.error(`✗ ${rel}:${line} — javadoc:${fqcn} resolves to a URL with no built page: ${buildPath}`);
        failures += 1;
      }
    }
  }

  if (failures > 0) {
    console.error(`\nlint-javadoc-links: ${failures} dead Javadoc link(s) across ${checked} reference(s).`);
    process.exit(1);
  }
  console.log(`lint-javadoc-links: ${checked} javadoc: reference(s) across ${docs.length} page(s), all resolve to a real page.`);
}

// CLI entry. Guarded so the pure exports (liveJavadocRefs, urlToBuildPath) can be unit-tested
// without requiring a real Javadoc build.
if (process.argv[1]?.endsWith('lint-javadoc-links.mjs')) {
  main();
}
