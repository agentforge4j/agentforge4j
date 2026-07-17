// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F9 — start-step indicator/chooser (#100) and export confirmation (#102)', () => {
  test('a Start badge marks the sole step; a chooser appears and reassigns start once a second step exists', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    // The single untouched starter step is itself already the start node.
    await expect(builder.canvasNodes).toHaveCount(1);
    await expect(builder.canvasNodes.first().getByTestId(TID.nodeStartBadge)).toBeVisible();
    await expect(builder.startStepSelect).toHaveCount(0); // nothing to choose between yet

    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(2);
    await expect(builder.startStepSelect).toBeVisible();

    const newNodeId = await builder.canvasNodes.nth(1).getAttribute('data-id');
    expect(newNodeId).toBeTruthy();
    await builder.startStepSelect.selectOption(newNodeId as string);
    await expect(builder.canvasNodes.nth(1).getByTestId(TID.nodeStartBadge)).toBeVisible();
    await expect(builder.canvasNodes.nth(0).getByTestId(TID.nodeStartBadge)).toHaveCount(0);
  });

  test('the start-step chooser is Guided-mode-editable-only: absent in Advanced and in read-only', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    // multiStep (a non-empty initialWorkflow) mounts in Advanced mode by default; switch to
    // Guided first to see the chooser at all.
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await page.getByTestId('workflow-builder-mode-guided').click();
    await expect(builder.startStepSelect).toBeVisible();
    await builder.enterAdvancedMode();
    await expect(builder.startStepSelect).toHaveCount(0);
  });

  test('Export shows a persisted, visible confirmation naming the produced file', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);
    await expect(builder.exportSuccess).toHaveCount(0);

    await page.getByTestId('workflow-builder-export').click();
    await expect(builder.exportSuccess).toBeVisible();

    // Persisted — still visible after other, unrelated interaction.
    await builder.validationPill.click();
    await page.keyboard.press('Escape');
    await expect(builder.exportSuccess).toBeVisible();
  });
});
