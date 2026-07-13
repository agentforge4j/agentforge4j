// SPDX-License-Identifier: Apache-2.0

import { ValidationPanel } from './ValidationPanel';
import { ACTION_LABELS } from '../copy/workflow-terminology';
import type { DraftValidationIssue } from '../hooks/useWorkflowDraft';
import type { CanvasModel } from '../model/canvasModel';
import type { CSSProperties } from 'react';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

type ValidationPillProps = {
  model: CanvasModel;
  clientIssues: DraftValidationIssue[];
  serverIssues?: DraftValidationIssue[];
  onFix: (stepId?: string) => void;
  className?: string;
};

// Gap between the pill and its popover, mirroring the --builder-space-xs default this stylesheet
// otherwise uses for the same purpose. A fixed px value (rather than resolving the CSS custom
// property from JS) is deliberate: the popover is positioned via a portal below, so this is a
// small cosmetic offset, not part of the occlusion fix itself.
const POPOVER_GAP_PX = 4;

export function ValidationPill({
  model,
  clientIssues,
  serverIssues = [],
  onFix,
  className,
}: ValidationPillProps) {
  const [open, setOpen] = useState(false);
  const [popoverPosition, setPopoverPosition] = useState<CSSProperties | null>(null);
  const anchorRef = useRef<HTMLDivElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
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
      const target = event.target as Node;
      if (anchorRef.current?.contains(target) || popoverRef.current?.contains(target)) {
        return;
      }
      setOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('mousedown', onPointerDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('mousedown', onPointerDown);
    };
  }, [open]);

  // The popover is portaled to document.body (see render below) so it always paints above an
  // open inspector panel, regardless of which ancestor stacking context the pill itself lives in
  // (the toolbar establishes its own stacking context via its own z-index, which a portal escapes
  // entirely — raising the popover's own z-index could never do that, since it stays scoped
  // inside that ancestor context). Since a portal detaches the popover from the anchor's
  // `position: relative` box, its position has to be computed from the anchor's own screen
  // coordinates instead of the CSS `top`/`right: 0` it used when it was a DOM descendant.
  useEffect(() => {
    if (!open) {
      setPopoverPosition(null);
      return;
    }
    const updatePosition = () => {
      const anchor = anchorRef.current;
      if (!anchor) {
        return;
      }
      const rect = anchor.getBoundingClientRect();
      setPopoverPosition({
        position: 'fixed',
        top: rect.bottom + POPOVER_GAP_PX,
        right: window.innerWidth - rect.right,
      });
    };
    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
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
      {open && popoverPosition
        ? createPortal(
            <div
              ref={popoverRef}
              className="wf-panel wf-validation-pill__popover wf-validation-pill__popover--portal"
              style={popoverPosition}
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
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}
