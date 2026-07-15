// SPDX-License-Identifier: Apache-2.0
//
// Checks the committed attestation (`visual-evidence/attestation.json`) against live repo state.
// Never runs an AI model, never launches a browser — pure git/filesystem comparison, safe and fast
// enough to run on every PR. Two modes:
//   (default)  Warn-only — used by `.github/workflows/visual-freshness.yml`. Always exits 0; prints
//              `::warning::` GitHub Actions annotations and a job-summary table for anything stale.
//   --strict   Blocking — used by the local `visual:release-check` command ahead of a 0.1.0
//              publish. Exits 1 if anything is stale or the evidence reports an unresolved failure.
//
// Usage: node scripts/visual/check-freshness.mjs [--strict] [--base-sha <sha>]

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, appendFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const E2E_ROOT = resolve(here, '..', '..');
const REPO_ROOT = resolve(E2E_ROOT, '..');
const ATTESTATION_PATH = join(E2E_ROOT, 'visual-evidence', 'attestation.json');
const MANIFEST_PATH = join(E2E_ROOT, 'visual', 'manifest.ts');

// Mirrors .github/workflows/ui-e2e.yml's own "what can change what the site/builder renders"
// change-detection glob, plus the manifest's own directory (an entry/interaction/check change is
// just as relevant as a source change — it's what decides *what gets looked at*).
const RELEVANT_PATH_PATTERN =
  /^(agentforge4j-web-ui\/|agentforge4j-workflow-builder\/|agentforge4j-docs\/|agentforge4j-ui-e2e\/visual\/|agentforge4j-workflows-catalog\/|agentforge4j-schema\/)/;

function parseArgs(argv) {
  const args = { strict: false, baseSha: null };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--strict') {
      args.strict = true;
    } else if (argv[i] === '--base-sha') {
      args.baseSha = argv[i + 1];
      i += 1;
    }
  }
  return args;
}

function git(args) {
  return execFileSync('git', args, { cwd: REPO_ROOT, encoding: 'utf8' }).trim();
}

function manifestHash() {
  return createHash('sha256').update(readFileSync(MANIFEST_PATH)).digest('hex');
}

function main() {
  const { strict, baseSha } = parseArgs(process.argv.slice(2));
  const warnings = [];

  if (!existsSync(ATTESTATION_PATH)) {
    warnings.push('No local visual-review evidence exists (visual-evidence/attestation.json is missing). ' +
      'Run `npm run visual:release-check` locally (see agentforge4j-ui-e2e/README.md) and commit the ' +
      'refreshed attestation.');
    report(warnings, strict);
    return;
  }

  const attestation = JSON.parse(readFileSync(ATTESTATION_PATH, 'utf8'));
  const currentSha = git(['rev-parse', 'HEAD']);

  if (attestation.commitSha !== currentSha) {
    warnings.push(`Evidence was generated for commit ${attestation.commitSha.slice(0, 12)}, not the current ${currentSha.slice(0, 12)}.`);

    // Only worth flagging as a *relevant* staleness if a UI-relevant path actually changed between
    // the attested commit and now — an evidence file that's merely "a few unrelated commits old"
    // is not what this check exists to catch.
    let changedFiles = [];
    try {
      changedFiles = git(['diff', '--name-only', attestation.commitSha, currentSha]).split('\n').filter(Boolean);
    } catch {
      // attestation.commitSha may not be reachable (shallow clone, rewritten history) — can't
      // compute a diff, so fall through without the extra relevance detail; the staleness warning
      // above already fired.
    }
    const relevantChanges = changedFiles.filter((f) => RELEVANT_PATH_PATTERN.test(f));
    if (relevantChanges.length > 0) {
      warnings.push(`Evidence predates ${relevantChanges.length} relevant UI change(s) since it was generated ` +
        `(e.g. ${relevantChanges.slice(0, 5).join(', ')}).`);
    }
  }

  const liveManifestHash = manifestHash();
  if (attestation.manifestHash !== liveManifestHash) {
    warnings.push('The visual-review manifest (visual/manifest.ts) has changed since the evidence was generated.');
  }

  if (attestation.overallStatus === 'fail') {
    warnings.push('The most recent visual-review evidence reports unresolved visual failures (overallStatus: fail).');
  }

  if (baseSha) {
    try {
      const prChanges = git(['diff', '--name-only', baseSha, currentSha]).split('\n').filter(Boolean);
      const relevant = prChanges.filter((f) => RELEVANT_PATH_PATTERN.test(f));
      if (relevant.length > 0 && attestation.commitSha === currentSha) {
        // Defensive: covers the case where the attestation was committed IN this same PR but a
        // later commit in the same PR touched UI paths without refreshing it again.
        warnings.push(`This PR's own diff touches ${relevant.length} UI-relevant path(s); confirm the attestation reflects them.`);
      }
    } catch {
      // --base-sha not resolvable in this checkout (e.g. shallow fetch) — skip this extra check.
    }
  }

  report(warnings, strict);
}

function report(warnings, strict) {
  if (warnings.length === 0) {
    console.log('[check-freshness] visual-review evidence is fresh.');
    return;
  }

  console.log(`[check-freshness] ${warnings.length} freshness issue(s) found:`);
  for (const warning of warnings) {
    console.log(`  - ${warning}`);
    if (process.env.GITHUB_ACTIONS === 'true') {
      console.log(`::warning::visual-review evidence: ${warning}`);
    }
  }

  if (process.env.GITHUB_STEP_SUMMARY) {
    const lines = [
      '## Visual review evidence freshness',
      '',
      ...warnings.map((w) => `- ${w}`),
      '',
      strict
        ? '_Blocking (release check)._'
        : '_Non-blocking — this does not fail the PR. Run `npm run visual:release-check` before a 0.1.0 publish._',
    ];
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`);
  }

  if (strict) {
    console.error('[check-freshness] --strict: failing.');
    process.exit(1);
  }
}

main();
