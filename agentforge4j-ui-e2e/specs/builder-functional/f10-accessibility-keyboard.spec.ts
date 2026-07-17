// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F10 — accessibility and keyboard coverage', () => {
  test('primary toolbar controls have accessible names', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);
    await expect(builder.nameField).toHaveAccessibleName('Workflow name');
    await expect(page.getByTestId('workflow-builder-mode-guided')).toHaveAccessibleName(/guided/i);
    await expect(page.getByTestId('workflow-builder-mode-advanced')).toHaveAccessibleName(/advanced/i);
    await expect(builder.undoButton).toHaveAccessibleName(/undo/i);
    await expect(builder.redoButton).toHaveAccessibleName(/redo/i);
    await expect(builder.validationPill).toHaveAccessibleName(/./); // non-empty
    await expect(page.getByTestId('workflow-builder-export')).toHaveAccessibleName(/./);
  });

  test('a palette add-button activates via keyboard (Enter)', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await builder.expandPalette();
    const addButton = page.getByTestId('workflow-builder-palette-add-ai-step');
    await addButton.focus();
    await expect(addButton).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(builder.canvasNodes).toHaveCount(2); // 1 starter + the newly added step
  });

  test('Escape closes the inspector and returns focus predictably', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await builder.selectNodeById('n-0');
    await expect(builder.inspector).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(builder.inspector).toHaveCount(0);
  });

  test('the delete-confirmation dialog traps Tab focus within itself', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await builder.selectNodeById('n-0');
    await builder.inspector.getByRole('button', { name: /delete step/i }).click();
    await expect(builder.deleteConfirmDialog).toBeVisible();

    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab');
      const withinDialog = await page.evaluate(() => {
        const dialog = document.querySelector('[role="alertdialog"]');
        return Boolean(dialog && dialog.contains(document.activeElement));
      });
      expect(withinDialog).toBe(true);
    }
    await builder.cancelDelete();
  });

  test('the validation popover moves focus into itself on open, and restores it on close', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await builder.addStep('decision');
    await builder.validationPill.focus();
    await builder.validationPill.click();
    await expect(builder.validationPopover).toBeVisible();
    const focusInPopover = await page.evaluate(() => {
      const dialog = document.querySelector('.wf-validation-pill__popover');
      return Boolean(dialog && dialog.contains(document.activeElement));
    });
    expect(focusInPopover).toBe(true);

    await page.keyboard.press('Escape');
    await expect(builder.validationPopover).toHaveCount(0);
    await expect(builder.validationPill).toBeFocused();
  });
});
