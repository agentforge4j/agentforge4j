// SPDX-License-Identifier: Apache-2.0
//
// Playwright globalSetup for playwright.visual.config.ts — runs clean-output.mjs's clearing logic
// exactly once, in the main process, before any worker starts, regardless of how this config was
// invoked. Closes the gap the previsual:capture npm pre-hook alone left open: `npm run
// visual:capture` runs that pre-hook, but `npx playwright test --config=playwright.visual.config.ts`
// (an equally natural entry point from an IDE or a manual debug run) bypasses npm pre-hooks
// entirely and used to skip the clear step, silently letting stale results/ai-review-results.json
// from a prior run contaminate the new one.

import { clearVisualOutput } from './clean-output.mjs';

export default async function globalSetup() {
  clearVisualOutput();
}
