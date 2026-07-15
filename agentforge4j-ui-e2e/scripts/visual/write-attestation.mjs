// SPDX-License-Identifier: Apache-2.0
//
// Writes the small, COMMITTED attestation file CI reads to warn about stale/missing visual-review
// evidence (`.github/workflows/visual-freshness.yml` / `check-freshness.mjs`) — deliberately just
// metadata (commit, manifest hash, timestamp, pass/fail), never the full report or any screenshot,
// per Day 2 Task 6's "prefer a small attestation file ... rather than committing the full report
// or screenshots" guidance. The full `visual-output/report.md`/`report.json` stay local-only
// (gitignored) or become a CI artifact upload — see the module README for the exact policy.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const E2E_ROOT = resolve(here, '..', '..');
const REPORT_PATH = join(E2E_ROOT, 'visual-output', 'report.json');
const ATTESTATION_DIR = join(E2E_ROOT, 'visual-evidence');
const ATTESTATION_PATH = join(ATTESTATION_DIR, 'attestation.json');

function main() {
  if (!existsSync(REPORT_PATH)) {
    console.error(`[write-attestation] no report found at ${REPORT_PATH} — run \`npm run visual:report\` first.`);
    process.exit(1);
  }
  const report = JSON.parse(readFileSync(REPORT_PATH, 'utf8'));

  const attestation = {
    generatedAt: report.generatedAt,
    commitSha: report.commitSha,
    manifestHash: report.manifestHash,
    versions: report.versions,
    viewportsCaptured: report.viewportsCaptured,
    totalCaptures: report.summary.totalCaptures,
    overallStatus: report.summary.overallStatus,
    aiReviewEnabled: report.summary.aiReviewEnabled,
  };

  mkdirSync(ATTESTATION_DIR, { recursive: true });
  writeFileSync(ATTESTATION_PATH, `${JSON.stringify(attestation, null, 2)}\n`);
  console.log(`[write-attestation] wrote ${ATTESTATION_PATH} (status: ${attestation.overallStatus})`);
  console.log('[write-attestation] remember to commit this file if you want CI to see the refreshed evidence.');
}

main();
