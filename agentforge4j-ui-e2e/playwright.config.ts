// SPDX-License-Identifier: Apache-2.0

import { defineConfig, devices } from '@playwright/test';

/**
 * Single Playwright config for the AgentForge4j UI suite. Projects are added per target as each
 * becomes release-critical; v1 ships the `builder` project only (drives the workflow-builder
 * library through its own `dev/` harness). The `web-ui` project is deferred (additive Phase 4).
 *
 * Server model: this config deliberately runs the dev server (`npm run dev`, vite, port 5173),
 * not a production-style build — the production-style build+preview path belongs to
 * `playwright.builder-functional.config.ts`. `BROWSER=none` stops vite opening a tab in
 * CI/headless.
 */
const BUILDER_DIR = '../agentforge4j-workflow-builder';
const BUILDER_PORT = 5173;
const BUILDER_URL = `http://localhost:${BUILDER_PORT}`;

const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: './specs',
  outputDir: './test-results',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BUILDER_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      name: 'builder',
      testDir: './specs/builder',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 800 },
      },
    },
  ],
  webServer: {
    command: 'npm run dev',
    cwd: BUILDER_DIR,
    url: BUILDER_URL,
    reuseExistingServer: !isCI,
    timeout: 120_000,
    env: { BROWSER: 'none' },
  },
});
