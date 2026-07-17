// SPDX-License-Identifier: Apache-2.0

import { useEffect, useMemo, useRef, useState } from 'react';
import type { BuilderPersistenceAdapter } from '../api/types';
import type { CanvasModel } from '../model/canvasModel';
import { createLocalStoragePersistence } from '../persistence/localStoragePersistence';
import { isRestorableCanvasModel } from '../persistence/canvasModelGuard';
import { createInitialCanvasModel } from './useCanvasState';
import { isUntouchedStarterCanvas } from './useBuilderMode';

/** Debounce window for persisting model edits; avoids writing on every keystroke. */
const SAVE_DEBOUNCE_MS = 500;

function warnPersistence(message: string, err?: unknown): void {
  if (typeof console !== 'undefined' && typeof console.warn === 'function') {
    if (err === undefined) {
      console.warn(`[workflow-builder] ${message}`);
    } else {
      console.warn(`[workflow-builder] ${message}`, err);
    }
  }
}

export type UseModelPersistenceOptions = {
  /** Host-supplied adapter; falls back to the built-in localStorage adapter when omitted. */
  persistence: BuilderPersistenceAdapter | undefined;
  /** Current canvas model. */
  model: CanvasModel;
  /** Programmatic setter that bypasses dirty-tracking (mirrors `useCanvasState.setModelFromLoad`). */
  setModelFromLoad: (model: CanvasModel) => void;
  /**
   * Whether a saved draft may silently replace the current model on mount. Disabled when the
   * host supplied an `initialWorkflow` at all (host-seeded identity or content must never be
   * recovered over) or when the builder is read-only (a read-only view is not the user's own
   * draft).
   */
  allowRestore: boolean;
  /** Whether edits are persisted at all. Disabled when the builder is read-only. */
  allowSave: boolean;
};

export type UseModelPersistenceResult = {
  /** True once a prior draft has been silently restored and the notice should be shown. */
  restored: boolean;
  /** Dismiss the restored-session notice without discarding the restored draft. */
  dismissRestoredNotice: () => void;
  /** Clear the saved draft and reset the canvas back to a fresh starter model. */
  startFresh: () => void;
};

/**
 * Wires load-on-mount / debounced-save-on-change draft recovery for the canvas model, plus a
 * best-effort `beforeunload` warning for the page-unload gap between an edit and the debounce
 * flushing. A pending (not yet flushed) save is also flushed on unmount, so SPA navigation away
 * from the builder — which never fires `beforeunload` — cannot silently drop recent edits.
 *
 * Uses a host-supplied {@link BuilderPersistenceAdapter} when provided; otherwise falls back to
 * the built-in localStorage-backed adapter. The package never makes a network call for this
 * either way.
 *
 * Restore is fail-closed: a loaded draft must pass a structural plausibility check
 * (`isRestorableCanvasModel`) before it may replace the live model, and it is skipped entirely
 * if the user has already started editing by the time an (async) `load()` resolves — a slow
 * host backend must never clobber in-progress work with a stale draft.
 */
export function useModelPersistence({
  persistence,
  model,
  setModelFromLoad,
  allowRestore,
  allowSave,
}: UseModelPersistenceOptions): UseModelPersistenceResult {
  const defaultAdapter = useMemo(() => createLocalStoragePersistence(), []);
  const adapter = persistence ?? defaultAdapter;

  const [restored, setRestored] = useState(false);
  // Starts true so the initial (starter/seeded) model on mount is not immediately
  // re-persisted; set back to true whenever a programmatic (non-user) load replaces the
  // model, so that replacement is not mistaken for an edit to save.
  const skipNextSaveRef = useRef(true);
  const pendingFlushRef = useRef(false);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Always-current mirrors for the unmount flush and the async-load race guard, which both
  // need the *latest* values from inside mount-scoped effects.
  const latestModelRef = useRef(model);
  latestModelRef.current = model;
  const adapterRef = useRef(adapter);
  adapterRef.current = adapter;
  const allowSaveRef = useRef(allowSave);
  allowSaveRef.current = allowSave;

  // Load on mount.
  useEffect(() => {
    if (!allowRestore) {
      return;
    }
    let cancelled = false;
    Promise.resolve(adapter.load())
      .then((loaded) => {
        if (cancelled || !loaded) {
          return;
        }
        if (!isRestorableCanvasModel(loaded)) {
          // A host adapter resolved something the current builder cannot safely render
          // (stale shape, truncated write). Skip the restore — restoring would crash the
          // editor at render time, outside this promise chain, on every remount. The
          // built-in adapter additionally self-clears such drafts on load.
          warnPersistence('Ignoring a saved draft with an unrecognized shape; starting fresh.');
          return;
        }
        if (isUntouchedStarterCanvas(loaded)) {
          return;
        }
        if (!isUntouchedStarterCanvas(latestModelRef.current)) {
          // The user already started editing before (an async) load() resolved — never
          // replace their in-progress work with the stored draft.
          return;
        }
        skipNextSaveRef.current = true;
        setModelFromLoad(loaded);
        setRestored(true);
      })
      .catch((err) => {
        warnPersistence('Failed to load a saved draft; starting fresh.', err);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only load
  }, []);

  // Debounced save on every meaningful model change.
  useEffect(() => {
    if (!allowSave) {
      return undefined;
    }
    if (skipNextSaveRef.current) {
      skipNextSaveRef.current = false;
      return undefined;
    }
    pendingFlushRef.current = true;
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    debounceTimerRef.current = setTimeout(() => {
      pendingFlushRef.current = false;
      try {
        void Promise.resolve(adapter.save(model)).catch((err) => {
          warnPersistence('Failed to save the draft.', err);
        });
      } catch (err) {
        warnPersistence('Failed to save the draft.', err);
      }
    }, SAVE_DEBOUNCE_MS);
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [model, adapter, allowSave]);

  // Flush a pending (debounced but not yet written) save on unmount. `beforeunload` below
  // only covers page unloads; an SPA route change unmounts the builder without any browser
  // prompt, and the debounced-save effect's own cleanup would otherwise just drop the timer —
  // losing everything typed since the last ≥debounce-quiet flush.
  useEffect(() => {
    return () => {
      if (!pendingFlushRef.current || !allowSaveRef.current) {
        return;
      }
      pendingFlushRef.current = false;
      try {
        void Promise.resolve(adapterRef.current.save(latestModelRef.current)).catch((err) => {
          warnPersistence('Failed to flush the draft on unmount.', err);
        });
      } catch (err) {
        warnPersistence('Failed to flush the draft on unmount.', err);
      }
    };
  }, []);

  // Best-effort secondary safety net for the gap between an edit and the debounce flushing.
  useEffect(() => {
    if (!allowSave || typeof window === 'undefined') {
      return undefined;
    }
    const onBeforeUnload = (event: BeforeUnloadEvent): void => {
      if (!pendingFlushRef.current) {
        return;
      }
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
    };
  }, [allowSave]);

  const dismissRestoredNotice = (): void => {
    setRestored(false);
  };

  const startFresh = (): void => {
    skipNextSaveRef.current = true;
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    pendingFlushRef.current = false;
    try {
      void Promise.resolve(adapter.clear?.()).catch((err) => {
        warnPersistence('Failed to clear the saved draft.', err);
      });
    } catch (err) {
      warnPersistence('Failed to clear the saved draft.', err);
    }
    setModelFromLoad(createInitialCanvasModel());
    setRestored(false);
  };

  return { restored, dismissRestoredNotice, startFresh };
}
