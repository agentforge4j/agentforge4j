// SPDX-License-Identifier: Apache-2.0

import { useEffect, useMemo, useRef, useState } from 'react';
import type { BuilderPersistenceAdapter } from '../api/types';
import type { CanvasModel } from '../model/canvasModel';
import { createLocalStoragePersistence } from '../persistence/localStoragePersistence';
import { createInitialCanvasModel } from './useCanvasState';
import { isUntouchedStarterCanvas } from './useBuilderMode';

/** Debounce window for persisting model edits; avoids writing on every keystroke. */
const SAVE_DEBOUNCE_MS = 500;

export type UseModelPersistenceOptions = {
  /** Host-supplied adapter; falls back to the built-in localStorage adapter when omitted. */
  persistence: BuilderPersistenceAdapter | undefined;
  /** Current canvas model. */
  model: CanvasModel;
  /** Programmatic setter that bypasses dirty-tracking (mirrors `useCanvasState.setModelFromLoad`). */
  setModelFromLoad: (model: CanvasModel) => void;
  /**
   * Whether a saved draft may silently replace the current model on mount. Disabled when the
   * host already seeded the builder with `initialWorkflow` content (nothing to "recover" over)
   * or when the builder is read-only (a read-only view is not the user's own draft).
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
 * best-effort `beforeunload` warning for the gap between an edit and the debounce flushing.
 *
 * Uses a host-supplied {@link BuilderPersistenceAdapter} when provided; otherwise falls back to
 * the built-in localStorage-backed adapter. The package never makes a network call for this
 * either way.
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

  // Load on mount.
  useEffect(() => {
    if (!allowRestore) {
      return;
    }
    let cancelled = false;
    Promise.resolve(adapter.load())
      .then((loaded) => {
        if (cancelled || !loaded || isUntouchedStarterCanvas(loaded)) {
          return;
        }
        skipNextSaveRef.current = true;
        setModelFromLoad(loaded);
        setRestored(true);
      })
      .catch((err) => {
        if (typeof console !== 'undefined' && typeof console.warn === 'function') {
          console.warn('[workflow-builder] Failed to load a saved draft; starting fresh.', err);
        }
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
      void adapter.save(model);
    }, SAVE_DEBOUNCE_MS);
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [model, adapter, allowSave]);

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
    void adapter.clear?.();
    setModelFromLoad(createInitialCanvasModel());
    setRestored(false);
  };

  return { restored, dismissRestoredNotice, startFresh };
}
