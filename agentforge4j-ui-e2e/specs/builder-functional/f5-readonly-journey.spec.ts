// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F5 — read-only journey', () => {
  test('inspection, pan/zoom, selection, validation, and export all work read-only', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&mode=readOnly&caps=export`);
    await expect(page.getByTestId('workflow-builder-readonly-badge')).toBeVisible();

    // Selection + inspection.
    await builder.selectNodeById('n-0');
    await expect(builder.inspector).toBeVisible();
    await expect(page.getByTestId('inspector-readonly-banner')).toBeVisible();
    await builder.closeInspector();

    // Zoom controls remain interactive.
    const zoomIn = page.locator('.react-flow__controls-zoomin');
    await expect(zoomIn).toBeVisible();
    const viewport = page.locator('.react-flow__viewport');
    const before = await viewport.getAttribute('style');
    await zoomIn.click();
    await expect.poll(async () => viewport.getAttribute('style')).not.toBe(before);

    // Validation.
    await expect(builder.validationPill).toBeVisible();
    await builder.openValidationPopover();
    await page.keyboard.press('Escape');
    await expect(builder.validationPopover).toHaveCount(0);

    // Export.
    await expect(page.getByTestId('workflow-builder-export')).toBeVisible();
    const draft = await builder.exportDraft();
    expect(draft.steps.length).toBe(FUNCTIONAL_FIXTURES.multiStep.nodeCount);
  });

  test('editing affordances are absent: no mode toggle, no undo/redo, no start-step chooser', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&mode=readOnly`);
    await expect(page.getByTestId('workflow-builder-mode-guided')).toHaveCount(0);
    await expect(page.getByTestId('workflow-builder-mode-advanced')).toHaveCount(0);
    await expect(builder.undoButton).toHaveCount(0);
    await expect(builder.redoButton).toHaveCount(0);
    await expect(builder.startStepSelect).toHaveCount(0);
    await expect(page.locator('.wf-palette')).toHaveCount(0);
  });
});
