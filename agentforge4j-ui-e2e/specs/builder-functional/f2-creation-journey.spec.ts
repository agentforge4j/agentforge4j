// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F2 — basic creation journey', () => {
  test('create, name, add + configure + connect steps, validate, export', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}&caps=export`);
    // Starting an empty workflow gives one untouched starter step (FUNCTIONAL_FIXTURES.empty).
    await expect(builder.canvasNodes).toHaveCount(1);

    // Name it.
    await builder.setWorkflowName('My New Workflow');
    await expect(builder.nameField).toHaveValue('My New Workflow');

    // Configure the starter step.
    await builder.canvasNodes.first().click();
    await expect(builder.inspector).toBeVisible();
    await builder.inspector.getByLabel('Name').fill('Collect the topic');
    await builder.closeInspector();

    // Add a representative second step.
    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(2);

    // Connect: the appended step lands on a linear continuation edge from the first.
    await expect(builder.canvasEdges).toHaveCount(1);

    // Configure it: the palette append auto-selects the new node. The Behavior section defaults
    // to collapsed in Guided mode (issue #101's discoverability fix means it's reachable, not
    // that it starts open) — expand it before asserting its fields.
    await expect(builder.inspector).toBeVisible();
    await builder.expandBehaviourSection();
    const behaviourFields = page.getByTestId('workflow-builder-inspector-behaviour-section').locator('.wf-field');
    await expect(behaviourFields.first()).toBeVisible();
    await builder.closeInspector();

    // Validate.
    await expect(builder.validationPill).toBeVisible();

    // Export / retrieve the resulting definition.
    const draft = await builder.exportDraft();
    expect(draft.steps).toHaveLength(2);
    await expect(builder.exportSuccess).toBeVisible();
  });
});
