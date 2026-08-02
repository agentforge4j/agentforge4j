// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';

test.describe('F4 — invalid-workflow journey and actionable validation (#95, #101)', () => {
  test('an invalid workflow: validation reports real findings, no silent success, no crash', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);

    // Deliberately invalid: add a Decision step and leave it unconfigured.
    await builder.addStep('decision');
    await expect(builder.canvasNodes).toHaveCount(2); // starter + the new Decision step

    // Meaningful validation results appear — not "Looks good".
    await expect(builder.validationPill).not.toHaveAccessibleName(/looks good/i);
    await builder.openValidationPopover();
    await expect(builder.validationPopover.getByRole('button', { name: /fix/i }).first()).toBeVisible();

    expect(errors).toEqual([]);
  });

  test('a valid workflow passes: the pill reads "Looks good"', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.simpleValid.query}`);
    await expect(builder.validationPill).toHaveAccessibleName(/looks good/i);
  });

  test('#95 regression: the validation popover renders above an already-open inspector, not behind it', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await builder.addStep('decision'); // appendNode auto-selects the new node

    // The inspector is already open on the invalid step (the common real-world case per #95).
    await expect(builder.inspector).toBeVisible();

    // Open the popover on top of it, and prove it is actually clickable (not merely
    // CSS-visible) by clicking its "Fix" button through to a real effect. Some issues are
    // workflow-level (no owning step) and render a disabled Fix button — target an enabled one.
    await builder.openValidationPopover();
    const fixButton = builder.validationPopover.locator('button.wf-validation-panel__fix-button:not([disabled])').first();
    await expect(fixButton).toBeVisible();
    await fixButton.click();
    await expect(builder.validationPopover).toHaveCount(0);
    await expect(builder.inspector).toBeVisible();
  });

  test('#101 regression: the "Add approval" checklist item is satisfiable, and reflects real validation', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await builder.addStep('ai-step'); // appendNode auto-selects the new node
    await expect(builder.inspector).toBeVisible();

    // The Behaviour section (collapsed by default in Guided mode) must genuinely expose the
    // approval control once expanded — not bury it unreachably (the H2/M1 discoverability bug).
    // The section's first combobox is actually the Agent picker (AI_STEP renders it before the
    // Approval field) — target the Approval field by its own accessible label, not position.
    const behaviourSection = page.getByTestId('workflow-builder-inspector-behaviour-section');
    await expect(behaviourSection).toBeVisible();
    await builder.expandBehaviourSection();
    const transitionSelect = behaviourSection.getByLabel('Approval');
    await expect(transitionSelect).toBeVisible();
    await transitionSelect.selectOption('HUMAN_APPROVAL');

    // Reflects in the checklist item's own completion state (item 3, "Add approval").
    await builder.closeInspector();
    const checklistItem = page.locator('.wf-guided-stepper__item', { hasText: 'Add approval' });
    await expect(checklistItem.locator('.wf-guided-stepper__marker--done')).toBeVisible();
  });
});
