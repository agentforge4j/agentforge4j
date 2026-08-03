// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { SITE_REDIRECTS, SITE_ROUTES } from '../../support/web-ui/routes';

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

  for (const redirect of SITE_REDIRECTS) {
    // Two separate tests on purpose, because only one of them can actually catch the defect.
    //
    // The defect is a stub whose forward target is a hard-coded ABSOLUTE production URL, which
    // sends every non-production origin off the artifact under test and onto the live public site.
    // A browser-behaviour test cannot prove that: with no egress to the public origin (CI sandbox,
    // offline laptop) the off-origin load simply never completes, the SPA's own client-side
    // <Navigate> has already landed on the destination, and the assertion passes while the served
    // markup is still wrong. Verified by mutation — an absolute stub passed a navigation-only
    // version of this test. So the markup is asserted directly, and the behaviour is asserted
    // separately for what it can honestly prove.
    test(`${redirect.path} is served as a stub that forwards within the serving origin`, async ({ request }) => {
      // The trailing-slash form, deliberately. Every generated shell is a directory
      // (dist/<path>/index.html) and only that address serves it — the bare form falls through to
      // the SPA's root index.html, which carries no stub markup at all, so requesting it would make
      // every assertion below a statement about the home page.
      const response = await request.get(`${redirect.path}/`, { maxRedirects: 0 });
      expect(response.status()).toBe(200);
      const html = await response.text();

      // Proves the response really is the stub and not that fallback, so the checks below cannot
      // pass (or fail) for the wrong reason if the server's routing ever changes.
      expect(html, 'expected the redirect stub, not the SPA fallback shell').toContain('content="noindex, follow"');

      const refresh = /<meta http-equiv="refresh" content="0;\s*url=([^"]+)"/.exec(html);
      expect(refresh, `${redirect.path}/ serves no meta refresh at all`).not.toBeNull();
      expect(refresh?.[1]).toBe(redirect.destination);

      // The fallback anchor is the way through for a client that honours neither the refresh nor
      // JavaScript, so it must stay on this origin too.
      const anchor = /<div id="root"><p><a href="([^"]+)"/.exec(html);
      expect(anchor, `${redirect.path}/ serves no fallback anchor`).not.toBeNull();
      expect(anchor?.[1]).toBe(redirect.destination);

      // Neither may name a scheme or a host — the two shapes that leave the origin.
      for (const value of [refresh?.[1], anchor?.[1]]) {
        expect(value, 'a redirect stub must not name an origin').toMatch(/^\/(?!\/)/);
      }
    });

    test(`${redirect.path} arrives at ${redirect.destination} in the browser`, async ({ page }) => {
      // The served address, same reason as above: the bare form never reaches the stub, so this
      // would only ever exercise the SPA's own <Navigate> and never the shipped shell.
      await page.goto(`${redirect.path}/`);
      await expect(page.getByRole('heading', { level: 1, name: redirect.destinationHeading })).toBeVisible();
      expect(new URL(page.url()).pathname).toBe(redirect.destination);
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
