// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { classifyFailingChecks, hasNoEvidence } from '../../scripts/visual/generate-report.mjs';

function capture(overrides: Record<string, unknown> = {}) {
  return {
    entryId: 'fixture',
    viewport: 'laptop',
    knownIssues: [],
    acceptedFindings: [],
    checks: [{ id: 'headings-present', status: 'pass' }],
    ...overrides,
  };
}

test.describe('classifyFailingChecks — per-check exemption (not entry-level)', () => {
  test('a clean capture blocks nothing (nothing failed)', () => {
    const result = classifyFailingChecks(capture());
    expect(result.blocking).toHaveLength(0);
    expect(result.nonBlocking).toHaveLength(0);
  });

  test('a failing check with no classification at all blocks', () => {
    const result = classifyFailingChecks(
      capture({ checks: [{ id: 'headings-present', status: 'fail', detail: 'x' }] }),
    );
    expect(result.blocking).toHaveLength(1);
    expect(result.blocking[0].check.id).toBe('headings-present');
  });

  test('knownIssues ALONE does not exempt a failing check — the real bug this fix closes', () => {
    // Before the fix, tagging an entry with any knownIssues number exempted EVERY failing check on
    // it, regardless of whether that check had anything to do with the tagged issue.
    const result = classifyFailingChecks(
      capture({
        knownIssues: [99],
        checks: [{ id: 'main-content-not-blank', status: 'fail', detail: 'unrelated blank page' }],
      }),
    );
    expect(result.blocking).toHaveLength(1);
    expect(result.blocking[0].check.id).toBe('main-content-not-blank');
    expect(result.nonBlocking).toHaveLength(0);
  });

  test('a specific acceptedFindings entry exempts only the matching checkId', () => {
    const result = classifyFailingChecks(
      capture({
        acceptedFindings: [{ checkId: 'no-clipped-panels', reason: 'expected', issue: 99 }],
        checks: [
          { id: 'no-clipped-panels', status: 'fail', detail: 'expected clipping' },
          { id: 'headings-present', status: 'fail', detail: 'unrelated real bug' },
        ],
      }),
    );
    expect(result.blocking).toHaveLength(1);
    expect(result.blocking[0].check.id).toBe('headings-present');
    expect(result.nonBlocking).toHaveLength(1);
    expect(result.nonBlocking[0].check.id).toBe('no-clipped-panels');
    expect(result.nonBlocking[0].classification.issue).toBe(99);
  });

  test('acceptedFindings combined with knownIssues still only exempts the named checkId', () => {
    const result = classifyFailingChecks(
      capture({
        knownIssues: [99, 104],
        acceptedFindings: [{ checkId: 'no-clipped-panels', reason: 'expected' }],
        checks: [
          { id: 'no-clipped-panels', status: 'fail' },
          { id: 'primary-actions-visible', status: 'fail' },
        ],
      }),
    );
    expect(result.blocking.map((b: { check: { id: string } }) => b.check.id)).toEqual(['primary-actions-visible']);
    expect(result.nonBlocking.map((b: { check: { id: string } }) => b.check.id)).toEqual(['no-clipped-panels']);
  });

  test('a viewport-scoped acceptedFindings entry exempts only on the matching viewport', () => {
    const mobileFailure = capture({
      viewport: 'mobile',
      acceptedFindings: [{ checkId: 'canvas-node-count', reason: 'mobile-only defect', viewports: ['mobile'] }],
      checks: [{ id: 'canvas-node-count', status: 'fail', detail: 'expected at least 2, found 0' }],
    });
    expect(classifyFailingChecks(mobileFailure).nonBlocking).toHaveLength(1);

    // The SAME checkId failing on a DIFFERENT viewport is NOT covered by a mobile-scoped entry —
    // this is the real bug a viewport-unaware exemption would reintroduce: a genuinely new,
    // unrelated desktop failure on the same check id must still block.
    const laptopFailure = capture({
      viewport: 'laptop',
      acceptedFindings: [{ checkId: 'canvas-node-count', reason: 'mobile-only defect', viewports: ['mobile'] }],
      checks: [{ id: 'canvas-node-count', status: 'fail', detail: 'expected at least 2, found 0' }],
    });
    const laptopResult = classifyFailingChecks(laptopFailure);
    expect(laptopResult.blocking).toHaveLength(1);
    expect(laptopResult.nonBlocking).toHaveLength(0);
  });

  test('an acceptedFindings entry with no viewports restriction applies to every viewport', () => {
    const result = classifyFailingChecks(
      capture({
        viewport: 'tablet-portrait',
        acceptedFindings: [{ checkId: 'headings-present', reason: 'expected everywhere' }],
        checks: [{ id: 'headings-present', status: 'fail' }],
      }),
    );
    expect(result.nonBlocking).toHaveLength(1);
  });
});

test.describe('hasNoEvidence — must never let a zero-evidence run report as pass', () => {
  test('missing expected-inventory.json alone is no evidence, even with captures present', () => {
    // Defensive: even if stale result files from an unrelated run somehow survived, no
    // expected-inventory.json means visual:capture did not run THIS pass, so integrity can't be
    // proven either way.
    expect(hasNoEvidence({ hasExpectedInventory: false, totalCaptures: 5 })).toBe(true);
  });

  test('zero captures alone is no evidence, even with expected-inventory.json present', () => {
    expect(hasNoEvidence({ hasExpectedInventory: true, totalCaptures: 0 })).toBe(true);
  });

  test('both missing is no evidence', () => {
    expect(hasNoEvidence({ hasExpectedInventory: false, totalCaptures: 0 })).toBe(true);
  });

  test('expected-inventory.json present and captures non-zero is real evidence', () => {
    expect(hasNoEvidence({ hasExpectedInventory: true, totalCaptures: 73 })).toBe(false);
  });
});
