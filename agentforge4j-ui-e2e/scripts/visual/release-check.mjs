// SPDX-License-Identifier: Apache-2.0
//
// One-shot "full visual release check" (Day 2 Tasks 7 + 8): regenerates every piece of evidence
// fresh (never trusts a possibly-stale prior run) and then fails closed via
// `check-freshness.mjs --strict` if anything is missing, stale, or reports an unresolved visual
// failure. Meant to be run manually by a maintainer ahead of a real 0.1.0 publish — never wired
// into CI (see `.github/workflows/visual-freshness.yml`'s own warn-only, evidence-only check).
//
// Does NOT run `build-assembled-site.mjs` itself (that step is slow — a full OSS reactor +
// Javadoc + Docusaurus build — and independently useful outside a release check, e.g. while
// iterating on visual/manifest.ts). Requires the composed site to already exist at
// `agentforge4j-docs/_site`; fails closed with a clear instruction if it doesn't, rather than
// silently building it (and silently taking several extra minutes) on a maintainer's behalf.
//
// Usage: node scripts/visual/release-check.mjs

import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { run as runShared } from './run-command.mjs';

const here = fileURLToPath(new URL('.', import.meta.url));
const E2E_ROOT = resolve(here, '..', '..');
const SITE_DIR = resolve(E2E_ROOT, '..', 'agentforge4j-docs', '_site');

const run = (command, args) => runShared('[release-check]', command, args, E2E_ROOT, process.platform === 'win32');

function main() {
  if (!existsSync(resolve(SITE_DIR, 'index.html'))) {
    console.error(`[release-check] no composed site found at ${SITE_DIR}.`);
    console.error('  Run `npm run visual:build-site` first (slow — full OSS reactor + Javadoc + Docusaurus build).');
    process.exit(1);
  }

  // `npm run visual:capture`, not a raw `npx playwright test`: identical command either way, but
  // avoids npx's own extra child-process layer on top of an already deep
  // node->playwright->browser-workers tree.
  run('npm', ['run', 'visual:capture']);
  run('node', ['scripts/visual/generate-report.mjs']);
  run('node', ['scripts/visual/write-attestation.mjs']);
  run('node', ['scripts/visual/check-freshness.mjs', '--strict']);

  console.log('[release-check] PASS — visual-review evidence is fresh and reports no unresolved failures.');
  console.log('[release-check] Commit agentforge4j-ui-e2e/visual-evidence/attestation.json to publish this evidence.');
}

main();
