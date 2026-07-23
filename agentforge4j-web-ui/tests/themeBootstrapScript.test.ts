// SPDX-License-Identifier: Apache-2.0
//
// index.html carries a hand-written, pre-render bootstrap script that sets `data-theme` on
// <html> before the SPA's bundle loads (see that file's own comment for why it can't just import
// theme.ts). This test extracts that EXACT script and proves it resolves identically to
// theme.ts's real resolveEffectiveTheme/currentThemeMode for every representative input —
// closing the "two independent copies of the same algorithm can silently drift apart" gap a pure
// documentation comment can't.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, test } from 'vitest';
import { THEME_STORAGE_KEY, resolveEffectiveTheme, type ThemeMode } from '@/theme/theme';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

function extractBootstrapScript(): string {
  const html = readFileSync(join(MODULE_ROOT, 'index.html'), 'utf8');
  const start = html.indexOf('<!-- theme-bootstrap:start');
  const end = html.indexOf('<!-- theme-bootstrap:end');
  if (start === -1 || end === -1 || end < start) {
    throw new Error('themeBootstrapScript.test: could not find the theme-bootstrap markers in index.html');
  }
  const region = html.slice(start, end);
  const scriptMatch = /<script>([\s\S]*?)<\/script>/.exec(region);
  if (!scriptMatch) {
    throw new Error('themeBootstrapScript.test: no <script> found between the theme-bootstrap markers');
  }
  return scriptMatch[1];
}

/** Runs the extracted bootstrap script against a fresh, fully-controlled fake `window`/
 *  `document`, returning the `data-theme` value it set — never against the real jsdom
 *  `window`/`document` the rest of the suite shares, so this can't leak state between tests. */
function runBootstrapScript(scriptSource: string, storedValue: string | null, systemPrefersDark: boolean): string | null {
  let dataTheme: string | null = null;
  const fakeWindow = {
    localStorage: {
      getItem: (key: string) => (key === THEME_STORAGE_KEY ? storedValue : null),
    },
    matchMedia: (query: string) => ({
      matches: query.includes('dark') ? systemPrefersDark : false,
    }),
  };
  const fakeDocument = {
    documentElement: {
      setAttribute: (name: string, value: string) => {
        if (name === 'data-theme') {
          dataTheme = value;
        }
      },
    },
  };
  // eslint-disable-next-line no-new-func -- deliberately sandboxing the exact production script.
  const runner = new Function('window', 'document', scriptSource);
  runner(fakeWindow, fakeDocument);
  return dataTheme;
}

function throwingLocalStorage() {
  return {
    getItem: () => {
      throw new Error('SecurityError');
    },
  };
}

describe('index.html theme-bootstrap script', () => {
  const scriptSource = extractBootstrapScript();

  test('uses the exact THEME_STORAGE_KEY literal theme.ts declares', () => {
    expect(scriptSource).toContain(`'${THEME_STORAGE_KEY}'`);
  });

  test.each<[string, string | null, boolean, ThemeMode, boolean]>([
    ['no stored value, OS light', null, false, 'system', false],
    ['no stored value, OS dark', null, true, 'system', true],
    ['stored light, OS dark (explicit wins)', 'light', true, 'light', false],
    ['stored dark, OS light (explicit wins)', 'dark', false, 'dark', true],
    ['stored system, OS dark', 'system', true, 'system', true],
    ['stored system, OS light', 'system', false, 'system', false],
    ['invalid stored value falls back to system, OS dark', 'not-a-real-mode', true, 'system', true],
  ])('%s', (_label, stored, systemPrefersDark, equivalentMode, expectDark) => {
    const result = runBootstrapScript(scriptSource, stored, systemPrefersDark);
    expect(result).toBe(expectDark ? 'dark' : 'light');
    // Cross-checked against theme.ts's own real function for the equivalent (mode, systemPrefersDark)
    // pair, not just a hard-coded expectation — the two must agree by construction.
    expect(result).toBe(resolveEffectiveTheme(equivalentMode, systemPrefersDark));
  });

  test('falls back to light when localStorage.getItem throws (matches theme.ts\'s fallback behaviour)', () => {
    let dataTheme: string | null = null;
    const fakeWindow = {
      localStorage: throwingLocalStorage(),
      matchMedia: () => ({ matches: false }),
    };
    const fakeDocument = {
      documentElement: { setAttribute: (name: string, value: string) => { if (name === 'data-theme') dataTheme = value; } },
    };
    const runner = new Function('window', 'document', scriptSource);
    expect(() => runner(fakeWindow, fakeDocument)).not.toThrow();
    expect(dataTheme).toBe('light');
  });

  test('falls back to light when matchMedia throws, for system mode with no stored value', () => {
    let dataTheme: string | null = null;
    const fakeWindow = {
      localStorage: { getItem: () => null },
      matchMedia: () => {
        throw new Error('matchMedia unsupported');
      },
    };
    const fakeDocument = {
      documentElement: { setAttribute: (name: string, value: string) => { if (name === 'data-theme') dataTheme = value; } },
    };
    const runner = new Function('window', 'document', scriptSource);
    expect(() => runner(fakeWindow, fakeDocument)).not.toThrow();
    expect(dataTheme).toBe('light');
  });
});
