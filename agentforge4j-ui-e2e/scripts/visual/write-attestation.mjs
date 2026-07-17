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
const OUTPUT_DIR = join(E2E_ROOT, 'visual-output');
const REPORT_PATH = join(OUTPUT_DIR, 'report.json');
const EXPECTED_INVENTORY_PATH = join(OUTPUT_DIR, 'expected-inventory.json');
const ATTESTATION_DIR = join(E2E_ROOT, 'visual-evidence');
const ATTESTATION_PATH = join(ATTESTATION_DIR, 'attestation.json');

/** Fields this script itself dereferences when building the attestation — checked independently of
 *  generate-report.mjs's own fail-closed behaviour (defence in depth: this script must never
 *  certify evidence that doesn't actually prove a real capture run, even if report.json somehow
 *  slipped through with stale, hand-edited, or pre-fix data that predates generate-report.mjs's own
 *  `noEvidence` guard). */
const REQUIRED_REPORT_FIELDS = {
  generatedAt: 'string',
  commitSha: 'string',
  manifestHash: 'string',
  viewportsCaptured: 'object', // Array.isArray checked separately below
};

/**
 * Pure: decides whether `report` (already-parsed report.json content) is trustworthy enough to
 * attest, independent of generate-report.mjs's own fail-closed behaviour — every check here mirrors
 * a way that behaviour is meant to prevent bad evidence, checked again rather than trusted, so this
 * script can never attest evidence that doesn't actually prove a real capture run happened.
 * Separated from real file I/O (below) so this is directly unit-testable against fixture report
 * objects. Returns `{ ok: true }` or `{ ok: false, error }`.
 */
export function validateReportForAttestation(report, hasExpectedInventory) {
  if (!hasExpectedInventory) {
    return {
      ok: false,
      error: 'visual-output/expected-inventory.json is missing — `visual:capture` never ran this ' +
        'pass (or its own clean-before-every-run guarantee was bypassed). Run `npm run visual:capture` first.',
    };
  }
  if (typeof report.summary?.totalCaptures !== 'number' || report.summary.totalCaptures === 0) {
    return {
      ok: false,
      error: 'report.json reports zero (or a non-numeric) totalCaptures — no real visual-review ' +
        'evidence exists. Run `npm run visual:capture` first.',
    };
  }
  if (typeof report.summary?.overallStatus !== 'string') {
    return { ok: false, error: 'report.json is missing a valid summary.overallStatus.' };
  }
  const missingFields = Object.entries(REQUIRED_REPORT_FIELDS)
    .filter(([field, type]) => typeof report[field] !== type)
    .map(([field]) => field)
    .concat(Array.isArray(report.viewportsCaptured) ? [] : ['viewportsCaptured']);
  if (missingFields.length > 0) {
    return {
      ok: false,
      error: `report.json is missing or has an invalid type for required field(s): ` +
        `${[...new Set(missingFields)].join(', ')} — incomplete report data cannot prove a real ` +
        'capture run. Run `npm run visual:report` again.',
    };
  }
  return { ok: true };
}

function main() {
  if (!existsSync(REPORT_PATH)) {
    console.error(`[write-attestation] no report found at ${REPORT_PATH} — run \`npm run visual:report\` first.`);
    process.exit(1);
  }
  const report = JSON.parse(readFileSync(REPORT_PATH, 'utf8'));

  const validation = validateReportForAttestation(report, existsSync(EXPECTED_INVENTORY_PATH));
  if (!validation.ok) {
    console.error(`[write-attestation] refusing to attest: ${validation.error}`);
    process.exit(1);
  }

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

// CLI entry, guarded so `validateReportForAttestation` can be imported and unit-tested (see
// specs/visual-unit/write-attestation.spec.ts) without this script's real side effects (reading
// the live report, writing the attestation file) running on import — same pattern
// generate-report.mjs/check-freshness.mjs already use for the same reason.
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main();
}
