// SPDX-License-Identifier: Apache-2.0
//
// Merges every per-capture result file (`visual-output/results/*.json`, written independently by
// each Playwright worker — see `specs/visual/capture.spec.ts`'s header comment for why there's no
// single already-consolidated file) plus the optional AI review output into one human-readable
// report (`visual-output/report.md`) and one machine-readable report (`visual-output/report.json`).
// Never calls an AI model itself — reads `ai-review-results.json` if present, treats its absence
// as "AI review not run this pass", not an error.

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const E2E_ROOT = resolve(here, '..', '..');
const REPO_ROOT = resolve(E2E_ROOT, '..');
const OUTPUT_DIR = join(E2E_ROOT, 'visual-output');
const RESULTS_DIR = join(OUTPUT_DIR, 'results');

function readJsonIfExists(path, fallback) {
  return existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : fallback;
}

function gitCommitSha() {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: REPO_ROOT, encoding: 'utf8' }).trim();
  } catch {
    return 'unknown (not a git checkout or git unavailable)';
  }
}

function packageVersion(relativeModulePath) {
  const path = join(REPO_ROOT, relativeModulePath, 'package.json');
  if (!existsSync(path)) {
    return 'unknown';
  }
  return JSON.parse(readFileSync(path, 'utf8')).version ?? 'unknown';
}

/** Hex sha256 of the manifest source file. Hashed as raw bytes (not imported/executed) so this
 *  script stays a plain, dependency-free `.mjs` — the manifest is TypeScript, and this repo has no
 *  TS-loader wired into standalone Node scripts (only into Playwright's own runtime). Any manifest
 *  edit changes this hash, which is exactly what `check-freshness.mjs` compares against the
 *  attestation to detect a stale report. */
function manifestHash() {
  const content = readFileSync(join(E2E_ROOT, 'visual', 'manifest.ts'));
  return createHash('sha256').update(content).digest('hex');
}

function loadCaptureRecords() {
  if (!existsSync(RESULTS_DIR)) {
    return [];
  }
  return readdirSync(RESULTS_DIR)
    .filter((name) => name.endsWith('.json'))
    .map((name) => JSON.parse(readFileSync(join(RESULTS_DIR, name), 'utf8')))
    .sort((a, b) => (a.entryId + a.viewport).localeCompare(b.entryId + b.viewport));
}

function aiFindingFor(aiReview, entryId, viewport) {
  if (!aiReview?.reviewed) {
    return null;
  }
  return aiReview.reviewed.find((r) => r.entryId === entryId && r.viewport === viewport) ?? null;
}

function buildReport() {
  const captures = loadCaptureRecords();
  const aiReview = readJsonIfExists(join(OUTPUT_DIR, 'ai-review-results.json'), null);

  const deterministicPass = captures.filter((c) => c.overallStatus === 'pass').length;
  const deterministicFail = captures.length - deterministicPass;
  const aiFail = (aiReview?.reviewed ?? []).filter((r) => r.status === 'fail').length;
  const aiWarning = (aiReview?.reviewed ?? []).filter((r) => r.status === 'warning').length;

  // Known-issue-tagged failures don't sink the overall verdict on their own (they're already
  // tracked elsewhere — see the manifest's `knownIssues` field and this workstream's scope
  // boundary against builder remediation); an UNtagged deterministic failure does.
  const newFailures = captures.filter((c) => c.overallStatus === 'fail' && (c.knownIssues ?? []).length === 0);
  const overallStatus = newFailures.length > 0 ? 'fail' : 'pass';

  const viewportsCaptured = [...new Set(captures.map((c) => c.viewport))].sort();

  const report = {
    generatedAt: new Date().toISOString(),
    commitSha: gitCommitSha(),
    manifestHash: manifestHash(),
    versions: {
      webUi: packageVersion('agentforge4j-web-ui'),
      workflowBuilder: packageVersion('agentforge4j-workflow-builder'),
    },
    viewportsCaptured,
    summary: {
      totalCaptures: captures.length,
      deterministicPass,
      deterministicFail,
      newDeterministicFailures: newFailures.length,
      aiReviewEnabled: Boolean(aiReview?.enabled),
      aiModel: aiReview?.model ?? null,
      aiReviewedCount: aiReview?.reviewedCount ?? 0,
      aiFail,
      aiWarning,
      overallStatus,
    },
    entries: captures.map((c) => ({
      ...c,
      aiFinding: aiFindingFor(aiReview, c.entryId, c.viewport),
    })),
  };
  return report;
}

function statusBadge(status) {
  return { pass: 'PASS', fail: 'FAIL', warning: 'WARN', skip: 'SKIP' }[status] ?? status.toUpperCase();
}

