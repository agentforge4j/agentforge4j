// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';

test.describe('desktop navigation', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  const PRIMARY_NAV_LINKS: ReadonlyArray<readonly [label: string, path: string]> = [
    ['Docs', '/docs'],
    ['Catalogue', '/catalogue'],
    ['Builder', '/builder'],
    ['Architecture', '/architecture'],
    ['Community', '/community'],
  ];

  for (const [label, path] of PRIMARY_NAV_LINKS) {
    test(`the primary nav "${label}" link navigates to ${path}`, async ({ page }) => {
      await page.goto('/');
      await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: label }).click();
      await expect(page).toHaveURL(new RegExp(`${path}$`));
    });
  }

  test('the logo returns home from a non-home route', async ({ page }) => {
    await page.goto('/architecture');
    await page.getByRole('link', { name: 'AgentForge4j', exact: true }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('heading', { level: 1, name: 'AgentForge4j' })).toBeVisible();
  });

  test('the header "Use" call-to-action navigates to /use', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Use', exact: true }).click();
    await expect(page).toHaveURL(/\/use$/);
  });

  test('browser back/forward walk the real route history', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Catalogue' }).click();
    await expect(page).toHaveURL(/\/catalogue$/);
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Builder' }).click();
    await expect(page).toHaveURL(/\/builder$/);

    await page.goBack();
    await expect(page).toHaveURL(/\/catalogue$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow catalogue' })).toBeVisible();

    await page.goForward();
    await expect(page).toHaveURL(/\/builder$/);
  });
});

test.describe('footer navigation', () => {
  test('every footer link has a real, non-empty href', async ({ page }) => {
    await page.goto('/');
    const links = page.locator('footer').getByRole('link');
    const count = await links.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i += 1) {
      expect(await links.nth(i).getAttribute('href')).toBeTruthy();
    }
  });

  // Regression guard: /releases previously existed only as a route with no nav/footer entry
  // anywhere, reachable only by typing the URL directly.
  test('Releases is reachable from the footer', async ({ page }) => {
    await page.goto('/');
    await page.locator('footer').getByRole('link', { name: 'Releases' }).click();
    await expect(page).toHaveURL(/\/releases$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Releases' })).toBeVisible();
  });
});
