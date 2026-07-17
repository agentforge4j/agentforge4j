// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';

/**
 * Part A — scope-aware reachability surfaces an unreachable step as a WARNING
 * (⚠), never an error (!), and a connected graph surfaces no warning. Deleting a
 * mid-chain step disconnects its downstream decision, which must then warn.
 */
test.describe('C11 — unreachable warning severity', () => {
  test('a disconnected step warns (⚠) while a connected graph does not', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto();
    await builder.enterAdvancedMode();

    // Connected baseline: the validation popover shows no warning glyph.
    await page.locator('.wf-validation-pill').click();
    const baselinePopover = page.getByRole('dialog', { name: /problems/i });
    await expect(baselinePopover).toBeVisible();
    await expect(baselinePopover.getByText('⚠')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(baselinePopover).toHaveCount(0);

    // Delete the mid-chain AI step (n-1) → the downstream decision becomes unreachable.
    await builder.selectNodeById('n-1');
    await builder.inspector.getByRole('button', { name: /delete step/i }).click();
    // Delete now requires confirmation (issue #96, PR #107).
    await expect(builder.deleteConfirmDialog).toBeVisible();
    await builder.confirmDelete();
    await expect(builder.canvasNodes).toHaveCount(BUILDER_FIXTURE.initialNodeCount - 1);

    await page.locator('.wf-validation-pill').click();
    const warnedPopover = page.getByRole('dialog', { name: /problems/i });
    await expect(warnedPopover.getByText('⚠').first()).toBeVisible();
  });
});
