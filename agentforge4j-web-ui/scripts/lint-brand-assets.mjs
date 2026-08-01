// SPDX-License-Identifier: Apache-2.0
//
// Brand-asset single-source check. Some brand files legitimately exist in two places, and neither
// copy can be a symlink or a build-time artefact: the published web asset must be a committed file
// under this module's `public/` for Vite to copy it verbatim, and the repository-level copy under
// `.github/assets/` is the committed source of truth for an image that lives outside the repository
// altogether. The only thing that can keep two real copies honest is a check that they are
// byte-identical.
//
// Stated precisely, because the obvious assumption is wrong: GitHub does NOT read a repository's
// social preview from any committed path. It is uploaded once in Settings -> General -> Social
// preview and stored server-side, so nothing in this repository can make GitHub's card change and
// nothing in GitHub can make this file change. That is exactly why the committed copy is worth
// keeping and worth checking — it is the only record of what was uploaded, the thing a future
// rebrand diffs against, and the file someone re-uploads from. If it silently drifts from the card
// the site serves, the two surfaces show different marks and nothing anywhere would say so.
//
// This is the same arrangement agentforge4j-docs already uses for the navbar logo
// (scripts/lint-navbar-logo.mjs) — one canonical file, a documented copy, and a lint that fails the
// build the moment they diverge. Byte comparison, not a visual or size heuristic: two renderings of
// "the same" artwork that differ at all are two different images, and the failure this prevents is
// the site's social card and the repository's social card quietly showing different marks.

import { readFileSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');

/** Each pair is `{canonical, copy}` as repo-relative paths, with `why` explaining why the duplicate
 * cannot be removed — a new pair without a real reason belongs nowhere near this list. */
export const MIRRORED_BRAND_ASSETS = [
  {
    canonical: '.github/assets/agentforge4j-social-preview.png',
    copy: 'agentforge4j-web-ui/public/brand/social-preview.png',
    why:
      "the committed record of the card uploaded to the repository's social-preview setting (GitHub stores that " +
      'image server-side, not at any path in the tree); the site serves its own og:image/twitter:image from this ' +
      "module's public/ directory. Both must be the same card.",
  },
];

/** Compares every declared pair, returning a list of human-readable problems (empty when clean).
 * Separated from the CLI wrapper so the comparison itself is directly testable. */
export function findBrandAssetProblems(repoRoot = REPO_ROOT, pairs = MIRRORED_BRAND_ASSETS) {
  const problems = [];
  for (const { canonical, copy, why } of pairs) {
    const canonicalPath = join(repoRoot, canonical);
    const copyPath = join(repoRoot, copy);
    if (!existsSync(canonicalPath)) {
      problems.push(`missing canonical brand asset: ${relative(repoRoot, canonicalPath)}`);
      continue;
    }
    if (!existsSync(copyPath)) {
      problems.push(`missing published copy of ${canonical}: ${relative(repoRoot, copyPath)} (${why})`);
      continue;
    }
    if (Buffer.compare(readFileSync(canonicalPath), readFileSync(copyPath)) !== 0) {
      problems.push(`${copy} is no longer byte-identical to ${canonical} — update both, or neither (${why})`);
    }
  }
  return problems;
}

if (process.argv[1]?.endsWith('lint-brand-assets.mjs')) {
  const problems = findBrandAssetProblems();
  if (problems.length > 0) {
    for (const problem of problems) {
      console.error(`lint-brand-assets: ${problem}`);
    }
    process.exit(1);
  }
  console.log(`lint-brand-assets: ${MIRRORED_BRAND_ASSETS.length} mirrored brand asset(s) byte-identical — clean`);
}
