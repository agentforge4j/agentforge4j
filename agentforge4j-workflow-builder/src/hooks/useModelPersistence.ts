// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
 * Restore is fail-closed: a loaded draft must pass the full structural gate
 * (`isRestorableCanvasModel` — every field the editor dereferences, not just containers)
 * before it may replace the live model, and it is skipped entirely if the user has already
 * started editing by the time an (async) `load()` resolves — a slow host backend must never
 * clobber in-progress work with a stale draft.
 *
 * Adapter writes (`save`/`clear`) are serialized in invocation order through a single
 * promise chain, so "Start fresh" cannot be silently undone by a debounced save that was
 * still in flight when the draft was cleared.
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
  // Incremented each time a debounced save actually starts; lets an in-flight save's `.then`
  // tell whether a *later* edit has since kicked off another save before clearing
  // `pendingFlushRef` — otherwise an earlier, slower save resolving after a newer one has
  // already started would incorrectly mark the newer (still-unsaved) edit as protected.
  const saveGenerationRef = useRef(0);
  // Always-current mirrors for the unmount flush and the async-load race guard, which both
  // need the *latest* values from inside mount-scoped effects.
  const latestModelRef = useRef(model);
  latestModelRef.current = model;
  const adapterRef = useRef(adapter);
  adapterRef.current = adapter;
  const allowSaveRef = useRef(allowSave);
  allowSaveRef.current = allowSave;

  // Serializes every adapter *write* (save/clear) through one promise chain so completion
  // order always matches invocation order. Without this, "Start fresh" could dispatch
  // `clear()` while a debounced `save()` was still in flight on an async host adapter —
  // out-of-order completion (two independent HTTP calls, say) would re-persist the
  // discarded draft after the clear, resurrecting it on the next mount. A rejected or
  // synchronously-throwing operation is logged and never poisons the chain. `load()` stays
  // outside the queue: it is read-only and mount-scoped, so it has no ordering hazard.
  const writeQueueRef = useRef<Promise<void>>(Promise.resolve());
  const enqueueAdapterWrite = useCallback((operation: () => Promise<void> | void, failureMessage: string): void => {
    writeQueueRef.current = writeQueueRef.current
      .then(() => operation())
      .then(
        () => undefined,
        (err) => {
          warnPersistence(failureMessage, err);
        },
      );
  }, []);

  // Load on mount.
  useEffect(() => {
    if (!allowRestore) {
      return;
    }
    let cancelled = false;
    // Deferred into the chain (`.then(() => adapter.load())`, not `Promise.resolve(adapter.load())`)
    // so a *synchronously* throwing host `load` becomes a rejection routed to `.catch` below,
    // never an uncaught error that tears down the whole tree from inside a mount effect —
    // mirrors the same-file rationale in WorkflowBuilder.tsx for validateWorkflow.
    Promise.resolve()
      .then(() => adapter.load())
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
      const generation = ++saveGenerationRef.current;
      // Cleared only once this save actually settles successfully, and only if no later
      // edit — or "Start fresh" — has bumped the generation in the meantime (see
      // `saveGenerationRef` above) — while a save is still in flight, or has failed, both
      // the `beforeunload` warning and the unmount flush must keep treating this edit as
      // unprotected rather than assuming it landed.
      enqueueAdapterWrite(
        () =>
          Promise.resolve(adapterRef.current.save(model)).then(() => {
            if (saveGenerationRef.current === generation) {
              pendingFlushRef.current = false;
            }
          }),
        'Failed to save the draft.',
      );
    }, SAVE_DEBOUNCE_MS);
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
    // `adapterRef` (not `adapter`) is read inside the timeout so an unstable `persistence`
    // prop identity is not a dependency here — only `model` changes should schedule a new
    // save; re-running this effect on every adapter-identity change would otherwise
    // re-persist an unchanged model and re-arm the unmount/beforeunload nets for no edit.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- adapter read via adapterRef
  }, [model, allowSave]);

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
      enqueueAdapterWrite(() => adapterRef.current.save(latestModelRef.current), 'Failed to flush the draft on unmount.');
    };
  }, [enqueueAdapterWrite]);

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
    // A save whose debounce already fired may still be in flight; bump the generation so
    // its settlement can no longer mark the discarded state as protected, and enqueue the
    // clear on the write queue so it runs strictly after that save settles — see
    // `enqueueAdapterWrite` for why ordering matters here.
    saveGenerationRef.current += 1;
    // `clear` is required by BuilderPersistenceAdapter (TypeScript enforces this on every
    // conforming adapter), so this is a direct call, not `adapter.clear?.()` — the button's
    // whole meaning is "no saved draft remains", so silently skipping the clear for an
    // adapter that lacks one would make "Start fresh" misleading rather than merely
    // incomplete. The queue's failure handling is defense-in-depth for a JS
    // (non-TypeScript) caller that bypasses the type system and supplies an adapter object
    // without `clear`: the canvas still resets and the failure is logged, rather than the
    // action crashing.
    enqueueAdapterWrite(() => adapter.clear(), 'Failed to clear the saved draft.');
    setModelFromLoad(createInitialCanvasModel());
    setRestored(false);
  };

  return { restored, dismissRestoredNotice, startFresh };
}
