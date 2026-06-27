// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';

test.describe('C4 — add/remove field and decision case', () => {
  test('adds then removes a form field on an input step', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto();
    await builder.enterAdvancedMode();
    await builder.selectNode(BUILDER_FIXTURE.nodeNames.askUser);
    await expect(builder.inspector).toBeVisible();

    const removeFields = page.getByTestId(TID.removeField);
    const initial = await removeFields.count();

    await page.getByTestId(TID.addField).click();
    await expect(removeFields).toHaveCount(initial + 1);

    await removeFields.last().click();
    await expect(removeFields).toHaveCount(initial);
  });

  test('adds then removes a decision case', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto();
    await builder.enterAdvancedMode();
    await builder.selectNode(BUILDER_FIXTURE.nodeNames.decision);
    await expect(builder.inspector).toBeVisible();

    const removeCases = page.getByTestId(TID.removeCase);
    const initial = await removeCases.count();

    await page.getByTestId(TID.addCase).click();
    await expect(removeCases).toHaveCount(initial + 1);

    await removeCases.last().click();
    await expect(removeCases).toHaveCount(initial);
  });
});
