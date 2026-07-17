// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';

test.describe('mobile navigation', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  // The desktop nav landmark is `hidden md:flex` — a real `display:none` below `md` removes it
  // from the accessibility tree entirely (unlike the jsdom unit tests, which don't apply CSS), so
  // at mobile width there are 0 "Primary" landmarks until the mobile panel mounts, then exactly 1.

  test('the hamburger opens and closes the mobile menu', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);

    await page.getByRole('button', { name: 'Open menu' }).click();
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(1);

    await page.getByRole('button', { name: 'Close menu' }).click();
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);
  });

  test('clicking a link inside the open menu navigates and closes the menu', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#primary-nav-mobile').getByRole('link', { name: 'Catalogue' }).click();

    await expect(page).toHaveURL(/\/catalogue$/);
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);
  });

  // Regression guard: the mobile GitHub link opens a new tab but previously left the menu
  // open behind it, unlike every other link in the panel.
  test('clicking the GitHub link inside the open menu also closes the menu', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Open menu' }).click();
    const [popup] = await Promise.all([
      page.waitForEvent('popup'),
      page.locator('#primary-nav-mobile').getByRole('link', { name: /GitHub/ }).click(),
    ]);
    await popup.close();
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);
  });

  // Regression guard: Escape previously did nothing while the mobile menu was open.
  test('pressing Escape closes the open menu', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Open menu' }).click();
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(1);

    await page.keyboard.press('Escape');
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);
  });

  // Final-review finding: closing the menu via Escape left focus stranded wherever it happened
  // to be inside the closing panel (e.g. a nav link), instead of returning it to the toggle
  // button — a keyboard user would lose their place entirely.
  test('pressing Escape returns focus to the menu toggle button', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#primary-nav-mobile').getByRole('link', { name: 'Catalogue' }).focus();

    await page.keyboard.press('Escape');
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Open menu' })).toBeFocused();
  });
});
