// SPDX-License-Identifier: Apache-2.0

import { defineConfig, devices } from '@playwright/test';

/**
 * Separate config file (not a second `projects` entry in `playwright.config.ts`) because
 * Playwright starts every configured `webServer` unconditionally regardless of which project
 * `--project` selects — a single shared config would force the `builder` CI job to also build
 * and boot this site, and vice versa. Two independent configs keep the two targets' CI cost
 * decoupled, matching this repo's existing "avoid complicating unrelated builds" convention
 * (see `.github/workflows/ui-e2e.yml`'s own path-filtered `changes` job).
 *
 * Server model: `agentforge4j-web-ui` has its own `preview` script (unlike the builder, which
 * doesn't) — run the real production build, then serve it, matching the Day 1 review brief's
 * instruction to test "the production-style built or preview site". `hosting.spec.ts` runs its
 * own separate static server against the same build output to verify the GitHub-Pages-specific
 * 404 mechanism, which `vite preview`'s own SPA-fallback middleware would otherwise mask.
 */
const WEB_UI_DIR = '../agentforge4j-web-ui';
const WEB_UI_PORT = 4183;
const WEB_UI_URL = `http://localhost:${WEB_UI_PORT}`;

const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: './specs/web-ui',
  outputDir: './test-results/web-ui',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI
    ? [['github'], ['html', { open: 'never', outputFolder: 'playwright-report/web-ui' }]]
    : [['list'], ['html', { open: 'never', outputFolder: 'playwright-report/web-ui' }]],
  use: {
    baseURL: WEB_UI_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      name: 'web-ui',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: `npm run build && npm run preview -- --port ${WEB_UI_PORT} --strictPort`,
    cwd: WEB_UI_DIR,
    url: WEB_UI_URL,
    reuseExistingServer: !isCI,
    timeout: 180_000,
  },
});
