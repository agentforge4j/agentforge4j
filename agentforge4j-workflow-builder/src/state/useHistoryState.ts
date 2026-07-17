// SPDX-License-Identifier: Apache-2.0

import { useCallback, useRef, useState } from 'react';

/** Options controlling how a `set` call folds into the undo/redo history. */
export interface HistorySetOptions {
  /**
   * Groups consecutive `set` calls that share the same key into a single
   * undoable step, as long as they land within the debounce window (see
   * {@link UseHistoryStateOptions.debounceMs}). Use this for continuous input
   * (typing, dragging) so every keystroke/tick does not become its own undo
   * step. Omit for discrete, always-distinct actions (add/delete/connect/…) —
   * omitting also seals any coalescing group already in progress, so
   * switching to a distinct action never merges with a preceding typing/drag
   * session.
   */
  coalesceKey?: string;
  /**
   * Seals the coalescing group started by this call: a later `set` call, even
   * with the same `coalesceKey`, always starts a new history entry. Pass this
   * on the call that ends a gesture (e.g. drag-release).
   */
  commit?: boolean;
  /**
   * Marks the coalescing group as a pointer gesture: subsequent same-key calls
   * keep coalescing regardless of the debounce window, until the group is
   * sealed by `commit` (or by any non-coalescing call). Use for drags — a drag
   * held still mid-gesture emits no changes for arbitrarily long, and must not
   * split into multiple undo entries when movement resumes. Typing keeps the
   * windowed behavior (omit this) so distinct typing sessions stay separate.
   */
  sticky?: boolean;
}

export interface UseHistoryStateOptions {
  /** Idle window (ms) within which same-key `set` calls coalesce. */
  debounceMs?: number;
  /** Maximum number of past snapshots retained (oldest dropped beyond this). */
  maxHistory?: number;
}

export interface UseHistoryStateResult<T> {
  present: T;
  canUndo: boolean;
  canRedo: boolean;
  set: (updater: T | ((prev: T) => T), options?: HistorySetOptions) => void;
  /** Seals any in-progress coalescing group without changing `present`. */
  commit: () => void;
  undo: () => void;
  redo: () => void;
  /** Replaces `present` and clears all history — for a full reload/import, not itself undoable. */
  reset: (next: T) => void;
}

interface HistorySnapshot<T> {
  past: T[];
  present: T;
  future: T[];
}

const DEFAULT_DEBOUNCE_MS = 500;
const DEFAULT_MAX_HISTORY = 100;

/**
 * Generic past/present/future undo-redo wrapper around a single piece of
 * state. Consecutive `set` calls that share a `coalesceKey` within the
 * debounce window collapse into one history entry (keystroke/drag
 * coalescing); calls without a `coalesceKey` are always distinct,
 * individually undoable steps.
 */
export function useHistoryState<T>(initial: T, options?: UseHistoryStateOptions): UseHistoryStateResult<T> {
  const debounceMs = options?.debounceMs ?? DEFAULT_DEBOUNCE_MS;
  const maxHistory = options?.maxHistory ?? DEFAULT_MAX_HISTORY;

  const [state, setState] = useState<HistorySnapshot<T>>({ past: [], present: initial, future: [] });
  const coalesceRef = useRef<{ key: string; timestamp: number; sticky: boolean } | null>(null);

  const set = useCallback(
    (updater: T | ((prev: T) => T), setOptions?: HistorySetOptions) => {
      // The coalescing decision (and the ref/time bookkeeping it depends on) happens
      // OUTSIDE the setState updater: updaters must be pure — React StrictMode
      // double-invokes them in development, and a ref mutated by the first invocation
      // would make the second one mis-classify the very same call as coalescing,
      // silently merging an edit into the previous undo entry.
      const now = Date.now();
      const key = setOptions?.coalesceKey;
      const ongoing = coalesceRef.current;
      const coalesces =
        key !== undefined &&
        ongoing !== null &&
        ongoing.key === key &&
        (ongoing.sticky || now - ongoing.timestamp <= debounceMs);

      coalesceRef.current =
        key !== undefined && !setOptions?.commit ? { key, timestamp: now, sticky: setOptions?.sticky === true } : null;

      setState((current) => {
        const nextPresent = typeof updater === 'function' ? (updater as (prev: T) => T)(current.present) : updater;

        let past = current.past;
        if (!coalesces) {
          past = [...current.past, current.present];
          if (past.length > maxHistory) {
            past = past.slice(past.length - maxHistory);
          }
        }

        return { past, present: nextPresent, future: [] };
      });
    },
    [debounceMs, maxHistory],
  );

  const commit = useCallback(() => {
    coalesceRef.current = null;
  }, []);

  const undo = useCallback(() => {
    coalesceRef.current = null;
    setState((current) => {
      if (current.past.length === 0) {
        return current;
      }
      const previous = current.past[current.past.length - 1]!;
      return {
        past: current.past.slice(0, -1),
        present: previous,
        future: [current.present, ...current.future],
      };
    });
  }, []);

  const redo = useCallback(() => {
    coalesceRef.current = null;
    setState((current) => {
      if (current.future.length === 0) {
        return current;
      }
      const next = current.future[0]!;
      return {
        past: [...current.past, current.present],
        present: next,
        future: current.future.slice(1),
      };
    });
  }, []);

  const reset = useCallback((next: T) => {
    coalesceRef.current = null;
    setState({ past: [], present: next, future: [] });
  }, []);

  return {
    present: state.present,
    canUndo: state.past.length > 0,
    canRedo: state.future.length > 0,
    set,
    commit,
    undo,
    redo,
    reset,
  };
}
