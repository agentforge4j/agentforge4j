// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { VISUAL_MANIFEST, validateManifest, type VisualManifestEntry } from '../../visual/manifest';

function entry(overrides: Partial<VisualManifestEntry>): VisualManifestEntry {
  return {
    id: 'fixture',
    surface: 'org',
    target: { kind: 'route', path: '/' },
    stateName: 'Fixture',
    viewports: ['laptop'],
    aiReviewEnabled: false,
    releaseImportance: 'nice-to-have',
    fullPage: true,
    ...overrides,
  };
}

test.describe('validateManifest', () => {
  test('the real manifest is internally consistent', () => {
    expect(() => validateManifest()).not.toThrow();
  });

  test('the real manifest is non-empty and covers both surfaces', () => {
    expect(VISUAL_MANIFEST.length).toBeGreaterThan(0);
    expect(VISUAL_MANIFEST.some((e) => e.surface === 'org')).toBe(true);
    expect(VISUAL_MANIFEST.some((e) => e.surface === 'builder')).toBe(true);
  });

  test('rejects a duplicate id', () => {
    const broken = [entry({ id: 'dup' }), entry({ id: 'dup' })];
    expect(() => validateManifest(broken)).toThrow(/duplicate id/);
  });

  test('rejects an unknown viewport name', () => {
    const broken = [entry({ viewports: ['not-a-real-viewport'] })];
    expect(() => validateManifest(broken)).toThrow(/unknown viewport/);
  });

  test('rejects an entry with zero viewports', () => {
    const broken = [entry({ viewports: [] })];
    expect(() => validateManifest(broken)).toThrow(/no viewports/);
  });

  test('every entry with knownIssues references at least one issue number', () => {
    for (const e of VISUAL_MANIFEST) {
      if (e.knownIssues) {
        expect(e.knownIssues.length).toBeGreaterThan(0);
      }
    }
  });

  test('rejects an entry using a delivery-tolerant interaction with no minNodeCount', () => {
    // addSampleSteps calls interactions.ts's addStep(), which can silently add nothing on a known,
    // tolerated mobile delivery failure — safe ONLY because minNodeCount independently catches it.
    // Omitting minNodeCount here must fail loudly, not silently reintroduce the "empty canvas
    // indistinguishable from correctly populated" bug this whole mechanism exists to close.
    const broken = [entry({ interaction: 'addSampleSteps' })];
    expect(() => validateManifest(broken)).toThrow(/minNodeCount must be set/);
  });

  test('accepts an entry using a delivery-tolerant interaction when minNodeCount is set', () => {
    const ok = [entry({ interaction: 'addSampleSteps', minNodeCount: 2 })];
    expect(() => validateManifest(ok)).not.toThrow();
  });

  test('every real manifest entry using a delivery-tolerant interaction sets minNodeCount', () => {
    // Direct, explicit coverage of the same invariant validateManifest() enforces above, against
    // the real manifest rather than a synthetic fixture.
    for (const e of VISUAL_MANIFEST) {
      if (e.interaction === 'addSampleSteps' || e.interaction === 'addStepAndSelectNode'
        || e.interaction === 'addUnconfiguredDecisionStep' || e.interaction === 'addSampleStepsAndExport') {
        expect(e.minNodeCount, `entry "${e.id}" uses "${e.interaction}" without minNodeCount`).toBeDefined();
      }
    }
  });
});
