// SPDX-License-Identifier: Apache-2.0
//
// Real-DOM regression coverage for `visual/checks.ts`'s `collectDomFactsFromPage` — specifically
// `main-content-not-blank`, whose visibility detection (isVisuallyPerceptible, display:none,
// visibility:hidden, the sr-only clip-rect technique) genuinely needs a browser's layout/style
// engine, unlike `evaluateDeterministicChecks`/`evaluatePageLoadCheck`/`evaluateRuntimeSignals`
// (checks.spec.ts), which are pure and operate on already-collected facts. Uses `page.setContent()`
// — no server, no navigation — so this runs under playwright.visual-unit.config.ts's existing
// no-webServer setup: Playwright only launches a browser for a test that actually requests the
// `page` fixture, which none of the other tests in this directory do.

import { expect, test } from '@playwright/test';
import { collectDomFactsFromPage } from '../../visual/checks';

test.describe('main-content-not-blank — genuinely visible content, not just DOM-present text', () => {
  test('a genuinely empty <main> is blank', async ({ page }) => {
    await page.setContent('<body><main></main></body>');
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(true);
  });

  test('normal visible text is not blank', async ({ page }) => {
    await page.setContent('<body><main><h1>Hello world</h1></main></body>');
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(false);
  });

  test('a normal visible image is not blank', async ({ page }) => {
    // A 1x1 transparent gif data URI decodes to a real, non-zero-rendered <img> once given explicit
    // dimensions — the check only cares about layout size, not decoded pixel content.
    await page.setContent(
      '<body><main><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBTAA7" width="100" height="100"></main></body>',
    );
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(false);
  });

  test('display:none text is blank — the real bug this check must not reintroduce', async ({ page }) => {
    await page.setContent('<body><main><h1 style="display:none">Hidden</h1></main></body>');
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(true);
  });

  test('visibility:hidden text is blank', async ({ page }) => {
    await page.setContent('<body><main><h1 style="visibility:hidden">Hidden</h1></main></body>');
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(true);
  });

  test('sr-only (screen-reader-only, DOM-present but visually clipped) text alone is blank — the exact bug found this round', async ({ page }) => {
    // Tailwind's real sr-only implementation: clips to ~1x1px while remaining un-display:none'd and
    // un-visibility:hidden'd, specifically so a screen reader still finds it. Before this fix,
    // main.textContent aggregated this text regardless of visibility, so a page whose ONLY content
    // was an sr-only label (e.g. this UI's own BuilderPage.tsx heading) reported non-blank.
    await page.setContent(`
      <body>
        <main>
          <h1 style="position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border-width:0">
            Screen-reader-only heading
          </h1>
        </main>
      </body>
    `);
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(true);
  });

  test('sr-only text ALONGSIDE genuinely visible text is not blank', async ({ page }) => {
    await page.setContent(`
      <body>
        <main>
          <h1 style="position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)">Screen-reader-only heading</h1>
          <p>Genuinely visible paragraph</p>
        </main>
      </body>
    `);
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(false);
  });

  test('an empty visible container (no text, no image) is blank', async ({ page }) => {
    await page.setContent('<body><main><div style="width:200px;height:200px;background:red"></div></main></body>');
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(true);
  });

  test('falls back to <body> when there is no <main> landmark', async ({ page }) => {
    await page.setContent('<body><h1>Hello world</h1></body>');
    const facts = await collectDomFactsFromPage(page, {});
    expect(facts.mainContentBlank).toBe(false);
  });
});
