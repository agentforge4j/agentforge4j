// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../copy/workflow-terminology';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { useEffect, useRef } from 'react';

export type ConfirmDeleteDialogProps = {
  /** The step name to show for a single-step deletion; omitted (or a multi-step
   * batch) falls back to a count-only title. */
  singleStepLabel?: string;
  /** Number of steps pending deletion; the dialog renders nothing when 0. */
  count: number;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * Lightweight, low-friction confirmation gate for a destructive step deletion
 * — the complementary safety net alongside undo/redo (Ctrl+Z) for a
 * first-time user who has not yet discovered undo. Shared by every deletion
 * trigger (inspector "Delete step" button, canvas Delete/Backspace key) so
 * the confirmation experience is identical regardless of entry point.
 *
 * Focus behavior: initial focus goes to Cancel (the least destructive action,
 * per the ARIA alertdialog pattern — a stray Enter must not delete), Tab is
 * trapped inside the dialog while it is open, and focus returns to whatever
 * element opened it once it closes.
 */
export function ConfirmDeleteDialog({ singleStepLabel, count, onConfirm, onCancel }: ConfirmDeleteDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (count === 0) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        // Capture-phase + stopPropagation: the step inspector (and other overlays)
        // also close on window-level Escape; while this dialog is open, Escape means
        // "cancel the deletion" only — it must not also close the inspector the
        // deletion was requested from (button-cancel keeps it open, so key-cancel
        // must too).
        event.stopPropagation();
        onCancel();
      }
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [count, onCancel]);

  // Initial focus + focus restoration. Focus moves to Cancel on open; on close, it
  // returns to the element that had it (the delete button / canvas) so keyboard users
  // are not dropped at the document root.
  useEffect(() => {
    if (count === 0) {
      return;
    }
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    cancelButtonRef.current?.focus();
    return () => {
      if (previouslyFocused && previouslyFocused.isConnected) {
        previouslyFocused.focus();
      }
    };
  }, [count]);

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
      <div className="wf-confirm-dialog__backdrop" role="presentation" onClick={onCancel} />
      <div
        ref={dialogRef}
        className="wf-panel wf-confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-label={title}
        onKeyDown={onDialogKeyDown}
      >
        <header className="wf-panel__header">
          <h2 className="wf-panel__title">{title}</h2>
        </header>
        <div className="wf-panel__body">
          <p>{ACTION_LABELS.confirmDeleteBody(count)}</p>
        </div>
        <footer className="wf-confirm-dialog__footer">
          <button ref={cancelButtonRef} type="button" className="wf-button wf-button--secondary" onClick={onCancel}>
            {ACTION_LABELS.confirmDeleteCancel}
          </button>
          <button type="button" className="wf-button wf-button--destructive" onClick={onConfirm}>
            {ACTION_LABELS.confirmDeleteConfirm}
          </button>
        </footer>
      </div>
    </>
  );
}
