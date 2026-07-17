// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { evaluateSiteProvenance } from '../../scripts/visual/site-provenance.mjs';

const COMMIT_A = 'a'.repeat(40);
const COMMIT_B = 'b'.repeat(40);

test.describe('evaluateSiteProvenance — release-check.mjs must never trust a stale or missing assembled site', () => {
  test('no marker at all is stale — missing assembled-site evidence', () => {
    const result = evaluateSiteProvenance(null, COMMIT_A, []);
    expect(result.fresh).toBe(false);
    expect(result.reason).toContain('no build-provenance marker found');
  });

  test('a marker built from an older commit is stale', () => {
    const result = evaluateSiteProvenance({ commitSha: COMMIT_A, dirtyRelevantFilesAtBuildTime: [] }, COMMIT_B, []);
    expect(result.fresh).toBe(false);
    expect(result.reason).toContain('not the current');
  });

  test('a marker built while relevant files were dirty is stale, even if the commit matches now', () => {
    const result = evaluateSiteProvenance(
      { commitSha: COMMIT_A, dirtyRelevantFilesAtBuildTime: ['agentforge4j-web-ui/src/App.tsx'] },
      COMMIT_A,
      [],
    );
    expect(result.fresh).toBe(false);
    expect(result.reason).toContain('uncommitted');
  });

  test('relevant files dirty right NOW (since the build) makes an otherwise-matching marker stale', () => {
    const result = evaluateSiteProvenance(
      { commitSha: COMMIT_A, dirtyRelevantFilesAtBuildTime: [] },
      COMMIT_A,
      ['agentforge4j-web-ui/src/pages/BuilderPage.tsx'],
    );
    expect(result.fresh).toBe(false);
    expect(result.reason).toContain('currently uncommitted');
  });

  test('a marker with a missing commitSha field is stale', () => {
    const result = evaluateSiteProvenance({ dirtyRelevantFilesAtBuildTime: [] }, COMMIT_A, []);
    expect(result.fresh).toBe(false);
  });

  test('a marker with a missing dirtyRelevantFilesAtBuildTime field is stale', () => {
    const result = evaluateSiteProvenance({ commitSha: COMMIT_A }, COMMIT_A, []);
    expect(result.fresh).toBe(false);
  });

  test('a marker matching the current commit, built clean, with nothing dirty now, is fresh', () => {
    const result = evaluateSiteProvenance({ commitSha: COMMIT_A, dirtyRelevantFilesAtBuildTime: [] }, COMMIT_A, []);
    expect(result).toEqual({ fresh: true });
  });
});
