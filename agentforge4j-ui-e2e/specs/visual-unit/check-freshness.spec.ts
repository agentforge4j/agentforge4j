// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { RELEVANT_PATH_PATTERN } from '../../scripts/visual/check-freshness.mjs';

test.describe('RELEVANT_PATH_PATTERN — attestation freshness change-detection', () => {
  test('an attestation-only commit does NOT register as a relevant UI change — the circular self-invalidation bug this closes', () => {
    // Committing the attestation file is itself a change under agentforge4j-ui-e2e/ — widening the
    // pattern from `agentforge4j-ui-e2e/visual/` to the whole tree (to also catch scripts/specs/
    // support changes) reintroduced the exact bug main()'s own "Deliberately NOT commitSha !==
    // currentSha" comment already describes: an attestation-only commit would predate ITSELF.
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/visual-evidence/attestation.json')).toBe(false);
  });

  test('a genuine manifest change still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/visual/manifest.ts')).toBe(true);
  });

  test('a change to the capture orchestrator still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/specs/visual/capture.spec.ts')).toBe(true);
  });

  test('a change to the local static server still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/scripts/visual/serve-assembled-site.mjs')).toBe(true);
  });

  test('a change to the manifest\'s own route data source still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/support/web-ui/routes.ts')).toBe(true);
  });

  test('a Node version bump (.nvmrc) still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('.nvmrc')).toBe(true);
  });

  test('a change to the ui-e2e CI workflow itself still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('.github/workflows/ui-e2e.yml')).toBe(true);
  });

  test('an unrelated repo path does not register as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('designs/agent-checkin.md')).toBe(false);
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-core/src/main/java/Foo.java')).toBe(false);
  });
});
