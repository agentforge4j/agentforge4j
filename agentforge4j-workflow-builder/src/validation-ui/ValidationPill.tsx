// SPDX-License-Identifier: Apache-2.0

import { ValidationPanel } from './ValidationPanel';
import { ACTION_LABELS } from '../copy/workflow-terminology';
import type { DraftValidationIssue } from '../hooks/useWorkflowDraft';
import type { CanvasModel } from '../model/canvasModel';
import { useEffect, useRef, useState } from 'react';

type ValidationPillProps = {
  model: CanvasModel;
  clientIssues: DraftValidationIssue[];
  serverIssues?: DraftValidationIssue[];
  onFix: (stepId?: string) => void;
  className?: string;
};

export function ValidationPill({
  model,
  clientIssues,
  serverIssues = [],
  onFix,
  className,
}: ValidationPillProps) {
  const [open, setOpen] = useState(false);
  const anchorRef = useRef<HTMLDivElement>(null);
  const totalIssues = clientIssues.length + serverIssues.length;
  const looksGood = totalIssues === 0;

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    const onPointerDown = (event: MouseEvent) => {
      if (!anchorRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('mousedown', onPointerDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('mousedown', onPointerDown);
    };
  }, [open]);

  return (
    <div ref={anchorRef} className={['wf-validation-pill-anchor', className].filter(Boolean).join(' ')}>
      <button
        type="button"
        className="wf-button wf-button--secondary wf-validation-pill"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={looksGood ? ACTION_LABELS.looksGood : ACTION_LABELS.thingsToFix(totalIssues)}
      >
        <span className="wf-validation-pill__glyph" aria-hidden>
          {looksGood ? '✓' : '!'}
        </span>
        <span className="wf-validation-pill__label-desktop">
          {looksGood ? `✓ ${ACTION_LABELS.looksGood}` : ACTION_LABELS.thingsToFix(totalIssues)}
        </span>
        <span className="wf-validation-pill__label-mobile">{looksGood ? ACTION_LABELS.okShort : String(totalIssues)}</span>
      </button>
      {open ? (
        <div
          className="wf-panel wf-validation-pill__popover"
          role="dialog"
          aria-label={ACTION_LABELS.clientValidation}
        >
          <header className="wf-panel__header">
            <h2 className="wf-panel__title">{ACTION_LABELS.clientValidation}</h2>
            <p className="wf-panel__description">{ACTION_LABELS.validationDetailsDescription}</p>
            <button
              type="button"
              className="wf-button wf-button--icon wf-button--ghost wf-panel__close"
              aria-label="Close"
              onClick={() => setOpen(false)}
            >
              ×
            </button>
          </header>
          <div className="wf-panel__body">
            <ValidationPanel
              model={model}
              clientIssues={clientIssues}
              serverIssues={serverIssues}
              onFix={(stepId) => {
                onFix(stepId);
                setOpen(false);
              }}
            />
          </div>
        </div>
      ) : null}
    </div>
  );
}
