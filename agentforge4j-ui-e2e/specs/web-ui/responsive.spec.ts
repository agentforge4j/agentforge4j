// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { SITE_ROUTES, VIEWPORTS } from '../../support/web-ui/routes';

test.describe('no horizontal overflow', () => {
  for (const viewport of VIEWPORTS) {
    test.describe(`${viewport.name} (${viewport.width}x${viewport.height})`, () => {
      test.use({ viewport: { width: viewport.width, height: viewport.height } });

      for (const route of SITE_ROUTES) {
        test(`${route.path} fits the viewport width`, async ({ page }) => {
          await page.goto(route.path);
          await expect(page.getByRole('heading', { level: 1, name: route.heading })).toBeVisible();
          const overflow = await page.evaluate(
            () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
          );
          expect(overflow, `${route.path} overflows horizontally at ${viewport.name}`).toBeLessThanOrEqual(1);
        });
      }
    });
  }
});

// Final-review finding: the header's desktop/mobile-nav breakpoint was moved from Tailwind's
// `md:` (768px) to `lg:` (1024px) during Day 1, specifically because the full desktop nav
// overflowed at 768px tablet-portrait. Pin the exact boundary so a future breakpoint change
// can't silently reintroduce that overflow.
test.describe('header breakpoint (lg: 1024px)', () => {
  test('at 1024px (tablet landscape) the desktop nav is exposed and the hamburger is not', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto('/');
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(1);
    await expect(page.getByRole('button', { name: 'Open menu' })).toHaveCount(0);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('at 1023px, just below the boundary, the hamburger is exposed and the desktop nav is not', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1023, height: 768 });
    await page.goto('/');
    await expect(page.getByRole('navigation', { name: 'Primary' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Open menu' })).toHaveCount(1);
  });
});

// Day 1 review finding: the catalogue-detail workflow diagram carried explicit pixel
// width/height with no responsive scaling — a ~1690px-tall, partly off-screen box on every
// device. Checked separately (needs a real workflow id, not in SITE_ROUTES).
test.describe('catalogue detail diagram fits its container', () => {
  for (const viewport of VIEWPORTS) {
    test(`at ${viewport.name} (${viewport.width}x${viewport.height})`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto('/catalogue/workflow-execution-estimator');

      const graph = page.getByRole('img', { name: /Step graph for the/ });
      await expect(graph).toBeVisible();
      const svg = graph.locator('svg');
      const box = await svg.boundingBox();
      expect(box).not.toBeNull();
      // The rendered box must not be wider than the viewport (the wrapper's own overflow-x-auto
      // is a safety net for pathologically wide graphs, not the expected common case).
      expect(box!.width).toBeLessThanOrEqual(viewport.width);
    });
  }
});
