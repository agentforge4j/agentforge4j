// SPDX-License-Identifier: Apache-2.0
//
// Post-build check that the client-redirect stub RECOGNITION RULE still matches what
// `@docusaurus/plugin-client-redirects` actually emits.
//
// Why this exists as its own gate, and here rather than in the composition step. The stubs are
// labelled by assemble-site.mjs (step 8, via redirect-stub-seo.mjs), which recognises a stub by its
// meta refresh — one regex over a third-party plugin's template. That pass fails closed when it
// recognises nothing, which is the right behaviour but the wrong PLACE to find out: `assemble-site.mjs`
// runs only in the deploy workflow, never in the Docs PR gate. Its unit tests assert against a
// fixture captured from the live site, not against the plugin's real output, so a plugin upgrade that
// reorders or requotes the template's attributes leaves `npm run check` green, merges, and then fails
// the deploy on `main`.
//
// This runs inside `npm run build`, immediately after `docusaurus build`, against the real emitted
// stubs — so template drift fails the pull request that introduces it, on the branch, where it is
// cheap to fix. It asserts recognition only; the labelling itself stays assemble-site's job.

import {existsSync, readdirSync, readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {redirectStubTarget} from './redirect-stub-seo.mjs';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

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
 * @param {{buildDir?: string, archiveVersion?: string|null}} [options]
 * @returns {string[]} the destination of every recognised stub
 */
export function verifyRedirectStubs({
  buildDir = join(MODULE_ROOT, 'build'),
  archiveVersion = process.env.AF4J_ARCHIVE_VERSION || null,
} = {}) {
  if (!existsSync(buildDir)) {
    throw new Error(`verify-redirect-stubs: ${buildDir} does not exist — run "docusaurus build" first`);
  }

  // An archive artifact owns no moving alias, so docusaurus.config.ts drops the redirects plugin
  // entirely in archive mode — zero stubs is the correct output there, not drift. Same carve-out
  // verify-noindex.mjs makes, for the same reason.
  if (archiveVersion) {
    console.log(`[verify-redirect-stubs] archive-mode build (${archiveVersion}) — no redirects plugin, skipped`);
    return [];
  }

  const targets = collectHtmlFiles(buildDir)
    .map((file) => redirectStubTarget(readFileSync(file, 'utf8')))
    .filter((target) => target !== null);

  if (targets.length === 0) {
    throw new Error(
      'verify-redirect-stubs: recognised no client-redirect stubs in the build output. ' +
        "docusaurus.config.ts's `redirectConfig` generates at least the `/` and `/latest` redirects in " +
        'both lifecycle states, so the stubs ARE there — the recognition rule in redirect-stub-seo.mjs ' +
        'no longer matches the template the plugin emits. Left unfixed, assemble-site.mjs ships them raw ' +
        '(no title, no description, no robots directive) and fails the deploy instead of this build.',
    );
  }

  console.log(`[verify-redirect-stubs] recognised ${targets.length} client-redirect stub(s): ${targets.join(', ')}`);
  return targets;
}

if (process.argv[1]?.endsWith('verify-redirect-stubs.mjs')) {
  try {
    verifyRedirectStubs();
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
