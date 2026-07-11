// SPDX-License-Identifier: Apache-2.0
//
// Runs before every builder release: fails if this package's own source carries a
// monetization/billing term or a router/HTTP-client import, or if `npm pack` would ship a file
// outside the declared allowlist. No automated forbidden-strings/package-content check existed on
// main before this script; net-new for Phase 3, not a port of an existing mechanism.
//
// Scans src/**, not the built dist/ bundle: tsup bundles third-party dependency code into dist/,
// and a blind substring scan over vendored library internals is false-positive-prone (verified
// empirically while writing this script — dist/index.cjs matched /cloud/i and /stripe/i purely
// from unrelated bundled-dependency text, and this package's own agent.schema.json legitimately
// uses "CLOUD" as a LOCAL/CLOUD execution-mode enum value, unrelated to the AgentForge4j Cloud
// product layer). Scanning our own source avoids both classes of false positive. The term list is
// ExecutionEstimatorForbiddenTermTest's calibrated monetization vocabulary (design §13) minus its
// two currency-symbol entries ("€", "$") plus "clerk" per this workflow's own design section. The
// symbols are deliberately dropped, not missed: every pattern below is \b-wrapped, and \b cannot
// anchor meaningfully around a bare symbol character — applied to "$" it would either never match
// or false-positive on every "${...}" template literal in this codebase.

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

function main() {
  const violations = [];

  for (const relativePath of packedFiles()) {
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
