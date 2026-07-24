// SPDX-License-Identifier: Apache-2.0
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from '@/theme/ThemeContext';
import { THEME_STORAGE_KEY } from '@/theme/theme';

/** A controllable `window.matchMedia('(prefers-color-scheme: dark)')` fake — the shared,
 *  always-`matches: false`, non-simulatable stub in tests/setup.ts can't drive the
 *  system-preference-reactivity tests below, so this file installs its own per test. */
function installMatchMediaMock(initialMatches: boolean) {
  let matches = initialMatches;
  let changeHandler: ((event: { matches: boolean }) => void) | null = null;
  const mql = {
    get matches() {
      return matches;
    },
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: (event: string, handler: (e: { matches: boolean }) => void) => {
      if (event === 'change') {
        changeHandler = handler;
      }
    },
    removeEventListener: (event: string, handler: (e: { matches: boolean }) => void) => {
      if (event === 'change' && changeHandler === handler) {
        changeHandler = null;
      }
    },
    dispatchEvent: () => false,
  };
  window.matchMedia = vi.fn().mockReturnValue(mql);
  return {
    simulateChange(next: boolean) {
      matches = next;
      changeHandler?.({ matches: next });
    },
  };
}

function TestHarness() {
  const { mode, effectiveTheme, setMode } = useTheme();
  return (
    <div>
      <p data-testid="mode">{mode}</p>
      <p data-testid="effective">{effectiveTheme}</p>
      <button onClick={() => setMode('light')}>Set light</button>
      <button onClick={() => setMode('dark')}>Set dark</button>
      <button onClick={() => setMode('system')}>Set system</button>
    </div>
  );
}

function renderHarness() {
  return render(
    <ThemeProvider>
      <TestHarness />
    </ThemeProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
});

afterEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
});

