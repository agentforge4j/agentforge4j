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

/**
 * Splits a capture's failing checks into what actually blocks a release vs what's been
 * deliberately, visibly classified as non-blocking. The ONLY exemption path is a SPECIFIC check on
 * this capture carrying an `acceptedFindings` entry naming that exact `checkId` — AND, when that
 * entry restricts itself to specific `viewports`, this capture's own viewport must be one of them
 * (see `visual/manifest.ts`'s doc comment on `AcceptedFinding.viewports`: a defect confirmed on
 * mobile only must not exempt the same check id failing on desktop for a different reason).
 * `knownIssues` is informational-only and never exempts anything by itself. Deliberately NOT "if
 * `knownIssues` is non-empty, every failure on this capture is non-blocking": that was a real
 * correctness bug — a capture tagged for one known, specific defect (e.g. a palette-clipping issue)
 * could develop a completely unrelated failure (blank page, broken image, console crash, missing
 * canvas) and still pass the release gate, because the exemption applied to the whole capture
 * instead of the one check that issue actually affects. A check id not covered by a (viewport-
 * matching) `acceptedFindings` entry always blocks, even on a capture that has OTHER accepted
 * findings — this is per-check, not "the whole capture is fine now". Never silent: every
 * non-blocking check here carries a `reason` that the report renders, and the caller decides for
 * itself whether to also distinguish `requiresHumanConfirmation` (a warning) from a fully accepted,
 * explained non-issue.
 */
function classifyFailingChecks(capture) {
  const failing = capture.checks.filter((c) => c.status === 'fail');
  const accepted = capture.acceptedFindings ?? [];
  const blocking = [];
  const nonBlocking = [];
  for (const check of failing) {
    const classification =
      accepted.find(
        (a) => a.checkId === check.id && (!a.viewports || a.viewports.includes(capture.viewport)),
      ) ?? null;
    (classification ? nonBlocking : blocking).push({ check, classification });
  }
  return { blocking, nonBlocking };
}

/**
 * Cross-checks the real result files against `expected-inventory.json` (written by
 * `capture.spec.ts` before any test runs — see its own comment). Two distinct integrity problems,
 * both release-blocking on their own, independent of any `acceptedFindings` classification (there
 * is no check to classify when the whole capture never happened):
 *  - `missing`: an entry×viewport the current manifest defines has no result file — most likely a
 *    test that crashed/threw before reaching its own `writeFileSync` (e.g. `addStep`'s fail-loud
 *    design in `visual/interactions.ts`), silently reporting fewer states than the manifest
 *    actually requires would otherwise go unnoticed.
 *  - `unexpected`: a result file exists with no matching entry in the current manifest — stale
 *    output from a prior run (a removed entry, a reduced viewport list) that `previsual:capture`'s
 *    clean step should have already prevented; still checked here as defence in depth.
 */
function inventoryDiff(captures) {
  const expectedPath = join(OUTPUT_DIR, 'expected-inventory.json');
  if (!existsSync(expectedPath)) {
    // No expected-inventory.json means `visual:capture` was never run this pass (e.g. only
    // `visual:report` was re-run standalone against already-generated results) — nothing to
    // cross-check against.
    return { missing: [], unexpected: [] };
  }
  const expected = new Set(JSON.parse(readFileSync(expectedPath, 'utf8')));
  const actual = new Set(captures.map((c) => `${c.entryId}--${c.viewport}`));
  return {
    missing: [...expected].filter((key) => !actual.has(key)).sort(),
    unexpected: [...actual].filter((key) => !expected.has(key)).sort(),
  };
}

