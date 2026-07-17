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
// Usage: node scripts/visual/check-freshness.mjs [--strict]

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
// change-detection glob exactly, including its `.nvmrc` and workflow-file-self triggers — NOT just
// `agentforge4j-ui-e2e/visual/`: the capture orchestrator (specs/visual/capture.spec.ts), the local
// static server (scripts/visual/serve-assembled-site.mjs), and every other file under
// agentforge4j-ui-e2e/ (including support/web-ui/routes.ts, the manifest's own route/viewport data
// source) are just as capable of changing what gets captured/checked as an edit inside visual/
// itself. Broader than strictly necessary in places (e.g. an unrelated specs/web-ui/ change also
// counts as "relevant") is the correct, safe direction for a warn-only freshness check — see this
// file's own "fail open (warn) rather than silently assume it's still fresh" precedent below.
//
// EXCLUDES `agentforge4j-ui-e2e/visual-evidence/` (the negative lookahead below) — that directory
// IS this script's own output (`ATTESTATION_PATH`, above), not a UI source. Matching the whole
// `agentforge4j-ui-e2e/` tree without this exclusion reintroduced the exact circular
// self-invalidation bug the `commitSha !== currentSha` comment below already explains and was
// fixed for once: committing the attestation is itself a change under `agentforge4j-ui-e2e/`, so an
// attestation-only commit would immediately register as "predates a relevant change" against
// itself, permanently, the moment the path pattern was widened past `visual/` to the whole tree.
const RELEVANT_PATH_PATTERN =
  /^(agentforge4j-web-ui\/|agentforge4j-workflow-builder\/|agentforge4j-docs\/|agentforge4j-ui-e2e\/(?!visual-evidence\/)|agentforge4j-workflows-catalog\/|agentforge4j-schema\/|\.nvmrc$|\.github\/workflows\/ui-e2e\.yml$)/;

function parseArgs(argv) {
  const args = { strict: false };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--strict') {
      args.strict = true;
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

/** Pure: parses `git status --porcelain` output and returns every path matching
 *  RELEVANT_PATH_PATTERN — separated from the actual `git status` invocation so this is directly
 *  unit-testable against fixture porcelain text, without a real git repository. Each porcelain line
 *  is exactly `XY PATH` (2 status chars, 1 space, then the path), except a rename, which is
 *  `XY OLD -> NEW`; renames keep the NEW path, matching what `git diff --name-only` already reports
 *  elsewhere in this file. Covers staged, unstaged, AND untracked (`??`) changes — a genuinely
 *  relevant new file that was never `git add`ed is just as capable of making evidence stale/
 *  misleading as a tracked modification. */
export function parseDirtyRelevantFiles(porcelainOutput) {
  if (!porcelainOutput.trim()) {
    return [];
  }
  return porcelainOutput
    .split('\n')
    .filter(Boolean)
    .map((line) => {
      const path = line.slice(3);
      const parts = path.split(' -> ');
      return parts[parts.length - 1];
    })
    .filter((path) => RELEVANT_PATH_PATTERN.test(path));
}

/** The canonical "is the source this suite cares about dirty right now" check — shared by this
 *  script's own --strict dirty-tree gate (below) and by scripts/visual/release-check.mjs's
 *  assembled-site provenance check (site-provenance.mjs), so both use exactly one definition of
 *  "relevant" and can never silently drift apart.
 *
 *  DESIGN DECISION (reviewed, not a default): strict evidence is anchored to a commit sha, and this
 *  function's whole job is proving no relevant file is dirty relative to that commit — never a hash
 *  of the working tree itself, even though a dirty-tree hash would let strict review run before
 *  committing. Rejected that alternative on durability grounds: a commit sha's content is
 *  guaranteed retrievable forever (as long as the commit is reachable); a hash over uncommitted
 *  content can attest to bytes that vanish the moment the developer's working tree changes again,
 *  with nothing durable anywhere to audit against later. It would also need to reimplement, on top
 *  of that weaker guarantee, correctness git already provides for free — content hashing must go
 *  through `git hash-object`, not a raw file read, since Windows/Linux line-ending normalization
 *  differences would otherwise hash the identical committed content differently on this repo's own
 *  dev machine vs. CI (a real, repeatedly-hit failure class in this codebase, not theoretical) —
 *  and `git stash create`, the obvious git-native shortcut, silently drops untracked files, which
 *  this function deliberately does NOT. None of that buys anything a commit-anchored gate doesn't
 *  already have: `npm run visual:capture`/`visual:report` (non-strict) already iterate freely on a
 *  dirty tree with the same substantive pass/fail signal — only the final, durable-evidence step
 *  requires a clean commit, which is how essentially every mature release gate already works
 *  (`npm publish`, tag-based deploys, SLSA/in-toto-style provenance — all anchored to a resolved
 *  revision, never a working-tree hash). */
export function getDirtyRelevantFiles() {
  return parseDirtyRelevantFiles(git(['status', '--porcelain']));
}

/** Pure: validates already-read attestation file text, tolerating every real-world way it can be
 *  broken: not valid JSON, valid JSON but not an object (e.g. `null` or an array), or missing/
 *  wrongly-typed fields this script's own logic actually dereferences. Never throws — an uncaught
 *  JSON.parse()/schema error would break this script's documented "always exits 0 in non-strict/CI
 *  mode" contract, turning a warn-only check into a hard CI failure over a corrupted or hand-edited
 *  artifact, which is a worse outcome than just warning about it. Separated from the real file read
 *  (below) so this is directly unit-testable against fixture text, without a real attestation file
 *  on disk. Returns `{ ok: true, attestation }` or `{ ok: false, warning }`. */
export function parseAttestation(raw) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    return {
      ok: false,
      warning: `visual-evidence/attestation.json is not valid JSON (${error.message}). Run ` +
        '`npm run visual:release-check` locally and commit a fresh attestation.',
    };
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return {
      ok: false,
      warning: 'visual-evidence/attestation.json does not contain a JSON object. Run ' +
        '`npm run visual:release-check` locally and commit a fresh attestation.',
    };
  }
  const requiredFieldTypes = { commitSha: 'string', manifestHash: 'string', overallStatus: 'string' };
  const invalidFields = Object.entries(requiredFieldTypes)
    .filter(([field, type]) => typeof parsed[field] !== type)
    .map(([field]) => field);
  if (invalidFields.length > 0) {
    return {
      ok: false,
      warning: 'visual-evidence/attestation.json is missing or has an invalid type for required ' +
        `field(s): ${invalidFields.join(', ')}. Run \`npm run visual:release-check\` locally and ` +
        'commit a fresh attestation.',
    };
  }
  return { ok: true, attestation: parsed };
}

