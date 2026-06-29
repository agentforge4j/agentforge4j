// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';

/**
 * Guards the default editable path and read-only precedence. The deletion that
 * C7 proves is blocked in read-only must succeed in the default mount — otherwise
 * C7 could pass against a builder that is simply broken. And an explicitly enabled
 * mutating capability must NOT re-open mutation while read-only.
 */
test.describe('C8 — editable default path + read-only precedence', () => {
  test('a step deletion succeeds in the default editable mount', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?caps=export');
    await builder.enterAdvancedMode();

    await expect(page.getByTestId(TID.modeAdvanced)).toBeVisible();
    await expect(page.locator('.wf-palette')).toBeVisible();

    const before = await builder.exportStepIds();
    expect(before).toContain('ai-step-dev');
    const nodeCountBefore = await builder.canvasNodes.count();

    await builder.selectNodeById('n-1'); // the AI step = ai-step-dev
    await builder.inspector.getByRole('button', { name: /delete step/i }).click();

    await expect(builder.canvasNodes).toHaveCount(nodeCountBefore - 1);
    expect(await builder.exportStepIds()).not.toContain('ai-step-dev');
  });

  test('read-only overrides an enabled mutating capability (import stays gone)', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?mode=readOnly&caps=import,export');

    await expect(page.getByTestId(TID.readonlyBadge)).toBeVisible();
    // `import` capability is set, but read-only is a hard override.
    await expect(page.getByTestId(TID.importButton)).toHaveCount(0);
    await expect(page.getByTestId(TID.exportButton)).toBeVisible();

    const before = await builder.exportDraft();
    const nodeCountBefore = await builder.canvasNodes.count();
    await builder.selectNodeById('n-1');
    await page.keyboard.press('Backspace');

    await expect(builder.canvasNodes).toHaveCount(nodeCountBefore);
    expect(await builder.exportDraft()).toEqual(before);
  });
});
