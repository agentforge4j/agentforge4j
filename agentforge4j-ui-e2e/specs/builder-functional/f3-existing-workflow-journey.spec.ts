// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F3 — existing-workflow journey', () => {
  test('load a fixture, confirm nodes/connections, edit, add/remove, validate, re-export', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);

    // Confirm nodes and connections.
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount);
    await expect(builder.canvasEdges).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount - 1);

    // Edit an allowed field: the workflow name.
    await builder.setWorkflowName('Renamed Workflow');
    await expect(builder.nameField).toHaveValue('Renamed Workflow');

    // Remove an element (delete now requires confirmation, per issue #96) before adding one —
    // n-0 is dev/sample-workflow.ts's fixed 1st step, backendStepId 'ask-user-dev'.
    await builder.selectNodeById('n-0');
    await page.getByTestId('workflow-builder-inspector-panel').getByRole('button', { name: /delete step/i }).click();
    await expect(builder.deleteConfirmDialog).toBeVisible();
    await builder.confirmDelete();
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount - 1);

    // Add an element.
    await builder.addStep(BUILDER_FIXTURE.addableKindSlug);
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount);

    // Validate updated state.
    await expect(builder.validationPill).toBeVisible();

    // Confirm the resulting definition actually changed: the removed step id is gone, the added
    // step is present, net count unchanged (-1 removed, +1 added).
    const afterExport = await builder.exportDraft();
    expect(afterExport.steps.some((s) => s.stepId === 'ask-user-dev')).toBe(false);
    expect(afterExport.steps).toHaveLength(FUNCTIONAL_FIXTURES.multiStep.nodeCount);
    expect((afterExport as unknown as { name?: string }).name).toBe('Renamed Workflow');
  });
});
