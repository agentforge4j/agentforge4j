// SPDX-License-Identifier: Apache-2.0

import { defineConfig } from '@playwright/test';

/**
 * Pure-logic unit tests for `visual/checks.ts` and `visual/manifest.ts` — no `page` fixture, no
 * browser, no `webServer`. A dedicated config (not folded into `playwright.visual.config.ts`)
 * specifically so these run instantly and never pay that config's static-server/dev-harness
 * `webServer` startup cost; Playwright is reused here as the test runner (rather than adding a
 * second, non-Playwright TS test framework to this module) purely because it already transpiles
 * this module's TypeScript correctly and consistently.
 */
export default defineConfig({
  testDir: './specs/visual-unit',
  outputDir: './test-results/visual-unit',
  fullyParallel: true,
  reporter: [['list']],
});
