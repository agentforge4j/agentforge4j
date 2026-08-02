// SPDX-License-Identifier: Apache-2.0
//
// Release staging — step 1 of cutting a versioned docs snapshot.
//
// Produces `.release-staging/docs/`: a full copy of the current `docs/` tree in which every
// `file=`/`vocab:`/`javadoc:` directive has been de-materialised into static content pinned to the
// target version (see dematerialize.mjs). The editable `docs/` (`next`) is never touched.
//
// Precondition: the generated reference pages and vocabulary sets must already exist (`npm run
// generate`), exactly as `docusaurus build` requires — this script does not run the (OSS-dependent)
// generate step itself, and it fails fast rather than cut a snapshot with unresolved references.
//
// Run via `node scripts/release-stage.mjs <version>` (usually invoked by release-cut / scratch-cut).

import {cpSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {dematerialize} from './dematerialize.mjs';
import {loadSets, DEFAULT_VOCAB_DIR} from '../src/remark/vocab.mjs';
import {includeAllowBases, DEFAULT_REPO_ROOT} from '../src/remark/include.mjs';
import {
  DOCS_DIR,
  STAGING_ROOT,
  STAGED_DOCS,
  listFiles,
  pathExists,
  validateVersion,
} from './release-paths.mjs';

const REFERENCE_DIR = join(DOCS_DIR, 'reference');

/**
 * Stage a materialised copy of `docs/` for `version` into `.release-staging/docs/`.
 *
 * @param {string} version the target snapshot version (Javadoc links are pinned to it)
 * @returns {string} the path to the staged docs tree
 */
export function stage(version) {
  validateVersion(version);
  // Fail closed if the tree has not been generated — a snapshot must be complete, not partial.
  if (!pathExists(REFERENCE_DIR) || !pathExists(DEFAULT_VOCAB_DIR)) {
    throw new Error(
      'release-stage: generated references/vocabulary are missing. Run `npm run generate` before staging.',
    );
  }

  rmSync(STAGED_DOCS, {recursive: true, force: true});
  mkdirSync(STAGING_ROOT, {recursive: true});
  cpSync(DOCS_DIR, STAGED_DOCS, {recursive: true});

  // Resolve the shared de-materialisation context once (vocab sets, include allowlist) — the source
  // is read from the real repo/vocab, freezing the current release's content into the snapshot.
  const vocabSets = loadSets(DEFAULT_VOCAB_DIR);
  const allowBases = includeAllowBases(DEFAULT_REPO_ROOT);

  let count = 0;
  for (const rel of listFiles(STAGED_DOCS)) {
    if (!rel.endsWith('.mdx') && !rel.endsWith('.md')) {
      continue;
    }
    const path = join(STAGED_DOCS, rel);
    const source = readFileSync(path, 'utf8');
    const out = dematerialize(source, {
      version,
      repoRoot: DEFAULT_REPO_ROOT,
      allowBases,
      vocabSets,
      docLabel: rel,
    });
    if (out !== source) {
      writeFileSync(path, out, 'utf8');
      count += 1;
    }
  }
  console.log(`[release-stage] staged docs for '${version}' at ${STAGED_DOCS} (${count} page(s) de-materialised)`);
  return STAGED_DOCS;
}

// CLI entry.
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('release-stage.mjs')) {
  const version = process.argv[2];
  stage(version);
}
