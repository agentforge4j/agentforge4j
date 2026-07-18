// SPDX-License-Identifier: Apache-2.0
//
// Runs before every builder release: fails if this package's own source carries a
// monetization/billing term or a router/HTTP-client import, or if `npm pack` would ship a file
// outside the declared allowlist. No automated forbidden-strings/package-content check existed on
// main before this script; net-new for Phase 3, not a port of an existing mechanism.
//
// Scans src/**, not the built dist/ bundle, for the general forbidden-terms/imports check: tsup
// bundles third-party dependency code into dist/, and a blind substring scan over vendored library
// internals is false-positive-prone (verified empirically while writing this script — dist/index.cjs
// matched /cloud/i and /stripe/i purely from unrelated bundled-dependency text, and this package's
// own agent.schema.json legitimately uses "CLOUD" as a LOCAL/CLOUD execution-mode enum value,
// unrelated to the AgentForge4j Cloud product layer). Scanning our own source avoids both classes of
// false positive. The term list is ExecutionEstimatorForbiddenTermTest's calibrated monetization
// vocabulary (design §13) minus its two currency-symbol entries ("€", "$") plus "clerk" per this
// workflow's own design section. The symbols are deliberately dropped, not missed: every pattern
// below is \b-wrapped, and \b cannot anchor meaningfully around a bare symbol character — applied to
// "$" it would either never match or false-positive on every "${...}" template literal in this
// codebase.
//
// The RetryPolicy field check below is the one deliberate exception to the src-only rule: it DOES
// scan the packed ESM and CJS entry artifacts (dist/index.js, dist/index.cjs), including their
// bundled copy of workflow.schema.json. Unlike the generic terms above, "allowAgentSwap"/
// "allowPromptOverride" are specific enough identifiers that the false-positive risk documented
// above does not apply (no vendored dependency in this package's tree has any reason to contain
// either string), and the whole point of this check is that the retired fields must be gone from
// what actually ships — including the schema copy bundled at build time from whatever
// agentforge4j-schema/.../workflow.schema.json this build was synced against — not merely absent
// from this package's own hand-written source.

import { execSync } from 'node:child_process';
import { globSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const FORBIDDEN_TERMS = [
  'credit',
  'credits',
  'billing',
  'subscription',
  'payment',
  'ledger',
  'entitlement',
  'reservation',
  'admission',
  'pricing',
  'currency',
  'clerk',
  'changes.md',
].map((term) => new RegExp(`\\b${term.replace('.', '\\.')}\\b`, 'i'));

// A source-level import, not merely the substring appearing anywhere (e.g. in a comment).
const FORBIDDEN_IMPORT_PATTERNS = [
  /\bfrom\s+["']react-router/,
  /\brequire\(["']react-router/,
  /\bfrom\s+["']axios["']/,
  /\brequire\(["']axios["']\)/,
];

// RetryPolicy shrank from five fields to three; these two must never reach a published artifact
// again, in either the exporter's own emitted output or the bundled schema copy that governs
// import-side validation.
const RETIRED_RETRY_POLICY_FIELDS = ['allowAgentSwap', 'allowPromptOverride'];
const RETRY_POLICY_ARTIFACT_PATHS = ['dist/index.js', 'dist/index.cjs'];

// Everything npm would ship beyond package.json/LICENSE/README (always included) must live under
// dist/ — no accidental src/, tests/, or config-file leakage into the published tarball.
function isOutsideAllowlist(relativePath) {
  const ALWAYS_INCLUDED = new Set(['package.json', 'LICENSE', 'README.md']);
  return !ALWAYS_INCLUDED.has(relativePath) && !relativePath.startsWith('dist/');
}

function packedFiles() {
  // execSync (not execFileSync): npm resolves to a .cmd shell script on Windows, which
  // execFileSync cannot invoke without a shell regardless of the exact binary name passed.
  const raw = execSync('npm pack --dry-run --json', { cwd: packageRoot, encoding: 'utf8' });
  const [{ files }] = JSON.parse(raw);
  return files.map((entry) => entry.path);
}

function sourceFiles() {
  return globSync('src/**/*.{ts,tsx}', { cwd: packageRoot });
}

// Checks the packed ESM and CJS entry artifacts — not source — for the two retired RetryPolicy
// fields, including whatever workflow.schema.json copy tsup bundled into them at build time. Skips
// silently (rather than failing) when a path isn't in the packed set or hasn't been built yet
// (`npm run build` must run before this script for the check to be meaningful; wired that way in
// release-builder.yml), since packedFiles() already separately enforces that only dist/ ships.
function retiredRetryPolicyFieldViolations(packed) {
  const violations = [];
  const packedSet = new Set(packed);
  for (const relativePath of RETRY_POLICY_ARTIFACT_PATHS) {
    if (!packedSet.has(relativePath)) {
      continue;
    }
    const content = readFileSync(resolve(packageRoot, relativePath), 'utf8');
    for (const field of RETIRED_RETRY_POLICY_FIELDS) {
      if (content.includes(field)) {
        violations.push(`${relativePath}: retired RetryPolicy field "${field}" still present in the built artifact`);
      }
    }
  }
  return violations;
}

function main() {
  const violations = [];
  const packed = packedFiles();

  for (const relativePath of packed) {
    if (isOutsideAllowlist(relativePath)) {
      violations.push(`${relativePath}: packed file outside the dist/-only allowlist`);
    }
  }

  for (const relativePath of sourceFiles()) {
    const content = readFileSync(resolve(packageRoot, relativePath), 'utf8');
    for (const pattern of FORBIDDEN_TERMS) {
      if (pattern.test(content)) {
        violations.push(`${relativePath}: forbidden term matching ${pattern}`);
      }
    }
    for (const pattern of FORBIDDEN_IMPORT_PATTERNS) {
      if (pattern.test(content)) {
        violations.push(`${relativePath}: forbidden import matching ${pattern}`);
      }
    }
  }

  violations.push(...retiredRetryPolicyFieldViolations(packed));

  if (violations.length > 0) {
    console.error(`npm-pack-inspect: ${violations.length} violation(s):`);
    for (const violation of violations) {
      console.error(`  - ${violation}`);
    }
    process.exitCode = 1;
    return;
  }

  console.log('npm-pack-inspect: clean.');
}

main();
