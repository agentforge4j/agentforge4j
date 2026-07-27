// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

/**
 * #94 regression coverage. Uses `?fixture=none` (initialWorkflow entirely omitted) — the one
 * seed state that engages the built-in localStorage draft-recovery adapter; any supplied
 * `initialWorkflow`, fixtures included, suppresses restore (`WorkflowBuilder.tsx`'s
 * `allowRestore: !readOnly && !initialWorkflow`). See fixtures/builder-functional-fixtures.ts.
 *
 * Like the `empty` fixture, a zero-step seed still renders the Builder's one untouched ASK_USER
 * starter step (`createInitialCanvasModel()`), not a literally blank canvas — every node count
 * below is relative to that starting point, and "Start fresh" resets back to it, not to zero.
 *
 * Each test gets a fresh, isolated Playwright browser context (default test isolation), so
 * localStorage never leaks between tests — no explicit clear needed between them.
 */
test.describe('F7 — persistence (#94)', () => {
  test('a reload restores the in-progress draft, with a visible notice', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.none.query}`);
    await expect(builder.canvasNodes).toHaveCount(1);
    await builder.setWorkflowName('Unsaved Draft');
    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(2);

    // Let the debounced save flush.
    await page.waitForTimeout(1000);
    await page.reload();

    await expect(builder.draftRestoredBanner).toBeVisible();
    await expect(builder.nameField).toHaveValue('Unsaved Draft');
    await expect(builder.canvasNodes).toHaveCount(2);
  });

  test('"Start fresh" clears the notice, the saved draft, and resets the canvas to a fresh starter', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.none.query}`);
    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(2);
    await page.waitForTimeout(1000);
    await page.reload();
    await expect(builder.draftRestoredBanner).toBeVisible();

    await builder.startFresh();
    await expect(builder.draftRestoredBanner).toHaveCount(0);
    await expect(builder.canvasNodes).toHaveCount(1); // back to the fresh, untouched starter

    // Confirmed permanent: a further reload finds nothing to restore.
    await page.reload();
    await expect(builder.draftRestoredBanner).toHaveCount(0);
    await expect(builder.canvasNodes).toHaveCount(1);
  });

  test('"Dismiss" hides the notice without discarding the restored draft', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.none.query}`);
    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(2);
    await page.waitForTimeout(1000);
    await page.reload();
    await expect(builder.draftRestoredBanner).toBeVisible();

    await builder.dismissDraftRestoredBanner();
    await expect(builder.draftRestoredBanner).toHaveCount(0);
    await expect(builder.canvasNodes).toHaveCount(2);
  });

  test('a fixture-seeded mount (initialWorkflow supplied) never shows a restore notice', async ({ page }) => {
    const builder = new BuilderPage(page);
    // Same origin/localStorage keyspace as the other tests in this file, but this test's own
    // context starts with empty storage (fresh context per test) — nothing to restore regardless.
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await expect(builder.draftRestoredBanner).toHaveCount(0);
  });
});
