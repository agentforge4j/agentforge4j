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
// scan every packed dist/ JavaScript artifact — the ESM entry, the CJS entry, and every code-split
// chunk either format produces — including their bundled copy of workflow.schema.json. Unlike the
// generic terms above, "allowAgentSwap"/"allowPromptOverride" are specific enough identifiers that
// the false-positive risk documented above does not apply (no vendored dependency in this
// package's tree has any reason to contain either string), and the whole point of this check is
// that the retired fields must be gone from what actually ships — including the schema copy
// bundled at build time from whatever agentforge4j-schema/.../workflow.schema.json this build was
// synced against — not merely absent from this package's own hand-written source.
//
// Scanning only the two entry files (dist/index.js, dist/index.cjs) is not enough: tsup code-splits
// the ESM build (this package's entry point lazily imports its zip/download I/O), so the code that
// actually builds RetryPolicy objects — and the bundled schema copy — can land in a separate,
// content-hashed chunk file (e.g. dist/chunk-AIJEKIZS.js) that the ESM entry file itself never
// contains. A scan limited to the two fixed entry paths would silently pass while a regression sat
// in a chunk the whole time; every packed .js/.cjs/.mjs file under dist/ must be scanned instead.

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
export const RETIRED_RETRY_POLICY_FIELDS = ['allowAgentSwap', 'allowPromptOverride'];

// The build's ESM and CJS entry points. Every packed set must contain both — if either is
// missing, no build ran (or ran incompletely) and this check verified nothing for that format.
export const RETRY_POLICY_REQUIRED_ENTRY_ARTIFACTS = ['dist/index.js', 'dist/index.cjs'];

// Any packed dist/ file in one of these formats is a candidate the RetryPolicy fields could have
// been bundled into — the two fixed entry points above, plus every code-split chunk either format
// produces (tsup names ESM chunks like dist/chunk-<hash>.js or dist/<name>-<hash>.js; a .mjs
// extension is scanned too in case a future tsup/entry config emits one).
const RETRY_POLICY_ARTIFACT_EXTENSIONS = ['.js', '.cjs', '.mjs'];

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

// Every packed dist/ JavaScript artifact — the fixed entry points plus every code-split chunk
// either format produced for this build. Not a fixed list: chunk filenames are content-hashed and
// change on every build, so the scan target set is derived from the actual packed output.
export function retryPolicyScanTargets(packed) {
  return packed.filter(
    (relativePath) =>
      relativePath.startsWith('dist/') &&
      RETRY_POLICY_ARTIFACT_EXTENSIONS.some((extension) => relativePath.endsWith(extension)),
  );
}

// Checks every packed dist/ JavaScript artifact — not just the ESM/CJS entry points, and not
// source — for the two retired RetryPolicy fields, including whatever workflow.schema.json copy
// tsup bundled into any of them at build time. Fails closed, not silently, when an expected entry
// artifact is missing from the packed set, or when the packed set has no scannable dist/ artifact
// at all: `npm run build` must run before this script for the check to be meaningful (wired that
// way in release-builder.yml), and either condition means this check verified nothing for that
// format — that must be loud, not a quiet pass, or a reordered/skipped build step, or a regression
// confined to a chunk file the entry points don't reach, would silently defeat the whole point of
// this check.
export function retiredRetryPolicyFieldViolations(packed, root) {
  const violations = [];
  const packedSet = new Set(packed);

  for (const relativePath of RETRY_POLICY_REQUIRED_ENTRY_ARTIFACTS) {
    if (!packedSet.has(relativePath)) {
      violations.push(
        `${relativePath}: expected packed entry artifact not found — run "npm run build" before this script`,
      );
    }
  }

  const scanTargets = retryPolicyScanTargets(packed);
  if (scanTargets.length === 0) {
    violations.push(
      'dist/*.{js,cjs,mjs}: no packed artifact found to scan for retired RetryPolicy fields — run "npm run build" before this script',
    );
    return violations;
  }

  for (const relativePath of scanTargets) {
    const content = readFileSync(resolve(root, relativePath), 'utf8');
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

  violations.push(...retiredRetryPolicyFieldViolations(packed, packageRoot));

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

// Guards direct CLI execution (`node npm-pack-inspect.mjs`) so a test can import this module's
// exported functions — e.g. retiredRetryPolicyFieldViolations against a synthetic packed set —
// without triggering a real `npm pack` invocation as a side effect of the import.
const isMainModule = process.argv[1] != null && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1]);
if (isMainModule) {
  main();
}
