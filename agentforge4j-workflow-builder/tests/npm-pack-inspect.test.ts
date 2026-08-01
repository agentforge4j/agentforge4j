// SPDX-License-Identifier: Apache-2.0

import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  RETIRED_RETRY_POLICY_FIELDS,
  retiredRetryPolicyFieldViolations,
  retryPolicyScanTargets,
} from '../scripts/npm-pack-inspect.mjs';

describe('retryPolicyScanTargets', () => {
  it('selects every packed dist/ .js, .cjs, and .mjs artifact, excluding source maps and declarations', () => {
    const packed = [
      'dist/index.js',
      'dist/index.js.map',
      'dist/index.cjs',
      'dist/index.cjs.map',
      'dist/index.d.ts',
      'dist/index.d.cts',
      'dist/chunk-AIJEKIZS.js',
      'dist/chunk-AIJEKIZS.js.map',
      'dist/zip-QSKTDVC3.mjs',
      'dist/index.css',
      'src/index.ts',
      'package.json',
    ];

    expect(retryPolicyScanTargets(packed)).toEqual([
      'dist/index.js',
      'dist/index.cjs',
      'dist/chunk-AIJEKIZS.js',
      'dist/zip-QSKTDVC3.mjs',
    ]);
  });
});

describe('retiredRetryPolicyFieldViolations', () => {
  let root: string;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'npm-pack-inspect-'));
    mkdirSync(join(root, 'dist'), { recursive: true });
  });

  afterEach(() => {
    rmSync(root, { recursive: true, force: true });
  });

  function writeDist(relativePath: string, content: string): void {
    writeFileSync(join(root, relativePath), content, 'utf8');
  }

  it('reports no violations when every scanned artifact is clean', () => {
    writeDist('dist/index.js', 'export const clean = true;');
    writeDist('dist/index.cjs', 'exports.clean = true;');

    const violations = retiredRetryPolicyFieldViolations(['dist/index.js', 'dist/index.cjs'], root);

    expect(violations).toEqual([]);
  });

  it('fails closed when the ESM entry artifact is missing from the packed set', () => {
    writeDist('dist/index.cjs', 'exports.clean = true;');

    const violations = retiredRetryPolicyFieldViolations(['dist/index.cjs'], root);

    expect(violations.some((v) => v.startsWith('dist/index.js:'))).toBe(true);
  });

  it('fails closed when the CJS entry artifact is missing from the packed set', () => {
    writeDist('dist/index.js', 'export const clean = true;');

    const violations = retiredRetryPolicyFieldViolations(['dist/index.js'], root);

    expect(violations.some((v) => v.startsWith('dist/index.cjs:'))).toBe(true);
  });

  it('fails closed when the packed set has no scannable dist/ artifact at all', () => {
    const violations = retiredRetryPolicyFieldViolations([], root);

    expect(violations.length).toBeGreaterThan(0);
    expect(violations.some((v) => v.includes('no packed artifact found to scan'))).toBe(true);
  });

  it('detects a retired field in the CJS entry artifact', () => {
    writeDist('dist/index.js', 'export const clean = true;');
    writeDist('dist/index.cjs', `exports.behaviour = { retryPolicy: { ${RETIRED_RETRY_POLICY_FIELDS[0]}: true } };`);

    const violations = retiredRetryPolicyFieldViolations(['dist/index.js', 'dist/index.cjs'], root);

    expect(violations).toEqual([
      `dist/index.cjs: retired RetryPolicy field "${RETIRED_RETRY_POLICY_FIELDS[0]}" still present in the built artifact`,
    ]);
  });

  it('mutation regression: detects a retired field confined to a split ESM chunk, independently of a clean CJS entry', () => {
    // Reproduces the exact CF-002 shape: tsup code-splits the ESM build, so the RetryPolicy code
    // (and the schema copy it carries) can land in a content-hashed chunk file the ESM entry point
    // itself never contains. Both entry points are clean here — only the chunk carries the retired
    // field — proving detection does not depend on the CJS bundle (which happens not to code-split)
    // catching the regression on the entry-scan's behalf.
    writeDist('dist/index.js', 'export * from "./chunk-AIJEKIZS.js";');
    writeDist('dist/index.cjs', 'exports.clean = true;');
    writeDist(
      'dist/chunk-AIJEKIZS.js',
      `export const retryPolicySimple = (n) => ({ allowRetry: true, ${RETIRED_RETRY_POLICY_FIELDS[1]}: false, maxAttempts: n });`,
    );

    const violations = retiredRetryPolicyFieldViolations(
      ['dist/index.js', 'dist/index.cjs', 'dist/chunk-AIJEKIZS.js'],
      root,
    );

    expect(violations).toEqual([
      `dist/chunk-AIJEKIZS.js: retired RetryPolicy field "${RETIRED_RETRY_POLICY_FIELDS[1]}" still present in the built artifact`,
    ]);
  });

  it('a scan limited to only the two fixed entry paths would have missed the chunk regression (control)', () => {
    // Negative control: proves the mutation above is real, by showing the pre-fix scan surface
    // (just the two hardcoded entry paths) reports nothing when only the chunk is tainted.
    writeDist('dist/index.js', 'export * from "./chunk-AIJEKIZS.js";');
    writeDist('dist/index.cjs', 'exports.clean = true;');
    writeDist(
      'dist/chunk-AIJEKIZS.js',
      `export const retryPolicySimple = (n) => ({ allowRetry: true, ${RETIRED_RETRY_POLICY_FIELDS[1]}: false, maxAttempts: n });`,
    );

    const entryOnlyViolations = retiredRetryPolicyFieldViolations(['dist/index.js', 'dist/index.cjs'], root);

    expect(entryOnlyViolations).toEqual([]);
  });
});
