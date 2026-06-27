// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';

test.describe('C3 — inspector lifecycle + non-empty Behaviour', () => {
  test('selecting a node opens the inspector with a populated Behaviour section, then closes', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto();
    await builder.enterAdvancedMode();

    await builder.selectNode(BUILDER_FIXTURE.nodeNames.aiStep);
    await expect(builder.inspector).toBeVisible();

    const behaviour = page.getByTestId(TID.behaviourSection);
    await expect(behaviour).toBeVisible();
    // Non-empty for an AI step: the Behaviour body renders at least one field (agent/instructions).
    await expect(behaviour.locator('.wf-field').first()).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(builder.inspector).toHaveCount(0);
  });
});
