// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../copy/workflow-terminology';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { useCallback, useEffect, useRef } from 'react';

export type ConfirmDeleteDialogProps = {
  /** The step name to show for a single-step deletion; omitted (or a multi-step
   * batch) falls back to a count-only title. */
  singleStepLabel?: string;
  /** Number of steps pending deletion; the dialog renders nothing when 0. */
  count: number;
  onConfirm: () => void;
  onCancel: () => void;
  /**
   * Focus target used on a CANCEL close when the element that opened the dialog is no
   * longer in the document. Not consulted on a CONFIRM close — the deletion that close
   * gates has not committed yet (it commits after this dialog unmounts), so restoring
   * focus here would target an element about to be removed out from under it; the
   * caller takes over focus once the deletion actually lands (see
   * {@link ConfirmDeleteDialog} focus-behavior notes). Should point at something always
   * connected while the builder is mounted (its root element, made programmatically
   * focusable via `tabIndex={-1}`).
   */
  fallbackFocusRef?: { current: HTMLElement | null };
};

const BODY_ID = 'wf-confirm-dialog-body';

/**
 * Lightweight, low-friction confirmation gate for a destructive step deletion
 * — the complementary safety net alongside undo/redo for a first-time user who
 * has not yet discovered undo. Shared by every deletion trigger (inspector
 * "Delete step" button, canvas Delete/Backspace key) so the confirmation
 * experience is identical regardless of entry point.
 *
 * Focus behavior: initial focus goes to Cancel (the least destructive action,
 * per the ARIA alertdialog pattern — a stray Enter must not delete), Tab is
 * trapped inside the dialog while it is open. On a CANCEL close (button,
 * backdrop, or Escape), focus returns to whatever element opened it — or, if
 * that element is gone, to `fallbackFocusRef`. On a CONFIRM close, this
 * component does NOT restore focus itself: the gated deletion is still
 * in-flight when the dialog unmounts (it commits afterward, asynchronously),
 * so the usual restoration target (the opener) is about to be removed from
 * the document too. The caller is responsible for focusing a stable target
 * once the deletion it requested actually completes — see
 * `WorkflowBuilder`'s `requestDeleteNode` / `onNodeDeletionCommitted`.
 */
export function ConfirmDeleteDialog({
  singleStepLabel,
  count,
  onConfirm,
  onCancel,
  fallbackFocusRef,
}: ConfirmDeleteDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const outcomeRef = useRef<'confirm' | 'cancel' | null>(null);

  const handleConfirm = useCallback((): void => {
    outcomeRef.current = 'confirm';
    onConfirm();
  }, [onConfirm]);
  const handleCancel = useCallback((): void => {
    outcomeRef.current = 'cancel';
    onCancel();
  }, [onCancel]);

  useEffect(() => {
    if (count === 0) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') {
        return;
      }
      // Scoped to this dialog's own subtree (focus is trapped inside it while open,
      // so a real keypress always targets somewhere in here) — a window-wide,
      // unscoped listener would let ANY Escape in the document cancel this dialog,
      // including one meant for a host page's own overlay or a second builder
      // instance's own confirmation dialog.
      const dialog = dialogRef.current;
      if (!dialog || !(event.target instanceof Node) || !dialog.contains(event.target)) {
        return;
      }
      // Capture-phase + stopPropagation: the step inspector (and other overlays)
      // also close on window-level Escape; while this dialog is open, Escape means
      // "cancel the deletion" only — it must not also close the inspector the
      // deletion was requested from (button-cancel keeps it open, so key-cancel
      // must too).
      event.stopPropagation();
      handleCancel();
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [count, handleCancel]);

  // Initial focus + CANCEL-only focus restoration (see the CONFIRM-close note in this
  // component's own doc comment above and on `fallbackFocusRef`).
  useEffect(() => {
    if (count === 0) {
      return;
    }
    outcomeRef.current = null;
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    cancelButtonRef.current?.focus();
    return () => {
      if (outcomeRef.current === 'confirm') {
        return;
      }
      if (previouslyFocused && previouslyFocused.isConnected) {
        previouslyFocused.focus();
      } else {
        fallbackFocusRef?.current?.focus();
      }
    };
  }, [count, fallbackFocusRef]);

  if (count === 0) {
    return null;
  }

  // Minimal focus trap: Tab/Shift+Tab cycle within the dialog's own focusable controls.
  const onDialogKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>): void => {
    if (event.key !== 'Tab') {
      return;
    }
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>('button:not([disabled])'));
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0]!;
    const last = focusable[focusable.length - 1]!;
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const title =
    count === 1 && singleStepLabel ? ACTION_LABELS.confirmDeleteTitleNamed(singleStepLabel) : ACTION_LABELS.confirmDeleteTitle(count);

  return (
    <>
      <div className="wf-confirm-dialog__backdrop" role="presentation" onClick={handleCancel} />
      <div
        ref={dialogRef}
        className="wf-panel wf-confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-label={title}
        aria-describedby={BODY_ID}
        onKeyDown={onDialogKeyDown}
      >
        <header className="wf-panel__header">
          <h2 className="wf-panel__title">{title}</h2>
        </header>
        <div className="wf-panel__body">
          <p id={BODY_ID}>{ACTION_LABELS.confirmDeleteBody(count)}</p>
        </div>
        <footer className="wf-confirm-dialog__footer">
          <button ref={cancelButtonRef} type="button" className="wf-button wf-button--secondary" onClick={handleCancel}>
            {ACTION_LABELS.confirmDeleteCancel}
          </button>
          <button type="button" className="wf-button wf-button--destructive" onClick={handleConfirm}>
            {ACTION_LABELS.confirmDeleteConfirm}
          </button>
        </footer>
      </div>
    </>
  );
}
