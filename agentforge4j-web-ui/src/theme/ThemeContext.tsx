// SPDX-License-Identifier: Apache-2.0
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  currentThemeMode,
  resolveEffectiveTheme,
  writeStoredThemeMode,
  type EffectiveTheme,
  type ThemeMode,
} from './theme';

const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)';

function safeLocalStorage(): Storage | undefined {
  try {
    return window.localStorage;
  } catch {
    // Some browsers throw merely on *accessing* window.localStorage under certain security
    // policies (not just on .getItem/.setItem) — treated identically to "storage unavailable".
    return undefined;
  }
}

function readSystemPrefersDark(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  return window.matchMedia(DARK_MEDIA_QUERY).matches;
}

interface ThemeContextValue {
  /** The user's current selection — `'system'` unless they have explicitly picked light or dark. */
  readonly mode: ThemeMode;
  /** The theme actually being painted right now — what callers that need a concrete light/dark
   *  branch (e.g. picking a logo variant) should read. */
  readonly effectiveTheme: EffectiveTheme;
  /** Sets and persists an explicit choice (including explicitly choosing `'system'`). */
  readonly setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  // Lazy initializers run once, synchronously, on first render — before paint, matching (and
  // idempotent with) the inline bootstrap script in index.html that already set `data-theme` on
  // `<html>` before the SPA's bundle even started loading. This does not itself prevent a flash
  // (that's the bootstrap script's job); it just makes React's own state agree with what's
  // already on screen from the very first render, so there is no *second*, JS-driven flip once
  // hydration/mount completes.
  const [mode, setModeState] = useState<ThemeMode>(() => currentThemeMode(safeLocalStorage()));
  const [systemPrefersDark, setSystemPrefersDark] = useState<boolean>(() => readSystemPrefersDark());

  const effectiveTheme = useMemo(() => resolveEffectiveTheme(mode, systemPrefersDark), [mode, systemPrefersDark]);

  // React to OS-level changes only while in 'system' mode — an explicit light/dark choice must
  // never silently flip just because the OS preference changed underneath it.
  useEffect(() => {
    if (mode !== 'system' || typeof window.matchMedia !== 'function') {
      return;
    }
    const media = window.matchMedia(DARK_MEDIA_QUERY);
    const onChange = (event: MediaQueryListEvent) => setSystemPrefersDark(event.matches);
    // addEventListener is the standard API (supported by every browser this project targets);
    // no legacy addListener fallback is carried, matching this codebase's baseline elsewhere.
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [mode]);

  // Keep the DOM attribute (the actual thing every CSS token selector matches on) in sync with
  // React state on every relevant change — covers both an explicit setMode call and a system
  // preference change picked up by the listener above.
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', effectiveTheme);
  }, [effectiveTheme]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    writeStoredThemeMode(safeLocalStorage(), next);
  }, []);

  const value = useMemo<ThemeContextValue>(() => ({ mode, effectiveTheme, setMode }), [mode, effectiveTheme, setMode]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return ctx;
}
