// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS, BUILDER_COPY, PALETTE_GROUP_LABELS } from '../copy/workflow-terminology';
import type { BuilderMode } from '../hooks/useBuilderMode';
import {
  LIBRARY_ADVANCED_KINDS,
  LIBRARY_COMMON_KINDS,
  LIBRARY_FLOW_KINDS,
  NODE_KIND_META,
  type NodeKind,
} from '../model/nodeKinds';
import { useEffect, useState } from 'react';

type StepPaletteProps = {
  mode: BuilderMode;
  onAddStep: (kind: NodeKind) => void;
  defaultCollapsed?: boolean;
  /**
   * True when the builder's own rendered container is narrower than the supported
   * breakpoint. Drives the compact bottom-sheet palette variant. This deliberately comes
   * from the SAME container measurement as the builder's narrow-container gate (see
   * `useNarrowContainerGate`) rather than a viewport media query — the two axes can
   * disagree (a narrow viewport hosting a wide, scrollable builder panel), and a viewport
   * query would resurrect the mobile affordances the gate exists to make unreachable.
   * Under the gate this is effectively always false (the editor is replaced outright below
   * the breakpoint); the mobile variant is retained as the seed of the deferred mobile
   * editing surface.
   */
  containerNarrow?: boolean;
};

const COLLAPSED_RAIL_KINDS: NodeKind[] = [...LIBRARY_COMMON_KINDS, ...LIBRARY_FLOW_KINDS];

export function StepPalette({ mode, onAddStep, defaultCollapsed = true, containerNarrow = false }: StepPaletteProps) {
  const [expanded, setExpanded] = useState(!defaultCollapsed);
  const [advancedOpen, setAdvancedOpen] = useState(mode === 'advanced');
  const isMobile = containerNarrow;
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setAdvancedOpen(mode === 'advanced');
  }, [mode]);

  const renderKind = (kind: NodeKind, compact?: boolean) => {
    const meta = NODE_KIND_META[kind];
    return (
      <button
        key={kind}
        type="button"
        draggable
        title={meta.description}
        onDragStart={(e) => {
          e.dataTransfer.setData('application/agentforge-node-kind', kind);
          e.dataTransfer.effectAllowed = 'copy';
        }}
        onClick={() => {
          onAddStep(kind);
          if (isMobile) {
            setMobileOpen(false);
          }
        }}
        className={['wf-palette__item', compact ? 'wf-palette__item--compact' : ''].filter(Boolean).join(' ')}
        aria-label={meta.label}
        data-testid={`workflow-builder-palette-add-${kind.toLowerCase().replace(/_/g, '-')}`}
      >
        <span className="wf-palette__item-icon" aria-hidden>
          {meta.iconGlyph}
        </span>
        {!compact ? (
          <span className="wf-palette__item-text">
            <span className="wf-palette__item-label">{meta.label}</span>
            <span className="wf-palette__item-description">{meta.description}</span>
          </span>
        ) : null}
      </button>
    );
  };

  const renderGroup = (label: string, kinds: NodeKind[], compact?: boolean) => (
    <>
      <p className="wf-palette__section-label">{label}</p>
      <div className="wf-palette__list">{kinds.map((kind) => renderKind(kind, compact))}</div>
    </>
  );

  const panelContent = (
    <>
      <div className="wf-palette__header">
        <span className="wf-palette__title">{BUILDER_COPY.stepLibrary}</span>
        {!isMobile ? (
          <button
            type="button"
            className="wf-button wf-button--icon wf-button--ghost"
            aria-label={expanded ? BUILDER_COPY.collapsePalette : BUILDER_COPY.expandPalette}
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? '‹' : '›'}
          </button>
        ) : null}
      </div>
      <div className="wf-palette__body">
        {renderGroup(PALETTE_GROUP_LABELS.common, LIBRARY_COMMON_KINDS, !expanded && !isMobile)}
        {expanded || isMobile ? renderGroup(PALETTE_GROUP_LABELS.flow, LIBRARY_FLOW_KINDS, false) : null}
        {expanded || isMobile ? (
          <>
            <button
              type="button"
              className="wf-palette__advanced-toggle"
              onClick={() => setAdvancedOpen((o) => !o)}
            >
              <span aria-hidden>{advancedOpen ? '▾' : '▸'}</span>
              {mode === 'guided' ? ACTION_LABELS.advancedStepsGuided : PALETTE_GROUP_LABELS.advanced}
            </button>
            {advancedOpen ? (
              <div className="wf-palette__list">{LIBRARY_ADVANCED_KINDS.map((kind) => renderKind(kind, false))}</div>
            ) : null}
          </>
        ) : null}
      </div>
    </>
  );

  if (isMobile) {
    return (
      <div className="wf-palette wf-palette--mobile">
        <button
          type="button"
          className="wf-button wf-button--primary wf-palette__mobile-trigger"
          aria-label={ACTION_LABELS.addStep}
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((o) => !o)}
          data-testid="workflow-builder-palette-mobile-trigger"
        >
          + {ACTION_LABELS.addStep}
        </button>
        {mobileOpen ? (
          <div className="wf-palette__mobile-sheet" role="dialog" aria-label={BUILDER_COPY.stepLibrary}>
            <p className="wf-palette__mobile-description">{ACTION_LABELS.chooseStepDescription}</p>
            <div className="wf-palette__mobile-content">{panelContent}</div>
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <aside
      className={['wf-palette', expanded ? 'wf-palette--expanded' : 'wf-palette--collapsed'].join(' ')}
      onMouseEnter={() => {
        if (!expanded) {
          setExpanded(true);
        }
      }}
      onMouseLeave={() => {
        if (defaultCollapsed) {
          setExpanded(false);
        }
      }}
    >
      {!expanded ? (
        <div className="wf-palette__collapsed-header">
          <button
            type="button"
            className="wf-button wf-button--icon wf-button--ghost"
            aria-label={BUILDER_COPY.expandPalette}
            onClick={() => setExpanded(true)}
          >
            ☰
          </button>
        </div>
      ) : null}
      <div className={['wf-palette__panel', !expanded ? 'wf-palette__panel--hidden' : ''].filter(Boolean).join(' ')}>
        {panelContent}
      </div>
      {!expanded ? (
        <div className="wf-palette__collapsed-list">{COLLAPSED_RAIL_KINDS.map((kind) => renderKind(kind, true))}</div>
      ) : null}
    </aside>
  );
}
