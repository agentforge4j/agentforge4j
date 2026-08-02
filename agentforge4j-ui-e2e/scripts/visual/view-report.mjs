// SPDX-License-Identifier: Apache-2.0
//
// Prints the generated report's summary to the console and the paths to the full reports, and
// best-effort opens the human-readable report in the OS default viewer. Never fails the process if
// opening fails (a headless CI-like environment has no default viewer) — printing the path is
// always enough to find it.

import { execFile } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const OUTPUT_DIR = resolve(here, '..', '..', 'visual-output');
const REPORT_MD = join(OUTPUT_DIR, 'report.md');
const REPORT_JSON = join(OUTPUT_DIR, 'report.json');

function openInDefaultViewer(path) {
  const command = process.platform === 'win32' ? 'cmd' : process.platform === 'darwin' ? 'open' : 'xdg-open';
  const args = process.platform === 'win32' ? ['/c', 'start', '', path] : [path];
  execFile(command, args, () => {
    // Best-effort only — no viewer available is not an error.
  });
}

function main() {
  if (!existsSync(REPORT_JSON)) {
    console.error(`[view-report] no report found at ${REPORT_JSON} — run \`npm run visual:report\` first.`);
    process.exit(1);
  }
  const report = JSON.parse(readFileSync(REPORT_JSON, 'utf8'));
  console.log(`Overall status: ${report.summary.overallStatus.toUpperCase()}`);
  console.log(`Generated: ${report.generatedAt} (commit ${report.commitSha.slice(0, 12)})`);
  console.log(`Captures: ${report.summary.totalCaptures} (${report.summary.deterministicPass} pass / ${report.summary.deterministicFail} fail, ${report.summary.blockingFailures} blocking)`);
  console.log(`AI review: ${report.summary.aiReviewEnabled ? `${report.summary.aiModel}, ${report.summary.aiReviewedCount} reviewed` : 'disabled'}`);
  console.log('');
  console.log(`Full report: ${REPORT_MD}`);
  console.log(`Machine-readable: ${REPORT_JSON}`);

  if (!process.env.CI && existsSync(REPORT_MD)) {
    openInDefaultViewer(REPORT_MD);
  }
}

main();
