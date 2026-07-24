// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import {
  THEME_STORAGE_KEY,
  currentThemeMode,
  isThemeMode,
  readStoredThemeMode,
  resolveEffectiveTheme,
  writeStoredThemeMode,
} from '@/theme/theme';

function fakeStorage(initial: Record<string, string> = {}): Storage {
  const store = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  } as Storage;
}

describe('isThemeMode', () => {
  test.each(['light', 'dark', 'system'])('%s is a valid mode', (value) => {
    expect(isThemeMode(value)).toBe(true);
  });

  test.each([null, undefined, 42, '', 'System', 'LIGHT', 'auto', 'high-contrast'])(
    '%s is not a valid mode',
    (value) => {
      expect(isThemeMode(value)).toBe(false);
    },
  );
});

describe('resolveEffectiveTheme', () => {
  test('light mode resolves to light regardless of system preference', () => {
    expect(resolveEffectiveTheme('light', true)).toBe('light');
    expect(resolveEffectiveTheme('light', false)).toBe('light');
  });

  test('dark mode resolves to dark regardless of system preference', () => {
    expect(resolveEffectiveTheme('dark', true)).toBe('dark');
    expect(resolveEffectiveTheme('dark', false)).toBe('dark');
  });

  test('system mode follows the system preference', () => {
    expect(resolveEffectiveTheme('system', true)).toBe('dark');
    expect(resolveEffectiveTheme('system', false)).toBe('light');
  });
});

describe('readStoredThemeMode', () => {
  test('returns the stored value when it is a valid mode', () => {
    expect(readStoredThemeMode(fakeStorage({ [THEME_STORAGE_KEY]: 'dark' }))).toBe('dark');
  });

  test('returns null when nothing is stored', () => {
    expect(readStoredThemeMode(fakeStorage())).toBeNull();
  });

  test('returns null (not the raw value) for an invalid/hand-edited stored value', () => {
    expect(readStoredThemeMode(fakeStorage({ [THEME_STORAGE_KEY]: 'blue' }))).toBeNull();
  });

  test('returns null when storage is undefined (unavailable) rather than throwing', () => {
    expect(readStoredThemeMode(undefined)).toBeNull();
  });

  test('returns null when .getItem throws rather than propagating the error', () => {
    const throwing: Pick<Storage, 'getItem'> = {
      getItem: () => {
        throw new Error('SecurityError: storage disabled');
      },
    };
    expect(readStoredThemeMode(throwing)).toBeNull();
  });
});

describe('writeStoredThemeMode', () => {
  test('persists a valid mode under the documented key', () => {
    const storage = fakeStorage();
    writeStoredThemeMode(storage, 'dark');
    expect(storage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  test('does not throw when storage is undefined', () => {
    expect(() => writeStoredThemeMode(undefined, 'light')).not.toThrow();
  });

  test('does not throw when .setItem throws (quota exceeded, security policy, etc.)', () => {
    const throwing: Pick<Storage, 'setItem'> = {
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    };
    expect(() => writeStoredThemeMode(throwing, 'dark')).not.toThrow();
  });
});

describe('currentThemeMode', () => {
  test('defaults to "system" when no explicit preference has ever been stored', () => {
    expect(currentThemeMode(fakeStorage())).toBe('system');
  });

  test('defaults to "system" when the stored value is invalid', () => {
    expect(currentThemeMode(fakeStorage({ [THEME_STORAGE_KEY]: 'not-a-real-mode' }))).toBe('system');
  });

  test('returns an explicitly stored light preference', () => {
    expect(currentThemeMode(fakeStorage({ [THEME_STORAGE_KEY]: 'light' }))).toBe('light');
  });

  test('returns an explicitly stored dark preference', () => {
    expect(currentThemeMode(fakeStorage({ [THEME_STORAGE_KEY]: 'dark' }))).toBe('dark');
  });

  test('returns an explicitly stored "system" preference (distinct from "never chosen", but behaves the same today)', () => {
    expect(currentThemeMode(fakeStorage({ [THEME_STORAGE_KEY]: 'system' }))).toBe('system');
  });
});