/** Reads the real attestation file and validates it via `parseAttestation` — also tolerates an
 *  unreadable file (permissions, I/O error), which `parseAttestation` alone can't see since it
 *  only ever receives already-read text. */
function loadAttestation() {
  let raw;
  try {
    raw = readFileSync(ATTESTATION_PATH, 'utf8');
  } catch (error) {
    return {
      ok: false,
      warning: `Could not read visual-evidence/attestation.json (${error.message}). Run ` +
        '`npm run visual:release-check` locally and commit a fresh attestation.',
    };
  }
  return parseAttestation(raw);
}

function main() {
  const { strict } = parseArgs(process.argv.slice(2));
  const warnings = [];

  // Refuses to create trust in evidence generated against a moving target: a strict release check
  // must review a durable, committed source state, not a working tree that can change again before
  // anyone reads the result. Checked first and unconditionally in strict mode — there is no point
  // validating an attestation's staleness against a tree that's dirty in a way the attestation
  // could never have accounted for.
  if (strict) {
    const dirtyRelevant = getDirtyRelevantFiles();
    if (dirtyRelevant.length > 0) {
      warnings.push(
        `Relevant source file(s) are uncommitted (dirty): ${dirtyRelevant.slice(0, 5).join(', ')}` +
          `${dirtyRelevant.length > 5 ? ` (+${dirtyRelevant.length - 5} more)` : ''}. A strict release ` +
          'check must review a durable, committed source state — commit these changes, then run ' +
          '`npm run visual:release-check` again.',
      );
      report(warnings, strict);
      return;
    }
  }

  if (!existsSync(ATTESTATION_PATH)) {
    warnings.push('No local visual-review evidence exists (visual-evidence/attestation.json is missing). ' +
      'Run `npm run visual:release-check` locally (see agentforge4j-ui-e2e/README.md) and commit the ' +
      'refreshed attestation.');
    report(warnings, strict);
    return;
  }

  const loaded = loadAttestation();
  if (!loaded.ok) {
    warnings.push(loaded.warning);
    report(warnings, strict);
    return;
  }
  const attestation = loaded.attestation;
  const currentSha = git(['rev-parse', 'HEAD']);

  // Deliberately NOT "warn whenever commitSha !== currentSha": committing the attestation file
  // itself necessarily produces a new HEAD the attestation can't know about — a bare SHA-equality
  // check would make every committed attestation register as stale one commit after it's
  // committed, permanently (a real defect an earlier version of this script had; verified via a
  // concrete repro: an attestation recording commit A, committed as part of commit B, immediately
  // read back as stale against B). The only thing that actually matters is whether a UI-RELEVANT
  // path changed between the attested commit and now — an attestation-only commit, or any other
  // commit that never touches the reviewed surface, must not invalidate otherwise-current evidence.
  if (attestation.commitSha !== currentSha) {
    let changedFiles = null;
    try {
      changedFiles = git(['diff', '--name-only', attestation.commitSha, currentSha]).split('\n').filter(Boolean);
    } catch {
      // attestation.commitSha may not be reachable (shallow clone, rewritten history) — can't
      // compute a diff, so we genuinely don't know whether anything relevant changed. Fail open
      // (warn) rather than silently assume it's still fresh.
    }
    if (changedFiles === null) {
      warnings.push(`Evidence was generated for commit ${attestation.commitSha.slice(0, 12)}, which is not reachable ` +
        `from the current ${currentSha.slice(0, 12)} (shallow clone or rewritten history) — cannot confirm freshness.`);
    } else {
      const relevantChanges = changedFiles.filter((f) => RELEVANT_PATH_PATTERN.test(f));
      if (relevantChanges.length > 0) {
        warnings.push(`Evidence (commit ${attestation.commitSha.slice(0, 12)}) predates ${relevantChanges.length} ` +
          `relevant UI change(s) since it was generated (e.g. ${relevantChanges.slice(0, 5).join(', ')}).`);
      }
    }
  }

  const liveManifestHash = manifestHash();
  if (attestation.manifestHash !== liveManifestHash) {
    warnings.push('The visual-review manifest (visual/manifest.ts) has changed since the evidence was generated.');
  }

  if (attestation.overallStatus === 'fail') {
    warnings.push('The most recent visual-review evidence reports unresolved visual failures (overallStatus: fail).');
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

// CLI entry, guarded so `RELEVANT_PATH_PATTERN` can be imported and unit-tested (see
// specs/visual-unit/check-freshness.spec.ts) without this script's real side effects (running git,
// reading the live attestation) running on import — same pattern generate-report.mjs/
// clean-output.mjs already use for the same reason.
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main();
}

export { RELEVANT_PATH_PATTERN };
