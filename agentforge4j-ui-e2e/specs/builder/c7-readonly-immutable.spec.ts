// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';

/**
 * Part B — read-only mode is the core trust guarantee: with `mode="readOnly"` no
 * interaction may mutate the workflow. Behavioural proof = the serialized
 * `WorkflowDefinition` (read via the harness export seam) is byte-identical after
 * a battery of mutation gestures, and every mutating affordance is absent.
 */
test.describe('C7 — read-only mode is immutable', () => {
  test('mutating affordances are absent; the badge and export remain', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?mode=readOnly&caps=export');

    await expect(page.getByTestId(TID.readonlyBadge)).toBeVisible();
    await expect(page.locator('.wf-palette')).toHaveCount(0);
    await expect(page.getByTestId(TID.modeGuided)).toHaveCount(0);
    await expect(page.getByTestId(TID.modeAdvanced)).toHaveCount(0);
    await expect(page.getByTestId(TID.saveButton)).toHaveCount(0);
    await expect(page.getByTestId(TID.publishButton)).toHaveCount(0);
    await expect(page.getByTestId(TID.importButton)).toHaveCount(0);
    // Export is read-only safe (serialization, not mutation) and stays available.
    await expect(page.getByTestId(TID.exportButton)).toBeVisible();
  });

  test('inspector is view-only: disabled fields, no runs-after, no delete', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?mode=readOnly');

    await builder.selectNodeById('n-1'); // AI step
    await expect(page.getByTestId(TID.inspectorReadonlyBanner)).toBeVisible();
    // The fieldset is disabled, so its first field is non-editable.
    await expect(builder.inspector.getByRole('textbox').first()).toBeDisabled();
    await expect(page.getByTestId(TID.runsAfterSelect)).toHaveCount(0);
    await expect(builder.inspector.getByRole('button', { name: /delete step/i })).toHaveCount(0);
  });

  test('drag and delete gestures leave the serialized workflow byte-identical', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?mode=readOnly&caps=export');

    const before = await builder.exportDraft();
    const nodeCountBefore = await builder.canvasNodes.count();
    const edgeCountBefore = await builder.canvasEdges.count();
    const transformBefore = await builder.nodeTransform('n-1');

    await builder.dragNodeBy('n-1', 160, 80);
    await builder.selectNodeById('n-1');
    await page.keyboard.press('Backspace');
    await page.keyboard.press('Delete');

    await expect(builder.canvasNodes).toHaveCount(nodeCountBefore);
    await expect(builder.canvasEdges).toHaveCount(edgeCountBefore);
    expect(await builder.nodeTransform('n-1')).toBe(transformBefore);

    const after = await builder.exportDraft();
    expect(after).toEqual(before);
  });
});