describe('ThemeProvider: default system preference', () => {
  test('defaults to "system" mode and follows a light OS preference when nothing is stored', () => {
    installMatchMediaMock(false);
    renderHarness();
    expect(screen.getByTestId('mode')).toHaveTextContent('system');
    expect(screen.getByTestId('effective')).toHaveTextContent('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  test('defaults to "system" mode and follows a dark OS preference when nothing is stored', () => {
    installMatchMediaMock(true);
    renderHarness();
    expect(screen.getByTestId('mode')).toHaveTextContent('system');
    expect(screen.getByTestId('effective')).toHaveTextContent('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});

describe('ThemeProvider: persisted explicit selections', () => {
  test('a persisted light selection is honoured on load, regardless of OS preference', () => {
    installMatchMediaMock(true); // OS says dark — must not win over an explicit stored light choice.
    localStorage.setItem(THEME_STORAGE_KEY, 'light');
    renderHarness();
    expect(screen.getByTestId('mode')).toHaveTextContent('light');
    expect(screen.getByTestId('effective')).toHaveTextContent('light');
  });

  test('a persisted dark selection is honoured on load, regardless of OS preference', () => {
    installMatchMediaMock(false); // OS says light — must not win over an explicit stored dark choice.
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    renderHarness();
    expect(screen.getByTestId('mode')).toHaveTextContent('dark');
    expect(screen.getByTestId('effective')).toHaveTextContent('dark');
  });

  test('a persisted "system" selection behaves like the default — follows the OS preference', () => {
    installMatchMediaMock(true);
    localStorage.setItem(THEME_STORAGE_KEY, 'system');
    renderHarness();
    expect(screen.getByTestId('mode')).toHaveTextContent('system');
    expect(screen.getByTestId('effective')).toHaveTextContent('dark');
  });

  test('choosing light persists it under the documented storage key', async () => {
    installMatchMediaMock(false);
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set light' }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  test('choosing dark persists it under the documented storage key', async () => {
    installMatchMediaMock(false);
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set dark' }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  test('explicitly choosing "system" persists it too, not just "no value"', async () => {
    installMatchMediaMock(false);
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set dark' }));
    await user.click(screen.getByRole('button', { name: 'Set system' }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');
  });
});

describe('ThemeProvider: system-preference reactivity', () => {
  test('in system mode, a live OS preference change flips the effective theme', () => {
    const media = installMatchMediaMock(false);
    renderHarness();
    expect(screen.getByTestId('effective')).toHaveTextContent('light');
    act(() => media.simulateChange(true));
    expect(screen.getByTestId('effective')).toHaveTextContent('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  test('an explicit light choice does NOT react to a subsequent OS preference change', async () => {
    const media = installMatchMediaMock(false);
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set light' }));
    act(() => media.simulateChange(true)); // OS flips to dark — must have no effect.
    expect(screen.getByTestId('effective')).toHaveTextContent('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  test('an explicit dark choice does NOT react to a subsequent OS preference change', async () => {
    const media = installMatchMediaMock(true);
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set dark' }));
    act(() => media.simulateChange(false)); // OS flips to light — must have no effect.
    expect(screen.getByTestId('effective')).toHaveTextContent('dark');
  });

  // The media-query listener is deliberately detached while an explicit choice is
  // active, so an OS change during that window is invisible to `systemPrefersDark` until
  // something resyncs it. Re-entering 'system' must resync from the OS's CURRENT value
  // immediately — not keep showing the stale value cached from before the explicit choice,
  // and not require a second, later OS change event to correct itself.
  test('re-entering "system" after the OS changed while an explicit Light choice was active resyncs immediately, without another OS event or reload', async () => {
    const media = installMatchMediaMock(false); // OS starts light.
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set light' }));
    act(() => media.simulateChange(true)); // OS flips to dark while explicit Light is active.
    expect(screen.getByTestId('effective')).toHaveTextContent('light'); // still light — explicit wins.
    await user.click(screen.getByRole('button', { name: 'Set system' }));
    expect(screen.getByTestId('effective')).toHaveTextContent('dark'); // must resync immediately.
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  test('re-entering "system" after the OS changed while an explicit Dark choice was active resyncs immediately (inverse direction)', async () => {
    const media = installMatchMediaMock(true); // OS starts dark.
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByRole('button', { name: 'Set dark' }));
    act(() => media.simulateChange(false)); // OS flips to light while explicit Dark is active.
    expect(screen.getByTestId('effective')).toHaveTextContent('dark'); // still dark — explicit wins.
    await user.click(screen.getByRole('button', { name: 'Set system' }));
    expect(screen.getByTestId('effective')).toHaveTextContent('light'); // must resync immediately.
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });
});

describe('ThemeProvider: storage and stored-value edge cases', () => {
  test('an invalid stored value falls back to system behaviour rather than crashing', () => {
    installMatchMediaMock(true);
    localStorage.setItem(THEME_STORAGE_KEY, 'not-a-real-mode');
    renderHarness();
    expect(screen.getByTestId('mode')).toHaveTextContent('system');
    expect(screen.getByTestId('effective')).toHaveTextContent('dark');
  });

  test('storage that throws on access falls back to system behaviour rather than crashing the app', () => {
    installMatchMediaMock(false);
    const originalDescriptor = Object.getOwnPropertyDescriptor(window, 'localStorage');
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('SecurityError: localStorage disabled');
      },
    });
    try {
      expect(() => renderHarness()).not.toThrow();
      expect(screen.getByTestId('mode')).toHaveTextContent('system');
    } finally {
      if (originalDescriptor) {
        Object.defineProperty(window, 'localStorage', originalDescriptor);
      }
    }
  });
});

describe('ThemeProvider: root attribute application', () => {
  test('the html element carries data-theme before/at initial render, matching the resolved theme', () => {
    installMatchMediaMock(true);
    renderHarness();
    // No act()/await needed — the lazy useState initializer + the sync effect both run within
    // the same render/commit React Testing Library's render() already flushes.
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
