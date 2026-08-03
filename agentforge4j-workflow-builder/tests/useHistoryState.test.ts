// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useHistoryState } from '../src/state/useHistoryState';

describe('useHistoryState', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts with no undo/redo available', () => {
    const { result } = renderHook(() => useHistoryState(0));
    expect(result.current.present).toBe(0);
    expect(result.current.canUndo).toBe(false);
    expect(result.current.canRedo).toBe(false);
  });

  it('set without a coalesceKey always pushes a distinct, individually undoable entry', () => {
    const { result } = renderHook(() => useHistoryState(0));
    act(() => result.current.set(1));
    act(() => result.current.set(2));
    expect(result.current.present).toBe(2);

    act(() => result.current.undo());
    expect(result.current.present).toBe(1);
    act(() => result.current.undo());
    expect(result.current.present).toBe(0);
    expect(result.current.canUndo).toBe(false);
  });

  it('redo restores an undone value and is cleared once a new set diverges history', () => {
    const { result } = renderHook(() => useHistoryState(0));
    act(() => result.current.set(1));
    act(() => result.current.undo());
    expect(result.current.canRedo).toBe(true);

    act(() => result.current.redo());
    expect(result.current.present).toBe(1);
    expect(result.current.canRedo).toBe(false);

    act(() => result.current.undo());
    act(() => result.current.set(99));
    expect(result.current.canRedo).toBe(false);
  });

  it('coalesces same-key calls within the debounce window into a single undo step', () => {
    const { result } = renderHook(() => useHistoryState('', { debounceMs: 500 }));
    act(() => result.current.set('a', { coalesceKey: 'field' }));
    act(() => result.current.set('ab', { coalesceKey: 'field' }));
    act(() => result.current.set('abc', { coalesceKey: 'field' }));

    expect(result.current.present).toBe('abc');
    act(() => result.current.undo());
    expect(result.current.present).toBe(''); // the whole "typing session" undone at once
    expect(result.current.canUndo).toBe(false);
  });

  it('a different coalesceKey never merges with the prior group', () => {
    const { result } = renderHook(() => useHistoryState('', { debounceMs: 500 }));
    act(() => result.current.set('a', { coalesceKey: 'field-a' }));
    act(() => result.current.set('b', { coalesceKey: 'field-b' }));

    act(() => result.current.undo());
    expect(result.current.present).toBe('a');
    act(() => result.current.undo());
    expect(result.current.present).toBe('');
  });

  it('a distinct (no coalesceKey) call seals an in-progress group so a later same-key call starts fresh', () => {
    const { result } = renderHook(() => useHistoryState('', { debounceMs: 500 }));
    act(() => result.current.set('a', { coalesceKey: 'field' }));
    act(() => result.current.set('structural'));
    act(() => result.current.set('b', { coalesceKey: 'field' }));

    expect(result.current.present).toBe('b');
    act(() => result.current.undo());
    expect(result.current.present).toBe('structural');
    act(() => result.current.undo());
    expect(result.current.present).toBe('a');
    act(() => result.current.undo());
    expect(result.current.present).toBe('');
  });

  it('commit:true seals the group even when the next call reuses the same key (drag-end semantics)', () => {
    const { result } = renderHook(() => useHistoryState(0, { debounceMs: 500 }));
    act(() => result.current.set(1, { coalesceKey: 'drag', commit: false }));
    act(() => result.current.set(2, { coalesceKey: 'drag', commit: true })); // drag ends here
    act(() => result.current.set(3, { coalesceKey: 'drag', commit: false })); // a new, separate drag

    expect(result.current.present).toBe(3);
    act(() => result.current.undo());
    expect(result.current.present).toBe(2); // second drag undone on its own
    act(() => result.current.undo());
    expect(result.current.present).toBe(0); // first drag (0->1->2) undone as one step
    expect(result.current.canUndo).toBe(false);
  });

  it('a call outside the debounce window starts a new entry even with the same key', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useHistoryState(0, { debounceMs: 50 }));
    act(() => result.current.set(1, { coalesceKey: 'k' }));
    act(() => vi.advanceTimersByTime(1000));
    act(() => result.current.set(2, { coalesceKey: 'k' }));

    expect(result.current.present).toBe(2);
    act(() => result.current.undo());
    expect(result.current.present).toBe(1);
    act(() => result.current.undo());
    expect(result.current.present).toBe(0);
  });

  it('reset replaces present and clears all history — not itself undoable', () => {
    const { result } = renderHook(() => useHistoryState(0));
    act(() => result.current.set(1));
    act(() => result.current.reset(100));

    expect(result.current.present).toBe(100);
    expect(result.current.canUndo).toBe(false);
    expect(result.current.canRedo).toBe(false);
  });

  it('keeps coalescing a sticky (gesture) group across an arbitrarily long mid-gesture pause, until commit seals it', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useHistoryState(0, { debounceMs: 50 }));
    act(() => result.current.set(1, { coalesceKey: 'drag', sticky: true }));
    // The pointer holds still far past the debounce window mid-drag...
    act(() => vi.advanceTimersByTime(10_000));
    act(() => result.current.set(2, { coalesceKey: 'drag', sticky: true }));
    act(() => result.current.set(3, { coalesceKey: 'drag', sticky: true, commit: true })); // release

    expect(result.current.present).toBe(3);
    act(() => result.current.undo());
    // The whole gesture — pause included — undoes as ONE step.
    expect(result.current.present).toBe(0);
    expect(result.current.canUndo).toBe(false);
  });

  it('coalescing boundaries survive StrictMode double-invocation (updater purity)', () => {
    const { result } = renderHook(() => useHistoryState('', { debounceMs: 500 }), { wrapper: StrictMode });

    // A distinct entry, then a coalescing session: under StrictMode, React invokes state
    // updaters twice in development — an impure updater that stamps its own coalescing ref
    // would classify the session's FIRST edit as already-coalescing on the second invocation
    // and silently merge it into the distinct entry below.
    act(() => result.current.set('structural'));
    act(() => result.current.set('a', { coalesceKey: 'field' }));
    act(() => result.current.set('ab', { coalesceKey: 'field' }));

    expect(result.current.present).toBe('ab');
    act(() => result.current.undo());
    expect(result.current.present).toBe('structural'); // typing session undone as one step, NOT merged into 'structural'
    act(() => result.current.undo());
    expect(result.current.present).toBe('');
    expect(result.current.canUndo).toBe(false);
  });

  it('caps retained history at maxHistory, dropping the oldest entries', () => {
    const { result } = renderHook(() => useHistoryState(0, { maxHistory: 3 }));
    for (let i = 1; i <= 10; i += 1) {
      act(() => result.current.set(i));
    }
    let undoCount = 0;
    while (result.current.canUndo) {
      act(() => result.current.undo());
      undoCount += 1;
    }
    expect(undoCount).toBe(3);
  });
});
