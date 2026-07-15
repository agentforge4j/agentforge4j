// SPDX-License-Identifier: Apache-2.0
//
// Deterministic screenshot + non-AI visual-check runner (Day 2 Tasks 3 + 4). Combined into one
// Playwright pass, not two separate scripts: both need the same settled page, so running them
// together avoids a second, possibly-inconsistent navigation. Generates one test per manifest
// entry × viewport (see `visual/manifest.ts`) so Playwright's own reporter, retries (deliberately
// 0 — see `playwright.visual.config.ts`), and parallelism all apply per capture.
//
// Each test writes its OWN result file under `visual-output/results/` rather than accumulating
// into a shared in-memory array + `test.afterAll`: `fullyParallel` runs this spec across multiple
// worker processes, each with its own module instance, so a shared array only ever holds one
// worker's subset — `scripts/visual/generate-report.mjs` merges every file in that directory
// instead of expecting one already-consolidated result.

import { test } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { VISUAL_MANIFEST, validateManifest, type VisualManifestEntry } from '../../visual/manifest';
import { INTERACTIONS } from '../../visual/interactions';
import {
  collectDomFactsFromPage,
  evaluateDeterministicChecks,
  evaluateRuntimeSignals,
  type CheckResult,
  type RuntimeSignal,
} from '../../visual/checks';
import { VIEWPORTS } from '../../support/web-ui/routes';

validateManifest();

const SITE_URL = process.env.VISUAL_TARGET_URL ?? 'http://localhost:4184';
const BUILDER_HARNESS_URL = 'http://localhost:5173';

const OUTPUT_DIR = join(import.meta.dirname, '..', '..', 'visual-output');
const SCREENSHOTS_DIR = join(OUTPUT_DIR, 'screenshots');
const RESULTS_DIR = join(OUTPUT_DIR, 'results');
mkdirSync(SCREENSHOTS_DIR, { recursive: true });
mkdirSync(RESULTS_DIR, { recursive: true });

export interface CaptureRecord {
  readonly entryId: string;
  readonly surface: string;
  readonly stateName: string;
  readonly releaseImportance: string;
  readonly knownIssues: readonly number[];
  readonly viewport: string;
  /** Relative to `visual-output/`, forward-slashed regardless of OS. */
  readonly screenshotPath: string;
  readonly checks: readonly CheckResult[];
  readonly overallStatus: 'pass' | 'fail';
  readonly aiReviewEnabled: boolean;
  readonly notes?: string;
}

function targetUrl(entry: VisualManifestEntry): string {
  if (entry.target.kind === 'route' || entry.target.kind === 'builder-route') {
    return `${SITE_URL}${entry.target.path}`;
  }
  return `${BUILDER_HARNESS_URL}/${entry.target.search}`;
}

const DISABLE_ANIMATIONS_CSS =
  '*, *::before, *::after { animation-duration: 0s !important; animation-delay: 0s !important; ' +
  'transition-duration: 0s !important; transition-delay: 0s !important; scroll-behavior: auto !important; }';