function renderMarkdown(report) {
  const lines = [];
  lines.push('# agentforge4j.org / Workflow Builder — Visual Review Report');
  lines.push('');
  lines.push(`- Generated: ${report.generatedAt}`);
  lines.push(`- Commit: \`${report.commitSha}\``);
  lines.push(`- Manifest hash: \`${report.manifestHash.slice(0, 16)}\``);
  lines.push(`- Web UI version: ${report.versions.webUi}`);
  lines.push(`- Workflow Builder version: ${report.versions.workflowBuilder}`);
  lines.push(`- Viewports captured: ${report.viewportsCaptured.join(', ') || '(none — run visual:capture first)'}`);
  lines.push(`- AI review: ${report.summary.aiReviewEnabled ? `enabled (${report.summary.aiModel}, ${report.summary.aiReviewedCount} reviewed)` : 'disabled'}`);
  lines.push('');
  lines.push('## Summary');
  lines.push('');
  lines.push(`- Total captures: ${report.summary.totalCaptures}`);
  lines.push(`- Deterministic checks: ${report.summary.deterministicPass} pass / ${report.summary.deterministicFail} fail`);
  lines.push(`- New (not already known-issue-tagged) deterministic failures: ${report.summary.newDeterministicFailures}`);
  if (report.summary.aiReviewEnabled) {
    lines.push(`- AI review: ${report.summary.aiFail} fail / ${report.summary.aiWarning} warning`);
  }
  lines.push(`- **Overall status: ${statusBadge(report.summary.overallStatus)}**`);
  lines.push('');

  for (const surface of ['org', 'builder']) {
    const entries = report.entries.filter((e) => e.surface === surface);
    if (entries.length === 0) {
      continue;
    }
    lines.push(`## ${surface === 'org' ? '.org site' : 'Workflow Builder'}`);
    lines.push('');
    lines.push('| State | Viewport | Deterministic | AI | Known issues | Screenshot |');
    lines.push('|---|---|---|---|---|---|');
    for (const entry of entries) {
      const known = entry.knownIssues.length > 0 ? entry.knownIssues.map((n) => `#${n}`).join(', ') : '—';
      const ai = entry.aiFinding ? `${statusBadge(entry.aiFinding.status)} (${entry.aiFinding.issueCategory})` : '—';
      lines.push(
        `| ${entry.stateName} | ${entry.viewport} | ${statusBadge(entry.overallStatus)} | ${ai} | ${known} | \`${entry.screenshotPath}\` |`,
      );
    }
    lines.push('');
  }

  const failingChecks = report.entries.filter((e) => e.overallStatus === 'fail');
  if (failingChecks.length > 0) {
    lines.push('## Failing deterministic checks — detail');
    lines.push('');
    for (const entry of failingChecks) {
      const tag = entry.knownIssues.length > 0 ? ` (known: ${entry.knownIssues.map((n) => `#${n}`).join(', ')})` : ' (NEW)';
      lines.push(`### ${entry.stateName} @ ${entry.viewport}${tag}`);
      for (const check of entry.checks.filter((c) => c.status === 'fail')) {
        lines.push(`- **${check.id}**: ${check.detail ?? '(no detail)'}`);
      }
      if (entry.notes) {
        lines.push(`- _Manifest note: ${entry.notes}_`);
      }
      lines.push('');
    }
  }

  const humanConfirm = (report.entries || [])
    .map((e) => e.aiFinding)
    .filter((f) => f && f.humanConfirmationRequired);
  if (humanConfirm.length > 0) {
    lines.push('## AI findings requiring human confirmation');
    lines.push('');
    lines.push('AI findings are review suggestions, not proven defects — confirm visually before acting.');
    lines.push('');
    for (const finding of humanConfirm) {
      lines.push(`- **${finding.entryId} @ ${finding.viewport}** — ${statusBadge(finding.status)}, ${finding.issueCategory} (confidence ${finding.confidence}): ${finding.evidence} [${finding.location}]`);
    }
    lines.push('');
  }

  return lines.join('\n');
}

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });
  const report = buildReport();
  writeFileSync(join(OUTPUT_DIR, 'report.json'), JSON.stringify(report, null, 2));
  writeFileSync(join(OUTPUT_DIR, 'report.md'), renderMarkdown(report));
  console.log(`[generate-report] wrote ${join(OUTPUT_DIR, 'report.json')} and report.md`);
  console.log(`[generate-report] overall status: ${report.summary.overallStatus}`);
  if (report.summary.totalCaptures === 0) {
    console.warn('[generate-report] WARNING: no captures found — run `npm run visual:capture` first.');
  }
}

main();
