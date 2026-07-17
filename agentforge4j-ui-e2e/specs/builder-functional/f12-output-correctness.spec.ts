// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

/** Draft steps captured via the dev harness's `exportBundle` seam are `EditableStep`s
 *  (`stepId`, `name`, `behaviourType`, `config`, ...) — not the nested `{behaviour: {type}}`
 *  shape `exportStepJson`/`serializeStepExecutable` produce internally for the zip bundle. */
type ExportedStep = { stepId: string; name: string; behaviourType: string };

test.describe('F12 — output correctness (exported workflow definition)', () => {
  test('the export matches the WorkflowDefinition contract: real fields, not just a click confirmation', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.simpleValid.query}&caps=export`);
    const draft = await builder.exportDraft();
    expect(draft.steps).toHaveLength(1);
    const step = draft.steps[0] as unknown as ExportedStep;
    expect(step.stepId).toBe('collect-topic');
    expect(step.behaviourType).toBe('INPUT');
  });

  test('edited values are present in the export', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);
    await builder.setWorkflowName('Edited Name');
    await builder.selectNodeById('n-1');
    await builder.inspector.getByLabel('Name').fill('Renamed Step');
    await builder.closeInspector();

    const draft = await builder.exportDraft();
    expect((draft as unknown as { name: string }).name).toBe('Edited Name');
    const renamed = draft.steps.find((s) => (s as unknown as ExportedStep).name === 'Renamed Step');
    expect(renamed).toBeDefined();
  });

  test('deleted elements are absent from the export', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);
    const before = await builder.exportDraft();
    // n-2 is dev/sample-workflow.ts's fixed 3rd step, backendStepId 'decision-dev' — not
    // `before.steps[2]`: the BRANCH step's default-branch target reorders the export's step
    // array (topoMainNodeOrder interleaves it ahead of some earlier canvas nodes), so array
    // index does not track canvas node index for this fixture. Read the known fixed id instead.
    const targetId = 'decision-dev';
    expect(before.steps.some((s) => s.stepId === targetId)).toBe(true);

    await builder.selectNodeById('n-2');
    await builder.inspector.getByRole('button', { name: /delete step/i }).click();
    await expect(builder.deleteConfirmDialog).toBeVisible();
    await builder.confirmDelete();
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount - 1);

    const after = await builder.exportDraft();
    expect(after.steps.some((s) => s.stepId === targetId)).toBe(false);
    expect(after.steps).toHaveLength(before.steps.length - 1);
  });

  test('connections match the canvas: steps serialize in the order they were added', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}&caps=export`);
    // The empty fixture starts with one untouched ASK_USER starter step already on the canvas.
    await builder.addStep('ai-step');
    await builder.addStep('save-result'); // folds into the previous step's outputKeys, not its own step

    const draft = await builder.exportDraft();
    const behaviourTypes = draft.steps.map((s) => (s as unknown as ExportedStep).behaviourType);
    expect(behaviourTypes).toEqual(['INPUT', 'AGENT']);
  });

  test('the export is deterministic: re-exporting with no intervening change yields identical output', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);
    const first = await builder.exportDraft();
    const second = await builder.exportDraft();
    expect(second).toEqual(first);
  });
});
