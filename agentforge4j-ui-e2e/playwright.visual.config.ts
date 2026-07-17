// SPDX-License-Identifier: Apache-2.0

import { defineConfig, devices } from '@playwright/test';

/**
 * Separate config file, same isolation reasoning as `playwright.web-ui.config.ts` (Playwright
 * starts every configured `webServer` regardless of `--project` selection, so a shared config
 * would force every other CI job to also boot the assembled-site static server and the builder
 * dev harness). Two targets are needed here (unlike the other two configs, which each target
 * one): the composed `.org` site (`serve-assembled-site.mjs`, the real screenshot subject) and
 * the workflow-builder package's own dev harness (only for `builder-readonly-mode`, the one
 * manifest entry that is genuinely unreachable on the public site today — see
 * `visual/manifest.ts`'s comment on that entry).
 *
 * `VISUAL_TARGET_URL` overrides the assembled-site target entirely (e.g. to point at an
 * already-running server such as the Pi preview) — set it and this config skips starting its own
 * static server for that target, so the suite can either start a server or connect to one.
 */
const SITE_PORT = 4184;
const SITE_URL = process.env.VISUAL_TARGET_URL ?? `http://localhost:${SITE_PORT}`;
const usingExternalSiteTarget = Boolean(process.env.VISUAL_TARGET_URL);

const BUILDER_HARNESS_PORT = 5173;
export const BUILDER_HARNESS_URL = `http://localhost:${BUILDER_HARNESS_PORT}`;

const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: './specs/visual',
  outputDir: './test-results/visual',
  // Runs once, in the main process, before any worker starts — the clear-before-every-run
  // guarantee this depends on must hold regardless of invocation method (`npm run visual:capture`
  // or a direct `npx playwright test --config=...`), not just when the previsual:capture npm
  // pre-hook happens to have run. See scripts/visual/global-setup.mjs.
  globalSetup: './scripts/visual/global-setup.mjs',
  fullyParallel: true,
  forbidOnly: isCI,
  // Deliberately 0, not the other configs' CI retry allowance: a retry that happens to pass would
  // silently mask a real, reportable visual defect — the opposite of what this suite exists to
  // surface (never mask real problems merely to produce stable screenshots).
  retries: 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI ? [['github'], ['list']] : [['list']],
  use: {
    trace: 'off',
    // This suite takes its own explicit, manifest-driven screenshots (see capture.spec.ts) rather
    // than Playwright's automatic per-test failure screenshot.
    screenshot: 'off',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      name: 'visual',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    ...(usingExternalSiteTarget
      ? []
      : [
          {
            command: `node scripts/visual/serve-assembled-site.mjs --port ${SITE_PORT}`,
            url: SITE_URL,
            reuseExistingServer: true,
            timeout: 30_000,
          },
        ]),
    {
      command: 'npm run dev',
      cwd: '../agentforge4j-workflow-builder',
      url: BUILDER_HARNESS_URL,
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
});

export { SITE_URL };
