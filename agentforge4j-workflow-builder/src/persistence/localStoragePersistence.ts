// SPDX-License-Identifier: Apache-2.0

import type { BuilderPersistenceAdapter } from '../api/types';
import type { CanvasModel } from '../model/canvasModel';

/** localStorage key for the built-in draft-recovery persistence adapter. */
const DRAFT_KEY = 'agentforge_builder_draft';

/**
 * Built-in, browser-local persistence adapter used when a host does not supply its own
 * `persistence` prop. Backed entirely by `window.localStorage` — the package never makes
 * a network call for this, so the standalone builder never requires a server.
 *
 * All operations are best-effort: a quota error, disabled storage, or corrupt stored
 * value is swallowed rather than surfaced, since losing the local draft-recovery
 * convenience is preferable to crashing the editor.
 */
export function createLocalStoragePersistence(): BuilderPersistenceAdapter {
  return {
    load: (): CanvasModel | null => {
      if (typeof window === 'undefined') {
        return null;
      }
      try {
        const raw = window.localStorage.getItem(DRAFT_KEY);
        if (!raw) {
          return null;
        }
        return JSON.parse(raw) as CanvasModel;
      } catch {
        return null;
      }
    },
    save: (model: CanvasModel): void => {
      if (typeof window === 'undefined') {
        return;
      }
      try {
        window.localStorage.setItem(DRAFT_KEY, JSON.stringify(model));
      } catch {
        // Best-effort: storage may be full, disabled, or unavailable (e.g. private
        // browsing). Draft recovery is a convenience, not a guarantee.
      }
    },
    clear: (): void => {
      if (typeof window === 'undefined') {
        return;
      }
      try {
        window.localStorage.removeItem(DRAFT_KEY);
      } catch {
        // Best-effort; see `save`.
      }
    },
  };
}