function buildReport() {
  const captures = loadCaptureRecords().map((c) => ({ ...c, classified: classifyFailingChecks(c) }));
  const aiReview = readJsonIfExists(join(OUTPUT_DIR, 'ai-review-results.json'), null);
  const { missing: missingCaptures, unexpected: unexpectedCaptures } = inventoryDiff(captures);

  const deterministicPass = captures.filter((c) => c.overallStatus === 'pass').length;
  const deterministicFail = captures.length - deterministicPass;
  const aiFail = (aiReview?.reviewed ?? []).filter((r) => r.status === 'fail').length;
  const aiWarning = (aiReview?.reviewed ?? []).filter((r) => r.status === 'warning').length;

  // A capture is a release-blocking failure only if it has at least one failing check that isn't
  // covered by a documented `acceptedFindings` classification — see `classifyFailingChecks` above.
  // A missing or unexpected capture (evidence integrity, not a check result) always blocks
  // regardless — see `inventoryDiff` above. Together, this is everything
  // `visual:release-check --strict` fails on.
  const blockingCaptures = captures.filter((c) => c.classified.blocking.length > 0);
  const overallStatus =
    blockingCaptures.length > 0 || missingCaptures.length > 0 || unexpectedCaptures.length > 0 ? 'fail' : 'pass';

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
      blockingFailures: blockingCaptures.length,
      nonBlockingFindings: captures.filter((c) => c.classified.nonBlocking.length > 0).length,
      missingCaptures,
      unexpectedCaptures,
      aiReviewEnabled: Boolean(aiReview?.enabled),
      aiModel: aiReview?.model ?? null,
      aiReviewedCount: aiReview?.reviewedCount ?? 0,
      aiFail,
      aiWarning,
      overallStatus,
    },
    entries: captures.map((c) => ({
      ...c,
      isBlocking: c.classified.blocking.length > 0,
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
  lines.push(`- Release-blocking failures (no matching acceptedFindings classification): ${report.summary.blockingFailures}`);
  lines.push(`- Non-blocking findings (explicitly accepted or flagged as a warning): ${report.summary.nonBlockingFindings}`);
  lines.push(`- Missing captures (expected by the manifest, no result found — release-blocking): ${report.summary.missingCaptures.length}`);
  lines.push(`- Unexpected captures (result found, not in the current manifest — release-blocking): ${report.summary.unexpectedCaptures.length}`);
  if (report.summary.aiReviewEnabled) {
    lines.push(`- AI review: ${report.summary.aiFail} fail / ${report.summary.aiWarning} warning`);
  }
  lines.push(`- **Overall status: ${statusBadge(report.summary.overallStatus)}**`);
  lines.push('');

  if (report.summary.missingCaptures.length > 0 || report.summary.unexpectedCaptures.length > 0) {
    lines.push('## Evidence integrity — detail');
    lines.push('');
    if (report.summary.missingCaptures.length > 0) {
      lines.push('**Missing** (the manifest expects these, but no result file exists — likely a crashed or');
      lines.push('errored capture; see the Playwright run\'s own output for which test failed):');
      lines.push('');
      for (const key of report.summary.missingCaptures) {
        lines.push(`- \`${key}\``);
      }
      lines.push('');
    }
    if (report.summary.unexpectedCaptures.length > 0) {
      lines.push('**Unexpected** (a result file exists with no matching entry in the current manifest —');
      lines.push('stale output from a prior run; `previsual:capture` should have cleared this):');
      lines.push('');
      for (const key of report.summary.unexpectedCaptures) {
        lines.push(`- \`${key}\``);
      }
      lines.push('');
    }
  }

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
      const deterministic =
        entry.overallStatus === 'pass' ? 'PASS' : entry.isBlocking ? 'FAIL (blocking)' : 'FAIL (non-blocking)';
      lines.push(
        `| ${entry.stateName} | ${entry.viewport} | ${deterministic} | ${ai} | ${known} | \`${entry.screenshotPath}\` |`,
      );
    }
    lines.push('');
  }

  const blockingEntries = report.entries.filter((e) => e.isBlocking);
  if (blockingEntries.length > 0) {
    lines.push('## Release-blocking deterministic failures — detail');
    lines.push('');
    lines.push('Not covered by a documented `acceptedFindings` classification for this specific check —');
    lines.push('these are what `visual:release-check --strict` actually fails on. A `knownIssues` tag on');
    lines.push('an entry (see the ".org site"/"Workflow Builder" tables above) is informational only and');
    lines.push('does NOT exempt a check by itself; only a matching `acceptedFindings` entry does.');
    lines.push('');
    for (const entry of blockingEntries) {
      lines.push(`### ${entry.stateName} @ ${entry.viewport} (BLOCKING)`);
      for (const { check } of entry.classified.blocking) {
        lines.push(`- **${check.id}**: ${check.detail ?? '(no detail)'}`);
      }
      if (entry.notes) {
        lines.push(`- _Manifest note: ${entry.notes}_`);
      }
      lines.push('');
    }
  }

  const nonBlockingEntries = report.entries.filter((e) => e.classified.nonBlocking.length > 0);
  if (nonBlockingEntries.length > 0) {
    lines.push('## Non-blocking findings — detail');
    lines.push('');
    lines.push('Real deterministic-check failures that do NOT fail the release check because they carry a');
    lines.push('documented, per-check `acceptedFindings` classification (see the manifest).');
    lines.push('`requires-human-confirmation` findings are warnings, not settled non-issues.');
    lines.push('');
    for (const entry of nonBlockingEntries) {
      lines.push(`### ${entry.stateName} @ ${entry.viewport}`);
      if (entry.knownIssues.length > 0) {
        lines.push(`- Also tagged (informational only): ${entry.knownIssues.map((n) => `#${n}`).join(', ')}`);
      }
      for (const { check, classification } of entry.classified.nonBlocking) {
        const confirmTag = classification?.requiresHumanConfirmation ? ' — requires-human-confirmation' : '';
        const issueTag = classification?.issue ? ` (#${classification.issue})` : '';
        lines.push(`- **${check.id}**${issueTag}${confirmTag}: ${check.detail ?? '(no detail)'}`);
        if (classification?.reason) {
          lines.push(`  - _Reason: ${classification.reason}_`);
        }
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

// CLI entry, guarded so `classifyFailingChecks`/`inventoryDiff` can be imported and unit-tested
// (see specs/visual-unit/generate-report.spec.ts) without this script's real side effects
// (reading the live repo, writing report.json/md) running on import — same pattern
// agentforge4j-docs/scripts/assemble-site.mjs already uses for the same reason.
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main();
}

export { classifyFailingChecks, inventoryDiff };
