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
  evaluatePageLoadCheck,
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
  /** Informational only — see `visual/manifest.ts`'s doc comment on this field. Does NOT exempt
   *  anything; `acceptedFindings` below is the only real exemption path. */
  readonly knownIssues: readonly number[];
  /** Per-check non-blocking classifications from the manifest (see `visual/manifest.ts`'s
   *  `AcceptedFinding` doc comment) — `overallStatus` below stays the raw, unfiltered mechanical
   *  result regardless of these; `generate-report.mjs` is what actually decides blocking vs
   *  non-blocking for the release-check verdict. */
  readonly acceptedFindings: readonly {
    checkId: string;
    reason: string;
    requiresHumanConfirmation?: boolean;
    issue?: number;
    viewports?: readonly string[];
  }[];
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

/**
 * Every `entryId--viewport` this run is ABOUT to attempt, written before any test executes.
 * `generate-report.mjs` cross-checks the real result files in `visual-output/results/` against
 * this list — a manifest entry whose test crashed/errored (so it never reached the
 * `writeFileSync` at the end of the test body — see `addStep`'s fail-loud design in
 * `visual/interactions.ts`) produces an entry here with no matching result file, which the report
 * surfaces as a missing, release-blocking capture instead of silently reporting fewer states than
 * the manifest actually defines. Written unconditionally at module load (every worker process
 * evaluates this file once before running any test in it), so multiple workers race to produce the
 * same, deterministic, idempotent content — a temp-file-then-rename approach was tried here first,
 * but still throws on Windows: renaming several temp files from different OS processes into the
 * same destination concurrently can fail with EPERM (reproduced directly, not theoretical — Windows
 * does not give concurrent same-destination renames the crash-safe atomicity POSIX rename
 * provides). `{ flag: 'wx' }` (`O_CREAT|O_EXCL`) is instead a single atomic OS-level "create only if
 * absent" syscall: exactly one worker's write wins and the rest predictably fail with `EEXIST`,
 * which is always safe to ignore here since every worker would have written byte-identical content
 * anyway — no locking, no retry, no temp file, and confirmed race-free under real concurrent OS
 * processes (see specs/visual-unit/expected-inventory-write.spec.ts).
 */
const expectedInventory = VISUAL_MANIFEST.flatMap((entry) => entry.viewports.map((viewportName) => `${entry.id}--${viewportName}`));
const expectedInventoryPath = join(OUTPUT_DIR, 'expected-inventory.json');
try {
  writeFileSync(expectedInventoryPath, JSON.stringify(expectedInventory, null, 2), { flag: 'wx' });
} catch (error) {
  if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
    throw error;
  }
}

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
      const domChecks = evaluateDeterministicChecks(facts, entry.minNodeCount);

      const status = response?.status();
      // See `evaluatePageLoadCheck`'s own doc comment in visual/checks.ts for the full known-route
      // status contract: HTTP 404 is the one specific, currently-documented SPA-fallback status for
      // a known route; a DIFFERENT 4xx is a real failure, not silently accepted. Whether the page
      // actually rendered despite a 404 is exactly what the DOM checks above already verify.
      const pageLoadCheck: CheckResult = evaluatePageLoadCheck(status);

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
        acceptedFindings: entry.acceptedFindings ?? [],
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
