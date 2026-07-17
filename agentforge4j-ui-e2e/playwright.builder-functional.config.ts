// SPDX-License-Identifier: Apache-2.0

import { defineConfig } from '@playwright/test';
import { FUNCTIONAL_VIEWPORTS } from './support/viewports';

/**
 * Day 4 functional testkit — separate config file (not a `playwright.config.ts` project) for the
 * same reason `playwright.web-ui.config.ts` is separate: Playwright starts every configured
 * `webServer` unconditionally regardless of `--project`, so folding this in would force the
 * existing `builder` (dev-server) CI job to also build+preview this target, and vice versa.
 *
 * Server model: unlike `playwright.config.ts` (which runs `npm run dev`, the dev server, since
 * the builder previously had no `preview` script), this config runs the dev harness through a
 * real production-style build (`vite build` + `vite preview`) — Objective 1 ("runs against the
 * real built Workflow Builder") and Task 9 ("uses a production-style build"). The dev-harness
 * source itself (React component tree) is identical either way; this only changes whether it's
 * served pre-bundled/minified or through Vite's dev transform pipeline.
 */
const BUILDER_DIR = '../agentforge4j-workflow-builder';
const PORT = 4193;
const URL = `http://localhost:${PORT}`;

const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: './specs/builder-functional',
  outputDir: './test-results/builder-functional',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI
    ? [['github'], ['html', { open: 'never', outputFolder: 'playwright-report/builder-functional' }]]
    : [['list'], ['html', { open: 'never', outputFolder: 'playwright-report/builder-functional' }]],
  use: {
    baseURL: URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      // Full critical-journey coverage. Excludes @tablet-only/@mobile-only (narrow-viewport-
      // specific scenarios aren't run twice at the wrong viewport) and @known-issue (quarantined
      // tests — see support/known-issues.ts — never run as part of normal CI; only
      // `npm run test:e2e:builder-functional:known-issues`'s direct --grep picks them up).
      name: 'desktop',
      grepInvert: /@tablet-only|@mobile-only|@known-issue/,
      use: { viewport: FUNCTIONAL_VIEWPORTS.desktop },
    },
    {
      // Targeted responsive journeys only (Task 6: "do not duplicate every test at every
      // viewport") — tests tagged @responsive, plus anything tagged @tablet-only.
      name: 'tablet',
      grep: /@responsive|@tablet-only/,
      grepInvert: /@mobile-only|@known-issue/,
      use: { viewport: FUNCTIONAL_VIEWPORTS.tablet },
    },
    {
      // Targeted responsive journeys + the mobile-gate known-problem scenarios.
      name: 'mobile',
      grep: /@responsive|@mobile-only/,
      grepInvert: /@tablet-only|@known-issue/,
      use: { viewport: FUNCTIONAL_VIEWPORTS.mobileStandard },
    },
    {
      // The strict command's target (`npm run test:e2e:builder-functional:known-issues`) —
      // quarantined tests only (support/known-issues.ts's knownIssueTest), the exact complement
      // of the three projects above, which all grepInvert this same tag out. A dedicated project
      // (not just a bare --grep on the other projects) because each of those already carries its
      // own grepInvert excluding @known-issue — Playwright ANDs a CLI --grep with a project's own
      // grep/grepInvert, so without this project a --grep @known-issue invocation would always
      // resolve to zero tests, defeating the point of a *strict* command.
      name: 'known-issues',
      grep: /@known-issue/,
      use: { viewport: FUNCTIONAL_VIEWPORTS.desktop },
    },
  ],
  webServer: {
    command: `npm run build:dev-harness && npm run preview -- --port ${PORT} --strictPort`,
    cwd: BUILDER_DIR,
    url: URL,
    reuseExistingServer: !isCI,
    timeout: 180_000,
  },
});