for (const entry of VISUAL_MANIFEST) {
  for (const viewportName of entry.viewports) {
    const viewport = VIEWPORTS.find((v) => v.name === viewportName);
    if (!viewport) {
      // Already fails loudly at module load via validateManifest(); defensive only.
      continue;
    }

    test(`${entry.id} @ ${viewportName}`, async ({ page }) => {
      const consoleErrors: { text: string; url: string }[] = [];
      const failedRequests: string[] = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') {
          consoleErrors.push({ text: msg.text(), url: msg.location()?.url ?? '' });
        }
      });
      page.on('pageerror', (error) => consoleErrors.push({ text: `uncaught page error: ${error.message}`, url: '' }));
      page.on('requestfailed', (request) => {
        failedRequests.push(`${request.method()} ${request.url()} — ${request.failure()?.errorText ?? 'unknown'}`);
      });

      await page.setViewportSize({ width: viewport.width, height: viewport.height });

      const url = targetUrl(entry);
      const response = await page.goto(url, { waitUntil: 'networkidle' });
      // Style tags don't survive a navigation — apply after `goto`, not before.
      await page.addStyleTag({ content: DISABLE_ANIMATIONS_CSS });
      await page.evaluate(() => document.fonts?.ready).catch(() => undefined);

      if (entry.interaction) {
        const interaction = INTERACTIONS[entry.interaction];
        if (!interaction) {
          throw new Error(`capture.spec: unknown interaction "${entry.interaction}" for entry "${entry.id}"`);
        }
        await interaction(page);
        await page.waitForLoadState('networkidle').catch(() => undefined);
        // Re-apply: the interaction may have triggered a client-side re-render that dropped the
        // injected style tag (React re-mounting the document head is not expected here, but a
        // second application is cheap insurance against exactly that class of flake).
        await page.addStyleTag({ content: DISABLE_ANIMATIONS_CSS });
      }

      const screenshotRelPath = ['screenshots', entry.surface, entry.id, `${viewportName}.png`].join('/');
      const screenshotAbsPath = join(OUTPUT_DIR, ...screenshotRelPath.split('/'));
      mkdirSync(join(OUTPUT_DIR, 'screenshots', entry.surface, entry.id), { recursive: true });

      const maskLocators = (entry.maskSelectors ?? []).map((selector) => page.locator(selector));
      await page.screenshot({
        path: screenshotAbsPath,
        fullPage: entry.fullPage,
        mask: maskLocators.length > 0 ? maskLocators : undefined,
        animations: 'disabled',
      });

      const facts = await collectDomFactsFromPage(page, entry);
      const domChecks = evaluateDeterministicChecks(facts);

      const status = response?.status();
      // A 4xx here is NOT necessarily broken: this site's own verified, accepted hosting contract
      // (agentforge4j-ui-e2e/README.md "Day 1.5 hosting contract") serves every known client-side
      // route with a real HTTP 404 whose BODY is the SPA shell, which then boots and renders the
      // correct page — GitHub Pages' own static-hosting mechanism for a client-routed SPA, proven
      // correct during the Assembler's own real-artifact verification, not a defect. Only a
      // missing response or a genuine server error (5xx) fails this check; whether the page
      // actually rendered despite a 404 is exactly what the DOM checks above already verify.
      const pageLoadCheck: CheckResult =
        status !== undefined && status < 500
          ? status >= 400
            ? { id: 'page-loaded-ok', status: 'pass', detail: `HTTP ${status} (accepted SPA-fallback — see Day 1.5 hosting contract)` }
            : { id: 'page-loaded-ok', status: 'pass' }
          : { id: 'page-loaded-ok', status: 'fail', detail: `HTTP ${status ?? 'no response'}` };

      // The browser logs "Failed to load resource ... 404" for the top-level document navigation
      // itself whenever the accepted SPA-fallback 404 above fires — an echo of the already-handled
      // status above, not a real console error. Filtered by exact URL match (the top-level nav's
      // own URL) so a genuinely broken sub-resource (a different URL) still fails this check.
      const filteredConsoleErrors =
        status === 404
          ? consoleErrors.filter((e) => e.url !== url)
          : consoleErrors;

      const signals: RuntimeSignal[] = [
        ...filteredConsoleErrors.map((e) => ({ kind: 'console-error' as const, detail: e.text })),
        ...failedRequests.map((detail) => ({ kind: 'request-failed' as const, detail })),
      ];
      const runtimeCheck = evaluateRuntimeSignals(signals);

      const allChecks = [pageLoadCheck, ...domChecks, runtimeCheck];
      const overallStatus: 'pass' | 'fail' = allChecks.some((check) => check.status === 'fail') ? 'fail' : 'pass';

      const record: CaptureRecord = {
        entryId: entry.id,
        surface: entry.surface,
        stateName: entry.stateName,
        releaseImportance: entry.releaseImportance,
        knownIssues: entry.knownIssues ?? [],
        viewport: viewportName,
        screenshotPath: screenshotRelPath,
        checks: allChecks,
        overallStatus,
        aiReviewEnabled: entry.aiReviewEnabled,
        notes: entry.notes,
      };
      writeFileSync(join(RESULTS_DIR, `${entry.id}--${viewportName}.json`), JSON.stringify(record, null, 2));
    });
  }
}
