// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';

test.describe('C1 — add-step renders', () => {
  test('adding a step adds a node to the canvas and the minimap', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto();
    await builder.enterAdvancedMode();

    const before = await builder.canvasNodes.count();
    expect(before).toBe(BUILDER_FIXTURE.initialNodeCount);
    await expect(builder.minimapNodes).toHaveCount(before);

    await builder.addStep(BUILDER_FIXTURE.addableKindSlug);

    await expect(builder.canvasNodes).toHaveCount(before + 1);
    await expect(builder.minimapNodes).toHaveCount(before + 1);
  });
});
