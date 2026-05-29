// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS, BUILDER_COPY } from '../copy/workflow-terminology';
import type { BuilderMode } from '../hooks/useBuilderMode';
import { LIBRARY_ADVANCED_KINDS, LIBRARY_COMMON_KINDS, NODE_KIND_META, type NodeKind } from '../model/nodeKinds';
import { useEffect, useState } from 'react';

type StepPaletteProps = {
  mode: BuilderMode;
  onAddStep: (kind: NodeKind) => void;
  defaultCollapsed?: boolean;
};

export function StepPalette({ mode, onAddStep, defaultCollapsed = true }: StepPaletteProps) {
  const [expanded, setExpanded] = useState(!defaultCollapsed);
  const [advancedOpen, setAdvancedOpen] = useState(mode === 'advanced');
  const [isMobile, setIsMobile] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setAdvancedOpen(mode === 'advanced');
  }, [mode]);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    const media = window.matchMedia('(max-width: 767px)');
    const update = () => setIsMobile(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);

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
        <p className="wf-palette__section-label">Common</p>
        <div className="wf-palette__list">{LIBRARY_COMMON_KINDS.map((kind) => renderKind(kind, !expanded && !isMobile))}</div>
        {expanded || isMobile ? (
          <>
            <button
              type="button"
              className="wf-palette__advanced-toggle"
              onClick={() => setAdvancedOpen((o) => !o)}
            >
              <span aria-hidden>{advancedOpen ? '▾' : '▸'}</span>
              {mode === 'guided' ? ACTION_LABELS.advancedStepsGuided : ACTION_LABELS.advancedSteps}
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
        <div className="wf-palette__collapsed-list">{LIBRARY_COMMON_KINDS.map((kind) => renderKind(kind, true))}</div>
      ) : null}
    </aside>
  );
}
