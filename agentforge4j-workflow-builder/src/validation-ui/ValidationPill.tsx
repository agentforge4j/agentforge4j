// SPDX-License-Identifier: Apache-2.0

import { ValidationPanel } from './ValidationPanel';
import { ACTION_LABELS } from '../copy/workflow-terminology';
import type { DraftValidationIssue } from '../hooks/useWorkflowDraft';
import type { CanvasModel } from '../model/canvasModel';
import type { BuilderTheme } from '../api/types';
import type { CSSProperties } from 'react';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

type ValidationPillProps = {
  model: CanvasModel;
  clientIssues: DraftValidationIssue[];
  serverIssues?: DraftValidationIssue[];
  onFix: (stepId?: string) => void;
  className?: string;
  /**
   * The host theme applied on the builder root. Because the popover is portaled to
   * `document.body` — outside the themed root element — its CSS custom properties and any
   * `theme.className`-scoped host rules would otherwise not reach it; the portal container
   * re-applies both so a themed host never gets a default-palette popover.
   */
  theme?: BuilderTheme;
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
  theme,
}: ValidationPillProps) {
  const [open, setOpen] = useState(false);
  const [popoverPosition, setPopoverPosition] = useState<CSSProperties | null>(null);
  const anchorRef = useRef<HTMLDivElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const toggleButtonRef = useRef<HTMLButtonElement>(null);
  // Whether the *next* close should restore focus to the toggle button. Defaults to true (the ×
  // path establishes no new focus target, so restoring is correct there), is computed from focus
  // containment for Escape (see the keydown handler below — a user may have Tabbed elsewhere while
  // the popover stayed open), and is set to false just before any close path that has already
  // moved focus somewhere the user chose — an outside click (browser focuses the clicked element)
  // or "Fix" (focus follows the inspector it opens) — so the focus-management effect below never
  // overrides that with the toggle button instead. Read by the first effect below, reset by the
  // third.
  const restoreFocusOnCloseRef = useRef(true);
  const totalIssues = clientIssues.length + serverIssues.length;
  const looksGood = totalIssues === 0;

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        // Capture phase + stopPropagation: this popover is the top-most layered surface whenever
        // it is open, including above an already-open step inspector (which the portal fix above
        // now makes reachable simultaneously, with focus moved into this dialog). A window-level
        // capture listener always runs before any bubble-phase window listener — including the
        // inspector's own Escape handler — for the same event, regardless of mount order, so one
        // Escape press dismisses only this popover and never reaches the inspector beneath it.
        event.stopPropagation();
        // Only restore focus to the toggle button if it is still inside the popover at this
        // moment. A keyboard user can Tab out of the popover to some other control while it stays
        // open (nothing but a pointer click closes it early) and then press Escape out of habit —
        // restoring unconditionally would steal focus back from that other control.
        restoreFocusOnCloseRef.current = Boolean(
          popoverRef.current && document.activeElement && popoverRef.current.contains(document.activeElement),
        );
        setOpen(false);
      }
    };
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (anchorRef.current?.contains(target) || popoverRef.current?.contains(target)) {
        return;
      }
      // The browser has already (or is about to) move focus to whatever the user clicked as part
      // of the click's own default action — restoring focus to the toggle button here would
      // override that and steal focus from the element the user just interacted with.
      restoreFocusOnCloseRef.current = false;
      setOpen(false);
    };
    window.addEventListener('keydown', onKeyDown, true);
    window.addEventListener('mousedown', onPointerDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown, true);
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
        // documentElement.clientWidth, not window.innerWidth: the containing block for
        // position: fixed (like getBoundingClientRect coordinates) excludes the vertical
        // scrollbar, which innerWidth includes — using innerWidth shifts the popover left
        // by the scrollbar width whenever the page scrolls.
        right: document.documentElement.clientWidth - rect.right,
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

  // Portaling to document.body detaches the popover from the toggle button's tab-order
  // adjacency it had before this fix (it was the button's next DOM sibling), so keyboard/AT
  // users now need explicit focus management: move focus into the dialog once it actually
  // mounts (guarded by `hasFocusedRef` so the later position recomputes on scroll/resize don't
  // keep stealing focus back), and restore it to the toggle button once the dialog closes —
  // but only on an actual open→close transition, never on the initial mount where `open` starts
  // `false` (that would steal focus from wherever the page already was).
  const hasFocusedRef = useRef(false);
  const wasOpenRef = useRef(false);
  useEffect(() => {
    if (open && popoverPosition && popoverRef.current && !hasFocusedRef.current) {
      popoverRef.current.focus();
      hasFocusedRef.current = true;
    }
    if (!open) {
      hasFocusedRef.current = false;
      if (wasOpenRef.current && restoreFocusOnCloseRef.current) {
        toggleButtonRef.current?.focus();
      }
      restoreFocusOnCloseRef.current = true;
    }
    wasOpenRef.current = open;
  }, [open, popoverPosition]);

  return (
    <div ref={anchorRef} className={['wf-validation-pill-anchor', className].filter(Boolean).join(' ')}>
      <button
        ref={toggleButtonRef}
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
              tabIndex={-1}
              className={['wf-panel', 'wf-validation-pill__popover', 'wf-validation-pill__popover--portal', theme?.className]
                .filter(Boolean)
                .join(' ')}
              style={{ ...popoverPosition, ...(theme?.variables as CSSProperties | undefined) }}
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
                  onClick={() => {
                    restoreFocusOnCloseRef.current = true;
                    setOpen(false);
                  }}
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
                    // `onFix` (WorkflowBuilder's `focusIssue`) selects the step and requests
                    // `focusField: 'panel'`, which moves focus into the inspector once it opens —
                    // don't have the close effect steal it back to the toggle button.
                    restoreFocusOnCloseRef.current = false;
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
