// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F1 — loading and initial state', () => {
  test('loads without fatal errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    expect(errors).toEqual([]);
  });

  test('empty workflow state renders as a single untouched starter step', async ({ page }) => {
    // The Builder's own concept of "empty" (a zero-step initialWorkflow) is one unconfigured
    // ASK_USER starter node, not a literally blank canvas — see FUNCTIONAL_FIXTURES.empty.
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.empty.nodeCount);
    await expect(builder.canvasNodes.first()).toHaveClass(/react-flow__node-step/);
  });

  test('a deterministic existing workflow loads with the expected node count', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await expect(builder.canvasNodes).toHaveCount(FUNCTIONAL_FIXTURES.multiStep.nodeCount);
    await expect(builder.canvasNodes.filter({ hasText: BUILDER_FIXTURE.nodeNames.askUser })).toHaveCount(1);
  });

  test('initial selection and inspector state: nothing selected, inspector closed', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    await expect(builder.inspector).toHaveCount(0);
  });

  test('required primary controls are visible on mount', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}&caps=export`);
    await expect(builder.nameField).toBeVisible();
    await expect(page.getByTestId('workflow-builder-mode-guided')).toBeVisible();
    await expect(page.getByTestId('workflow-builder-mode-advanced')).toBeVisible();
    await expect(builder.validationPill).toBeVisible();
    await expect(page.getByTestId('workflow-builder-canvas')).toBeVisible();
    await expect(page.getByTestId('workflow-builder-export')).toBeVisible();
  });
});
