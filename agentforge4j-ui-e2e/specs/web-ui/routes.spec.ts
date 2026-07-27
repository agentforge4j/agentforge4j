// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { SITE_ROUTES } from '../../support/web-ui/routes';

test.describe('every public route', () => {
  for (const route of SITE_ROUTES) {
    test(`${route.path} renders its heading with no console/page errors`, async ({ page }) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(String(error)));
      page.on('console', (msg) => {
        if (msg.type() === 'error') {
          errors.push(msg.text());
        }
      });

      await page.goto(route.path);
      await expect(page.getByRole('heading', { level: 1, name: route.heading })).toBeVisible();
      expect(errors).toEqual([]);
    });
  }

  test('an unmatched path renders the branded 404 page, not a blank screen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(String(error)));

    await page.goto('/this-route-does-not-exist');
    await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();
    expect(errors).toEqual([]);
  });
});
