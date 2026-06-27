// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';

test.describe('C6 — responsive', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('mobile viewport collapses the palette to a trigger with no horizontal overflow', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto();

    await expect(page.getByTestId(TID.mobileTrigger)).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });
});
