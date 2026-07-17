// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { validateReportForAttestation } from '../../scripts/visual/write-attestation.mjs';

function validReport(overrides: Record<string, unknown> = {}) {
  return {
    generatedAt: '2026-07-17T00:00:00.000Z',
    commitSha: 'a'.repeat(40),
    manifestHash: 'b'.repeat(64),
    viewportsCaptured: ['laptop'],
    summary: {
      totalCaptures: 73,
      overallStatus: 'pass',
    },
    ...overrides,
  };
}

test.describe('validateReportForAttestation — independent rejection, defence in depth', () => {
  test('a well-formed report with real captures and expected-inventory.json present is accepted', () => {
    expect(validateReportForAttestation(validReport(), true)).toEqual({ ok: true });
  });

  test('rejects when expected-inventory.json is missing, even with a well-formed report', () => {
    const result = validateReportForAttestation(validReport(), false);
    expect(result.ok).toBe(false);
    expect(result.error).toContain('expected-inventory.json is missing');
  });

  test('rejects zero totalCaptures', () => {
    const result = validateReportForAttestation(validReport({ summary: { totalCaptures: 0, overallStatus: 'fail' } }), true);
    expect(result.ok).toBe(false);
    expect(result.error).toContain('zero');
  });

  test('rejects a non-numeric totalCaptures', () => {
    const result = validateReportForAttestation(
      validReport({ summary: { totalCaptures: '73', overallStatus: 'pass' } }),
      true,
    );
    expect(result.ok).toBe(false);
    expect(result.error).toContain('non-numeric');
  });

  test('rejects a missing summary.overallStatus', () => {
    const result = validateReportForAttestation(validReport({ summary: { totalCaptures: 73 } }), true);
    expect(result.ok).toBe(false);
    expect(result.error).toContain('overallStatus');
  });

  test('rejects a missing commitSha', () => {
    const report = validReport();
    delete (report as Record<string, unknown>).commitSha;
    const result = validateReportForAttestation(report, true);
    expect(result.ok).toBe(false);
    expect(result.error).toContain('commitSha');
  });

  test('rejects a missing manifestHash', () => {
    const report = validReport();
    delete (report as Record<string, unknown>).manifestHash;
    const result = validateReportForAttestation(report, true);
    expect(result.ok).toBe(false);
    expect(result.error).toContain('manifestHash');
  });

  test('rejects a non-array viewportsCaptured', () => {
    const result = validateReportForAttestation(validReport({ viewportsCaptured: 'laptop' }), true);
    expect(result.ok).toBe(false);
    expect(result.error).toContain('viewportsCaptured');
  });

  test('rejects a completely empty report object', () => {
    const result = validateReportForAttestation({}, true);
    expect(result.ok).toBe(false);
  });
});
