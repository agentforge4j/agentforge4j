// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';

test.describe('keyboard navigation', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('skip-link is the first tab stop and jumps to main content', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');
    const skipLink = page.getByRole('link', { name: 'Skip to content' });
    await expect(skipLink).toBeFocused();
    await expect(skipLink).toHaveAttribute('href', '#main-content');
  });

  // Final-review finding: <main id="main-content"> had no tabIndex, so activating the skip link
  // scrolled the page but never actually moved keyboard focus there — the very thing a skip link
  // exists to do.
  test('activating the skip link moves keyboard focus to main content', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();

    await page.keyboard.press('Enter');
    await expect(page.locator('#main-content')).toBeFocused();
  });

  test('tabbing through the header reaches every primary nav link, in order, before the CTA', async ({
    page,
  }) => {
    await page.goto('/');
    const expectedOrder = ['Skip to content', 'AgentForge4j', 'Docs', 'Catalogue', 'Builder', 'Architecture', 'Community', 'GitHub', 'Use'];

    for (const name of expectedOrder) {
      await page.keyboard.press('Tab');
      const focused = page.locator(':focus');
      const accessibleName = await focused.evaluate((el) => el.getAttribute('aria-label') ?? el.textContent?.trim());
      expect(accessibleName, `expected focus on "${name}"`).toContain(name);
    }
  });

  test('every focused interactive element shows a visible focus outline', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    const outline = await page.evaluate(() => getComputedStyle(document.activeElement as Element).outlineStyle);
    expect(outline).not.toBe('none');
  });
});
