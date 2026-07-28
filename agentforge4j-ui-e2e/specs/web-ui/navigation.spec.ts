// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';

test.describe('desktop navigation', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  // Docs is deliberately excluded from this table: it is a real cross-artifact anchor
  // (`external: true` in nav.ts), not a client-side route — clicking it performs a real browser
  // navigation to /docs/, which this plain (un-assembled) SPA preview server has no directory for.
  // See "the Docs nav link is a real anchor, not a client-side route" below instead.
  const PRIMARY_NAV_LINKS: ReadonlyArray<readonly [label: string, path: string]> = [
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

  // Two regression guards in one link.
  //
  // The first is the /docs route-collision bug: the SPA used to own a client-side route at /docs
  // (agentforge4j-web-ui/src/pages/DocsPage.tsx, removed) that intercepted every click before a real
  // browser request for the composed Docusaurus artifact could ever happen. The nav entry must
  // render as a plain anchor (a real navigation), never a client-side <Link>.
  //
  // The second is the entry point itself. `/docs/` is not a page — it is the client-redirect stub
  // the docs redirects plugin generates, a title-less near-empty 200 whose only content is a meta
  // refresh. Linking it made every visit to the documentation take an extra hop, and it was the only
  // route in. The link must target the real versioned documentation tree instead.
  //
  // Asserted by shape, not by version: which version is current is derived at build time from the
  // docs module's own version lifecycle (agentforge4j-web-ui/scripts/build-docs-entry.mjs), so a
  // literal here would have to be edited at every release — exactly the coupling that fix avoids.
  test('the Docs nav link is a real anchor into the versioned documentation tree, not a client-side route and not the redirect stub', async ({
    page,
  }) => {
    await page.goto('/');
    const docsLink = page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Docs' });
    const href = await docsLink.getAttribute('href');
    expect(href).toMatch(/^\/docs\/.+\/$/);
    expect(href).not.toBe('/docs/');
  });

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
