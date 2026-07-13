// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../copy/workflow-terminology';
import { useEffect } from 'react';

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
 */
export function ConfirmDeleteDialog({ singleStepLabel, count, onConfirm, onCancel }: ConfirmDeleteDialogProps) {
  useEffect(() => {
    if (count === 0) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancel();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [count, onCancel]);

  if (count === 0) {
    return null;
  }

  const title =
    count === 1 && singleStepLabel ? ACTION_LABELS.confirmDeleteTitleNamed(singleStepLabel) : ACTION_LABELS.confirmDeleteTitle(count);

  return (
    <>
      <div className="wf-confirm-dialog__backdrop" role="presentation" onClick={onCancel} />
      <div className="wf-panel wf-confirm-dialog" role="alertdialog" aria-modal="true" aria-label={title}>
        <header className="wf-panel__header">
          <h2 className="wf-panel__title">{title}</h2>
        </header>
        <div className="wf-panel__body">
          <p>{ACTION_LABELS.confirmDeleteBody(count)}</p>
        </div>
        <footer className="wf-confirm-dialog__footer">
          <button type="button" className="wf-button wf-button--secondary" onClick={onCancel}>
            {ACTION_LABELS.confirmDeleteCancel}
          </button>
          <button type="button" className="wf-button wf-button--destructive" autoFocus onClick={onConfirm}>
            {ACTION_LABELS.confirmDeleteConfirm}
          </button>
        </footer>
      </div>
    </>
  );
}
