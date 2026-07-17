// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F8 — undo/redo and delete safety (#96)', () => {
  test('undo/redo toolbar round-trips a structural change', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await expect(builder.undoButton).toBeDisabled();
    await expect(builder.redoButton).toBeDisabled();

    await builder.addStep('ai-step'); // canvas starts with 1 untouched starter step
    await expect(builder.canvasNodes).toHaveCount(2);
    await expect(builder.undoButton).toBeEnabled();

    await builder.undo();
    await expect(builder.canvasNodes).toHaveCount(1);
    await expect(builder.redoButton).toBeEnabled();

    await builder.redo();
    await expect(builder.canvasNodes).toHaveCount(2);
  });

  test('Ctrl+Z / Ctrl+Shift+Z keyboard shortcuts undo and redo', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(2);

    // The undo/redo keydown listener only fires for keydowns originating inside the builder
    // root, and deliberately ignores Ctrl+Z while focus sits in a text field (so native
    // per-field text undo stays intact) — focus the root itself directly, the same target the
    // app's own post-delete focus management uses.
    await page.evaluate(() => (document.querySelector('[data-testid="workflow-builder"]') as HTMLElement)?.focus());

    await page.keyboard.press('Control+z');
    await expect(builder.canvasNodes).toHaveCount(1);

    await page.keyboard.press('Control+Shift+z');
    await expect(builder.canvasNodes).toHaveCount(2);
  });

  test('deleting a step requires confirmation; Cancel leaves it in place', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await builder.selectNodeById('n-0');
    await builder.inspector.getByRole('button', { name: /delete step/i }).click();
    await expect(builder.deleteConfirmDialog).toBeVisible();

    await builder.cancelDelete();
    await expect(builder.deleteConfirmDialog).toHaveCount(0);
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount);
  });

  test('deleting a step: Confirm removes it, and the deletion itself is undoable', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await builder.selectNodeById('n-0');
    await builder.inspector.getByRole('button', { name: /delete step/i }).click();
    await builder.confirmDelete();
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount - 1);

    await builder.undo();
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount);
  });
});
