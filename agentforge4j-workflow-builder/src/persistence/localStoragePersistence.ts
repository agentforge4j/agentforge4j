// SPDX-License-Identifier: Apache-2.0

import type { BuilderPersistenceAdapter } from '../api/types';
import type { CanvasModel } from '../model/canvasModel';
import { isRestorableCanvasModel } from './canvasModelGuard';

/** localStorage key for the built-in draft-recovery persistence adapter. */
const DRAFT_KEY = 'agentforge_builder_draft';

/**
 * Version stamp written into every stored draft envelope. Bump whenever the persisted
 * `CanvasModel` shape changes incompatibly; drafts stored under any other version are
 * discarded on load rather than restored into code that no longer understands them.
 */
export const DRAFT_STORAGE_VERSION = 1;

type StoredDraft = {
  version: number;
  model: CanvasModel;
};

/**
 * Built-in, browser-local persistence adapter used when a host does not supply its own
 * `persistence` prop. Backed entirely by `window.localStorage` — the package never makes
 * a network call for this, so the standalone builder never requires a server.
 *
 * The stored value is a `{ version, model }` envelope (see {@link DRAFT_STORAGE_VERSION});
 * on load, a missing/mismatched version or a structurally implausible model (see
 * `isRestorableCanvasModel`) causes the stored draft to be **removed and ignored** —
 * fail-closed, so a stale-shape draft from an older builder version can never crash the
 * editor on every mount.
 *
 * This adapter keeps a single global draft slot: concurrent tabs (or multiple builder
 * instances sharing an origin) overwrite each other last-write-wins. See the
 * `BuilderPersistenceAdapter` docs; hosts needing per-workflow or per-user drafts should
 * supply their own adapter.
 *
 * All operations are best-effort: a quota error, disabled storage, or corrupt stored
 * value is swallowed rather than surfaced, since losing the local draft-recovery
 * convenience is preferable to crashing the editor.
 */
export function createLocalStoragePersistence(): BuilderPersistenceAdapter {
  const removeStoredDraft = (): void => {
    try {
      window.localStorage.removeItem(DRAFT_KEY);
    } catch {
      // Best-effort; see the adapter contract note above.
    }
  };

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
        let parsed: unknown;
        try {
          parsed = JSON.parse(raw);
        } catch {
          removeStoredDraft();
          return null;
        }
        const envelope = parsed as Partial<StoredDraft> | null;
        if (
          !envelope ||
          typeof envelope !== 'object' ||
          envelope.version !== DRAFT_STORAGE_VERSION ||
          !isRestorableCanvasModel(envelope.model)
        ) {
          // Unknown version or implausible shape: discard so it cannot be offered again.
          removeStoredDraft();
          return null;
        }
        return envelope.model;
      } catch {
        return null;
      }
    },
    save: (model: CanvasModel): void => {
      if (typeof window === 'undefined') {
        return;
      }
      try {
        const envelope: StoredDraft = { version: DRAFT_STORAGE_VERSION, model };
        window.localStorage.setItem(DRAFT_KEY, JSON.stringify(envelope));
      } catch {
        // Best-effort: storage may be full, disabled, or unavailable (e.g. private
        // browsing). Draft recovery is a convenience, not a guarantee.
      }
    },
    clear: (): void => {
      if (typeof window === 'undefined') {
        return;
      }
      removeStoredDraft();
    },
  };
}
