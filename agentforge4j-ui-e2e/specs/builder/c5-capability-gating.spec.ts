// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';

/**
 * The dev harness mounts the builder with `aiAssist: false`. The AI affordance is the only
 * authenticated surface, so its absence is the capability-gating contract: with the capability
 * off the control does not render, and there is therefore no control from which an authenticated
 * call could be attempted.
 */
test.describe('C5 — capability gating', () => {
  test('no AI affordance renders when aiAssist is disabled', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto();

    await expect(page.getByTestId(TID.aiAssist)).toHaveCount(0);
    await expect(page.getByRole('button', { name: /build with ai/i })).toHaveCount(0);
  });
});
