// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';
import { PALETTE_STEP_KINDS } from '../../fixtures/builder-functional-fixtures';

/** ASK_USER and SAVE_RESULT have no Behavior `<details>` section — their fields live directly in
 *  the Basics section (matches `KINDS_WITH_BEHAVIOR` in StepConfigPanel.tsx). */
const KINDS_WITHOUT_BEHAVIOUR_SECTION = new Set(['ask-user', 'save-result']);

test.describe('F6 — step-type coverage (every currently supported palette step type)', () => {
  for (const kind of PALETTE_STEP_KINDS) {
    test(`${kind.label} (${kind.slug}): discoverable, addable, selectable, configurable`, async ({ page }) => {
      const builder = new BuilderPage(page);
      await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);

      // Discoverable: the palette add-button is reachable once expanded.
      await builder.expandPalette();
      const addButton = page.getByTestId(`workflow-builder-palette-add-${kind.slug}`);
      await expect(addButton).toBeVisible();
      await expect(addButton).toHaveAccessibleName(kind.label);

      // Addable. (Starting from the empty fixture's one untouched starter step — see
      // FUNCTIONAL_FIXTURES.empty — so the canvas has 2 nodes once this one is added.)
      await addButton.click();
      await expect(builder.canvasNodes).toHaveCount(2);

      // Selectable, and its editor/inspector loads. appendNode auto-selects the new node, but
      // close and re-click explicitly too, so this also proves manual selection works.
      await expect(builder.inspector).toBeVisible();
      await builder.closeInspector();
      await builder.canvasNodes.last().click();
      await expect(builder.inspector).toBeVisible();
      if (!KINDS_WITHOUT_BEHAVIOUR_SECTION.has(kind.slug)) {
        await expect(page.getByTestId('workflow-builder-inspector-behaviour-section')).toBeVisible();
      }

      // Minimal valid configuration: every kind has a Name field. Exact match — SAVE_RESULT's
      // own "Result name" field would otherwise also match a substring "Name" lookup.
      const nameInput = builder.inspector.getByLabel('Name', { exact: true });
      await nameInput.fill(`${kind.label} step`);
      await expect(nameInput).toHaveValue(`${kind.label} step`);
    });
  }
});
