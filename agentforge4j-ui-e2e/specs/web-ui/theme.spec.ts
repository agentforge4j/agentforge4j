// SPDX-License-Identifier: Apache-2.0
//
// Real-browser coverage for the marketing SPA's light/dark/system theme (src/theme/,
// src/components/ThemeToggle.tsx). Unit tests (agentforge4j-web-ui/tests/theme.test.ts,
// ThemeContext.test.tsx, ThemeToggle.test.tsx) already cover the resolution logic and component
// behaviour in isolation under jsdom — this file proves the same behaviour against a real,
// production-built page: actual localStorage persistence across a real navigation/reload,
// actual `prefers-color-scheme` emulation, and that representative pages render usable content
// in both themes, not just that the attribute/token machinery is wired correctly in the abstract.

import { expect, test } from '@playwright/test';

const THEME_STORAGE_KEY = 'agentforge4j.theme';

test.describe('theme: persistence and system reactivity', () => {
  test('with no stored preference, a dark OS preference is honoured on first load', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });

  test('with no stored preference, a light OS preference is honoured on first load', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  });

  test('choosing Dark persists across a real reload, even if the OS preference is light', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await page.getByRole('button', { name: /Theme/ }).click();
    await page.getByRole('menuitemradio', { name: 'Dark' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    const stored = await page.evaluate((key) => window.localStorage.getItem(key), THEME_STORAGE_KEY);
    expect(stored).toBe('dark');
  });

  test('choosing Light persists across a real reload, even if the OS preference is dark', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await page.getByRole('button', { name: /Theme/ }).click();
    await page.getByRole('menuitemradio', { name: 'Light' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  });

  test('choosing System persists explicitly and continues to react to OS changes after reload', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /Theme/ }).click();
    await page.getByRole('menuitemradio', { name: 'Dark' }).click();
    await page.getByRole('button', { name: /Theme/ }).click();
    await page.getByRole('menuitemradio', { name: 'System' }).click();

    const stored = await page.evaluate((key) => window.localStorage.getItem(key), THEME_STORAGE_KEY);
    expect(stored).toBe('system');

    await page.emulateMedia({ colorScheme: 'dark' });
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });

  test('no light-theme flash: data-theme is already correct on the very first frame, before the SPA bundle runs', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    // Intercept the moment the document starts parsing, before any deferred/module script runs.
    await page.goto('/', { waitUntil: 'commit' });
    const themeAtCommit = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    expect(themeAtCommit).toBe('dark');
  });
});

test.describe('theme control: accessibility and layout', () => {
  test('is present and operable at a mobile viewport, not only desktop', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    const trigger = page.getByRole('button', { name: /Theme/ });
    await expect(trigger).toBeVisible();
    await trigger.click();
    await expect(page.getByRole('menuitemradio', { name: 'Dark' })).toBeVisible();
  });

  test('does not cause the header to overflow horizontally at a narrow mobile width', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });

  test('the trigger has a visible focus outline in both themes', async ({ page }) => {
    // Keyboard-only throughout (no mouse click anywhere in this test): a browser's
    // `:focus-visible` heuristic is input-modality-sensitive, and mixing a preceding mouse click
    // with a later programmatic/keyboard focus on the SAME element is a real, known way to get an
    // unrepresentative result — not a signal about the underlying CSS, which is identical
    // (theme-token-driven) in both modes.
    await page.goto('/');
    await page.keyboard.press('Tab'); // skip link
    await page.keyboard.press('Tab'); // logo
    // ... continue tabbing until the theme trigger itself has focus, identified by its own role.
    const trigger = page.getByRole('button', { name: /Theme/ });
    for (let i = 0; i < 20 && !(await trigger.evaluate((el) => el === document.activeElement)); i += 1) {
      await page.keyboard.press('Tab');
    }
    await expect(trigger).toBeFocused();
    const lightOutline = await page.evaluate(() => getComputedStyle(document.activeElement as Element).outlineStyle);
    expect(lightOutline).not.toBe('none');

    await page.keyboard.press('Enter'); // open the menu
    await page.keyboard.press('ArrowUp'); // System -> Dark
    await page.keyboard.press('Enter'); // select Dark, closes and refocuses the trigger
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await expect(trigger).toBeFocused();
    const darkOutline = await page.evaluate(() => getComputedStyle(document.activeElement as Element).outlineStyle);
    expect(darkOutline).not.toBe('none');
  });
});

test.describe('theme: representative pages render correctly in both themes', () => {
  const ROUTES = ['/', '/use', '/releases', '/architecture', '/catalogue/agent-creator'];

  for (const route of ROUTES) {
    test(`${route} has no unreadable (identical fg/bg) text and no console errors in dark mode`, async ({ page }) => {
      const consoleErrors: string[] = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') {
          consoleErrors.push(msg.text());
        }
      });
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.goto(route);
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

      // A real, if coarse, "not obviously broken" signal: the resolved body background and
      // foreground colours must actually differ once dark tokens are applied — the exact defect
      // class a forgotten raw hard-coded colour (unaffected by the token swap) would produce.
      const [bg, fg] = await page.evaluate(() => {
        const style = getComputedStyle(document.body);
        return [style.backgroundColor, style.color];
      });
      expect(bg).not.toBe(fg);
      expect(consoleErrors).toEqual([]);
    });
  }

  test('the architecture page diagrams keep their light "paper" frame in dark mode (documented exception, not a raw-colour regression)', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/architecture');
    const frame = page.locator('img.diagram-frame').first();
    await expect(frame).toBeVisible();
    const bg = await frame.evaluate((el) => getComputedStyle(el).backgroundColor);
    // rgb(248, 249, 251) == #f8f9fb, the documented deliberate light frame colour.
    expect(bg).toBe('rgb(248, 249, 251)');
  });

  test('the header logo switches to the dark-background variant in dark mode', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await expect(page.getByAltText('AgentForge4j')).toHaveAttribute('src', '/brand/logo-horizontal-dark.svg');
  });

  test('the header logo stays the standard variant in light mode', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await expect(page.getByAltText('AgentForge4j')).toHaveAttribute('src', '/brand/logo-horizontal.svg');
  });
});
