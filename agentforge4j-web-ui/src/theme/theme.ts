// SPDX-License-Identifier: Apache-2.0
//
// Pure theme-resolution logic, deliberately dependency-free (no React, no DOM access beyond the
// injectable `storage`/`matchMedia` seams below) so it can run both inside the React tree
// (ThemeContext.tsx) and inside the pre-render bootstrap script that sets the initial
// `data-theme` attribute before the SPA mounts (see index.html's inline <script> and
// themeBootstrapScript.ts, which documents why that script is a separate, hand-written copy of
// this exact algorithm rather than an import from here).

/** A user's theme selection. `'system'` is itself an explicit, persisted choice — distinct from
 *  "no selection has ever been made" (`readStoredThemeMode()` returning `null`), which also
 *  currently resolves to the system preference but is not persisted until the user actually
 *  picks something (including picking "System" explicitly). */
export type ThemeMode = 'light' | 'dark' | 'system';

/** The two states a page can actually be painted in — what `data-theme` on `<html>` is set to. */
export type EffectiveTheme = 'light' | 'dark';

export const THEME_MODES: readonly ThemeMode[] = ['light', 'dark', 'system'];

/** localStorage key. Stable and documented because two independent places must read/write the
 *  exact same key: the React ThemeProvider and the pre-render bootstrap script in index.html
 *  (which cannot import this module — it runs before any bundle loads). Keep both in sync by
 *  hand if this ever changes; `themeBootstrapScript.test.ts` cross-checks the literal string in
 *  `index.html` against this constant so the two cannot silently drift apart. */
export const THEME_STORAGE_KEY = 'agentforge4j.theme';

export function isThemeMode(value: unknown): value is ThemeMode {
  return typeof value === 'string' && (THEME_MODES as readonly string[]).includes(value);
}

/**
 * Reads the persisted theme mode, tolerating every real-world way `localStorage` can misbehave:
 * unavailable entirely (private browsing in some browsers, a security policy, a non-browser test
 * environment), throwing on `.getItem` (some browsers throw on quota/security errors rather than
 * returning null), or holding a value that isn't one of the three valid modes (hand-edited,
 * written by a future version with a fourth mode, or corrupted). Every one of these cases
 * resolves to `null` — "no explicit preference" — never a thrown error that would break the page.
 */
export function readStoredThemeMode(storage: Pick<Storage, 'getItem'> | undefined): ThemeMode | null {
  if (!storage) {
    return null;
  }
  try {
    const raw = storage.getItem(THEME_STORAGE_KEY);
    return isThemeMode(raw) ? raw : null;
  } catch {
    return null;
  }
}

/**
 * Persists an explicit theme mode. Swallows a throwing `.setItem` (quota exceeded, security
 * policy, private-browsing restrictions in some browsers) rather than crashing the theme
 * switch — the mode still applies to the current page; it just won't be remembered next visit,
 * which is a strictly better failure mode than an uncaught exception breaking the toggle.
 */
export function writeStoredThemeMode(storage: Pick<Storage, 'setItem'> | undefined, mode: ThemeMode): void {
  if (!storage) {
    return;
  }
  try {
    storage.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // Best-effort only — see doc comment above.
  }
}

/** Resolves a `ThemeMode` to the concrete `EffectiveTheme` that should actually be painted,
 *  given whether the OS/browser currently reports a dark-mode preference. */
export function resolveEffectiveTheme(mode: ThemeMode, systemPrefersDark: boolean): EffectiveTheme {
  if (mode === 'light') {
    return 'light';
  }
  if (mode === 'dark') {
    return 'dark';
  }
  return systemPrefersDark ? 'dark' : 'light';
}

/** The mode actually in effect right now: the stored explicit choice, or `'system'` if none has
 *  ever been made — this is the single "no preference exists" default the whole app uses. */
export function currentThemeMode(storage: Pick<Storage, 'getItem'> | undefined): ThemeMode {
  return readStoredThemeMode(storage) ?? 'system';
}
