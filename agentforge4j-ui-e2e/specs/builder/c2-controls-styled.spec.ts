// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';

/**
 * I6: with no host `--color-*` theme, the primary control's accent resolves through the token
 * fallback chain `--builder-color-accent -> --afb-accent -> --afb-blue-500 (#2f6bff)`. The
 * browser reports `#2f6bff` as `rgb(47, 107, 255)`. The active mode-toggle button is the always
 * present primary control under the harness capabilities (all false).
 */
const PRIMARY_ACCENT_RGB = 'rgb(47, 107, 255)';

test.describe('C2 — controls styled with no host theme', () => {
  test('primary control resolves to the literal token accent value', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto();

    // Avoid any :hover state on the control before reading its computed style.
    await page.mouse.move(1, 1);

    const primary = page.locator('.wf-button--primary').first();
    await expect(primary).toBeVisible();

    const background = await primary.evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(background).toBe(PRIMARY_ACCENT_RGB);
  });
});
