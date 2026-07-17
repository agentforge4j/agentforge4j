// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../copy/workflow-terminology';
import { wireBehaviourTypeForCanvasNode } from '../model/mapper';
import type { AgentRef, StepTransition } from '../api/types';
import type { BuilderMode } from '../hooks/useBuilderMode';
import type {
  CanvasModel,
  CanvasNode,
  LoopTerminationStrategy,
  MaxIterationsActionUi,
  NodeData,
} from '../model/canvasModel';
import { getRunsAfterState, isInsideLoopBody, START_SENTINEL } from '../model/graphOps';
import type { NodeKind } from '../model/nodeKinds';
import { NODE_KIND_META } from '../model/nodeKinds';
import type { ReactNode, Ref } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

const KINDS_WITH_BEHAVIOR = new Set<NodeKind>([
  'AI_STEP',
  'AI_DEBATE',
  'DECISION',
  'REPEAT',
  'REUSE_WORKFLOW',
  'STOP',
  'RETRY',
]);

export type StepConfigPanelProps = {
  model: CanvasModel;
  selectedId: string | null;
  mode: BuilderMode;
  onClose: () => void;
  onDelete: (id: string) => void;
  onUpdateNodeData: (id: string, partial: Partial<NodeData>) => void;
  agentCatalog?: AgentRef[];
  /** Read-only posture: hides mutating affordances and disables fields. */
  readOnly?: boolean;
  /** Reposition the selected node to run after `afterId` (or START_SENTINEL). */
  onReposition?: (nodeId: string, afterId: string) => void;
  /**
   * One-shot request to move focus somewhere inside the panel once it opens for the currently
   * selected node. `'transition'` reveals and focuses the Approval field, even if its containing
   * section is collapsed by default for the current mode — wired from the guided-mode checklist's
   * "Require approval" action (`WorkflowBuilder`'s `onGuidedStageAction`), mirroring how the "Add
   * input" stage already jumps to and reveals the input step rather than silently mutating data
   * for the user. `'panel'` focuses the panel container itself with no specific field target —
   * wired from the validation popover's "Fix" action, whose issues can point at any field.
   */
  focusField?: 'transition' | 'panel' | null;
  /** Called once the requested `focusField` has been applied (or determined inapplicable), so the
   * caller can clear the one-shot request. */
  onFocusFieldHandled?: () => void;
};

function TransitionField({
  value,
  onChange,
  selectRef,
}: {
  value: StepTransition;
  onChange: (value: StepTransition) => void;
  selectRef?: Ref<HTMLSelectElement>;
}) {
  return (
    <label className="wf-field">
      <span className="wf-field__label" title={ACTION_LABELS.approvalHelp}>
        {ACTION_LABELS.approvalField}
      </span>
      <select
        ref={selectRef}
        className="wf-input wf-select"
        value={value}
        onChange={(e) => onChange(e.target.value as StepTransition)}
      >
        <option value="AUTO">{ACTION_LABELS.approvalAutomatic}</option>
        <option value="HUMAN_REVIEW">{ACTION_LABELS.approvalReview}</option>
        <option value="HUMAN_APPROVAL">{ACTION_LABELS.approvalRequired}</option>
      </select>
    </label>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="wf-inspector__section">
      <h3 className="wf-inspector__section-title">{title}</h3>
      {children}
    </section>
  );
}

export function StepConfigPanel({
  model,
  selectedId,
  mode,
  onClose,
  onDelete,
  onUpdateNodeData,
  agentCatalog,
  readOnly = false,
  onReposition,
  focusField = null,
  onFocusFieldHandled,
}: StepConfigPanelProps) {
  const node = useMemo(() => model.nodes.find((n) => n.id === selectedId) ?? null, [model.nodes, selectedId]);
  const hasAgentCatalog = Boolean(agentCatalog && agentCatalog.length > 0);
  const transitionSelectRef = useRef<HTMLSelectElement>(null);
  const behaviorDetailsRef = useRef<HTMLDetailsElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  // Sticky memory that the Behavior section was force-opened by a `focusField` reveal request, so
  // it stays open once `focusField` itself is cleared (a one-shot signal — see below) rather than
  // snapping back closed on the very next render.
  const [behaviorRevealed, setBehaviorRevealed] = useState(false);

  const otherNodes = useMemo(
    () =>
      model.nodes
        .filter((n) => n.id !== node?.id)
        .map((n) => ({ id: n.id, label: n.data.name || NODE_KIND_META[n.kind].label })),
    [model.nodes, node],
  );

  const runsAfter = useMemo(() => getRunsAfterState(model, node?.id ?? null), [model, node]);
  const labelForNode = (id: string): string => {
    const found = model.nodes.find((n) => n.id === id);
    return found ? found.data.name?.trim() || NODE_KIND_META[found.kind].label : id;
  };

  useEffect(() => {
    if (!node || !selectedId) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [node, selectedId, onClose]);

  // Reset the sticky reveal once selection moves to a different node, so it never leaks into a
  // node that never had a reveal requested for it.
  useEffect(() => {
    setBehaviorRevealed(false);
  }, [selectedId]);

  useEffect(() => {
    if (!focusField) {
      return;
    }
    if (!node || !selectedId) {
      // No node is selected to apply the request to (e.g. a future caller sets the one-shot
      // without also selecting a node) — clear it now so it cannot linger and unexpectedly fire
      // against whatever node is selected next.
      onFocusFieldHandled?.();
      return;
    }
    if (focusField === 'panel') {
      // No specific field target — a validation issue can point at any field on any node kind, so
      // the panel container itself (tabIndex={-1} below) is the focus target.
      panelRef.current?.focus();
      onFocusFieldHandled?.();
      return;
    }
    if (node.kind !== 'AI_STEP' && node.kind !== 'REUSE_WORKFLOW') {
      // The selected node has no TransitionField to reveal (e.g. it is an AI_DEBATE/other kind) —
      // nothing to do; clear the one-shot request so it does not linger against a future selection.
      onFocusFieldHandled?.();
      return;
    }
    // `behaviorOpen` below already forces the Behavior <details> open in the same render/commit
    // that mounts this effect (via the `focusField` check directly), so the field is already in
    // the DOM by the time this runs. `behaviorRevealed` is set here so the section stays open on
    // the next render too, once the one-shot `focusField` request itself has been cleared below.
    setBehaviorRevealed(true);
    // A native <summary> click toggles the DOM `open` attribute directly without React ever
    // seeing it, so on a repeat reveal request `behaviorOpen` can still evaluate `true` from the
    // previous request (`behaviorRevealed` stays sticky) and the JSX diff sees no prop change —
    // React never re-applies the attribute, leaving the section closed in the DOM while React
    // believes it is open. Setting `.open` imperatively bypasses that diff entirely and forces
    // the section open regardless of what the user did to it since the last request.
    if (behaviorDetailsRef.current) {
      behaviorDetailsRef.current.open = true;
    }
    transitionSelectRef.current?.scrollIntoView({ block: 'center' });
    transitionSelectRef.current?.focus();
    onFocusFieldHandled?.();
  }, [focusField, node, selectedId, onFocusFieldHandled]);

  if (!node || !selectedId) {
    return null;
  }

  const title = NODE_KIND_META[node.kind].label;
  const insideLoopBody = isInsideLoopBody(model, node);
  // Forced open (regardless of mode) when a focus request targets the field this section holds, or
  // once one already has (behaviorRevealed) — the checklist's "Add approval" item is genuinely
  // satisfiable, just hidden by this section defaulting to collapsed in guided mode.
  // `behaviorRevealed` keeps it open on the render right
  // after `focusField` itself is cleared (a one-shot signal, consumed immediately by the effect
  // above), instead of the section snapping shut again the instant it was revealed.
  const behaviorOpen = mode === 'advanced' || focusField === 'transition' || behaviorRevealed;
  const wireBehaviourType = wireBehaviourTypeForCanvasNode(node);
  const wireBehaviourNote =
    node.kind === 'SAVE_RESULT'
      ? ACTION_LABELS.behaviourTypeSaveResultNote
      : node.kind === 'REPEAT'
        ? ACTION_LABELS.behaviourTypeRepeatNote
        : null;
  const showBehaviorSection = KINDS_WITH_BEHAVIOR.has(node.kind);

  return (
    <>
      <div className="wf-inspector__backdrop" role="presentation" onClick={onClose} />
      <aside
        ref={panelRef}
        tabIndex={-1}
        className="wf-panel wf-inspector wf-inspector--open"
        role="dialog"
        aria-label={title}
        aria-readonly={readOnly || undefined}
        data-testid="workflow-builder-inspector-panel"
      >
      <header className="wf-panel__header wf-inspector__header">
        <h2 className="wf-panel__title">{title}</h2>
        <button
          type="button"
          className="wf-button wf-button--ghost wf-button--icon"
          aria-label={ACTION_LABELS.configureStepClose}
          onClick={onClose}
        >
          ×
        </button>
      </header>
      <div className="wf-panel__body wf-inspector__body">
        {readOnly ? (
          <p className="wf-inspector__banner" data-testid="inspector-readonly-banner">
            {ACTION_LABELS.readOnlyBadgeTitle}
          </p>
        ) : null}
        {!readOnly && insideLoopBody ? (
          <p className="wf-inspector__banner">{ACTION_LABELS.loopBodyReadOnly}</p>
        ) : null}
        <fieldset disabled={insideLoopBody || readOnly} className="wf-inspector__fieldset">
          <Section title={ACTION_LABELS.basicsSection}>
            <label className="wf-field">
              <span className="wf-field__label">{ACTION_LABELS.nameField}</span>
              <input
                className="wf-input"
                value={node.data.name}
                onChange={(e) => onUpdateNodeData(node.id, { name: e.target.value } as Partial<NodeData>)}
              />
            </label>
            <div className="wf-inspector__grid">
              <label className="wf-field">
                <span className="wf-field__label">{ACTION_LABELS.typeField}</span>
                <input className="wf-input" value={title} disabled />
              </label>
              <label className="wf-field">
                <span className="wf-field__label">{ACTION_LABELS.descriptionField}</span>
                <input className="wf-input" value={NODE_KIND_META[node.kind].description} disabled />
              </label>
            </div>
          </Section>

          {!readOnly && runsAfter.kind !== 'hidden' ? (
            <Section title={ACTION_LABELS.flowSection}>
              {runsAfter.kind === 'editable' ? (
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.runsAfterField}</span>
                  <select
                    className="wf-input wf-select"
                    data-testid="runs-after-select"
                    value={runsAfter.current}
                    onChange={(e) => onReposition?.(node.id, e.target.value)}
                  >
                    {runsAfter.targets.map((target) => (
                      <option key={target.value} value={target.value}>
                        {target.value === START_SENTINEL
                          ? ACTION_LABELS.runsAfterStartOption
                          : labelForNode(target.value)}
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.runsAfterField}</span>
                  <select className="wf-input wf-select" data-testid="runs-after-select" value="" disabled>
                    <option value="">{ACTION_LABELS.selectPlaceholder}</option>
                  </select>
                  <span className="wf-field__hint">
                    {runsAfter.reason === 'multiplePredecessors'
                      ? ACTION_LABELS.runsAfterMultiplePredecessors
                      : runsAfter.reason === 'multipleSuccessors'
                        ? ACTION_LABELS.runsAfterMultipleSuccessors
                        : ACTION_LABELS.runsAfterNoTargets}
                  </span>
                </label>
              )}
            </Section>
          ) : null}

          <Section title={ACTION_LABELS.inputsOutputsSection}>
            {node.kind === 'ASK_USER' ? (
              <>
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.questionField}</span>
                  <textarea
                    className="wf-input wf-textarea"
                    value={node.data.question}
                    onChange={(e) => onUpdateNodeData(node.id, { question: e.target.value } as Partial<NodeData>)}
                  />
                </label>
                <p className="wf-field__hint">{ACTION_LABELS.formFieldsHint}</p>
                {node.data.artifactItems.map((item, idx) => (
                  <div key={item.id} className="wf-inspector__card">
                    <div className="wf-inspector__card-header">
                      <button
                        type="button"
                        className="wf-button wf-button--icon wf-button--ghost wf-inspector__card-remove"
                        aria-label={ACTION_LABELS.removeField}
                        title={ACTION_LABELS.removeField}
                        data-testid="workflow-builder-inspector-remove-field"
                        onClick={() =>
                          onUpdateNodeData(node.id, {
                            artifactItems: node.data.artifactItems.filter((_, i) => i !== idx),
                          } as Partial<NodeData>)
                        }
                      >
                        ×
                      </button>
                    </div>
                    <select
                      className="wf-input wf-select"
                      value={item.type}
                      onChange={(e) => {
                        const next = [...node.data.artifactItems];
                        next[idx] = { ...item, type: e.target.value as (typeof item)['type'] };
                        onUpdateNodeData(node.id, { artifactItems: next } as Partial<NodeData>);
                      }}
                    >
                      {(['TEXT', 'TEXT_AREA', 'SINGLE_CHOICE', 'MULTI_CHOICE', 'BOOLEAN', 'NUMBER', 'DATE'] as const).map(
                        (t) => (
                          <option key={t} value={t}>
                            {t}
                          </option>
                        ),
                      )}
                    </select>
                    <input
                      className="wf-input"
                      placeholder="Label"
                      value={item.label}
                      onChange={(e) => {
                        const next = [...node.data.artifactItems];
                        next[idx] = { ...item, label: e.target.value };
                        onUpdateNodeData(node.id, { artifactItems: next } as Partial<NodeData>);
                      }}
                    />
                    <input
                      className="wf-input"
                      placeholder="Key"
                      value={item.key}
                      onChange={(e) => {
                        const next = [...node.data.artifactItems];
                        next[idx] = { ...item, key: e.target.value };
                        onUpdateNodeData(node.id, { artifactItems: next } as Partial<NodeData>);
                      }}
                    />
                  </div>
                ))}
                <button
                  type="button"
                  className="wf-button wf-button--secondary"
                  data-testid="workflow-builder-inspector-add-field"
                  onClick={() =>
                    onUpdateNodeData(node.id, {
                      artifactItems: [
                        ...node.data.artifactItems,
                        {
                          id: `item-${node.data.artifactItems.length}`,
                          type: 'TEXT',
                          label: '',
                          key: '',
                          required: false,
                        },
                      ],
                    } as Partial<NodeData>)
                  }
                >
                  {ACTION_LABELS.addField}
                </button>
              </>
            ) : null}
            {node.kind === 'SAVE_RESULT' ? (
              <label className="wf-field">
                <span className="wf-field__label">{ACTION_LABELS.resultNameField}</span>
                <input
                  className="wf-input"
                  value={node.data.resultName}
                  onChange={(e) => onUpdateNodeData(node.id, { resultName: e.target.value } as Partial<NodeData>)}
                />
              </label>
            ) : null}
            {node.kind === 'LOAD_RESOURCE' ? (
              <>
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.resourcePathField}</span>
                  <input
                    className="wf-input"
                    value={node.data.resourcePath}
                    onChange={(e) => onUpdateNodeData(node.id, { resourcePath: e.target.value } as Partial<NodeData>)}
                  />
                </label>
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.resultContextKeyField}</span>
                  <input
                    className="wf-input"
                    value={node.data.resultName}
                    onChange={(e) => onUpdateNodeData(node.id, { resultName: e.target.value } as Partial<NodeData>)}
                  />
                </label>
              </>
            ) : null}
          </Section>

          {showBehaviorSection ? (
          <details
            ref={behaviorDetailsRef}
            className="wf-inspector__details"
            open={behaviorOpen}
            data-testid="workflow-builder-inspector-behaviour-section"
          >
            <summary className="wf-inspector__details-summary">{ACTION_LABELS.behaviorSection}</summary>
            <div className="wf-inspector__details-body">
              {node.kind === 'AI_STEP' ? (
                <>
                  <label className="wf-field">
                    <span className="wf-field__label">{ACTION_LABELS.agentField}</span>
                    {hasAgentCatalog ? (
                      <select
                        className="wf-input wf-select"
                        value={node.data.agentRef}
                        onChange={(e) => onUpdateNodeData(node.id, { agentRef: e.target.value } as Partial<NodeData>)}
                      >
                        <option value="">{ACTION_LABELS.selectPlaceholder}</option>
                        {agentCatalog!.map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.name}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input
                        className="wf-input"
                        value={node.data.agentRef}
                        placeholder={ACTION_LABELS.agentPlaceholder}
                        onChange={(e) => onUpdateNodeData(node.id, { agentRef: e.target.value } as Partial<NodeData>)}
                      />
                    )}
                  </label>
                  <label className="wf-field">
                    <span className="wf-field__label">{ACTION_LABELS.instructionsField}</span>
                    <textarea
                      className="wf-input wf-textarea wf-textarea--tall"
                      value={node.data.instructions}
                      onChange={(e) => onUpdateNodeData(node.id, { instructions: e.target.value } as Partial<NodeData>)}
                    />
                  </label>
                  <TransitionField
                    value={node.data.transition}
                    onChange={(value) => onUpdateNodeData(node.id, { transition: value } as Partial<NodeData>)}
                    selectRef={transitionSelectRef}
                  />
                </>
              ) : null}
              {node.kind === 'AI_DEBATE' ? (
                <DebateForm node={node} agentCatalog={agentCatalog} onUpdate={onUpdateNodeData} />
              ) : null}
              {node.kind === 'DECISION' ? (
                <DecisionForm node={node} otherNodes={otherNodes} onUpdate={onUpdateNodeData} />
              ) : null}
              {node.kind === 'REPEAT' ? <RepeatForm node={node} onUpdate={onUpdateNodeData} /> : null}
              {node.kind === 'REUSE_WORKFLOW' ? (
                <>
                  <label className="wf-field">
                    <span className="wf-field__label">{ACTION_LABELS.reuseWorkflow}</span>
                    <input
                      className="wf-input"
                      value={node.data.workflowRef}
                      placeholder={ACTION_LABELS.workflowIdPlaceholder}
                      onChange={(e) => onUpdateNodeData(node.id, { workflowRef: e.target.value } as Partial<NodeData>)}
                    />
                  </label>
                  <TransitionField
                    value={node.data.transition}
                    onChange={(value) => onUpdateNodeData(node.id, { transition: value } as Partial<NodeData>)}
                    selectRef={transitionSelectRef}
                  />
                </>
              ) : null}
              {node.kind === 'STOP' ? (
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.reasonField}</span>
                  <textarea
                    className="wf-input wf-textarea"
                    value={node.data.reason}
                    onChange={(e) => onUpdateNodeData(node.id, { reason: e.target.value } as Partial<NodeData>)}
                  />
                </label>
              ) : null}
              {node.kind === 'RETRY' ? (
                <>
                  <label className="wf-field">
                    <span className="wf-field__label">{ACTION_LABELS.targetStepField}</span>
                    <select
                      className="wf-input wf-select"
                      value={node.data.targetNodeId}
                      onChange={(e) => onUpdateNodeData(node.id, { targetNodeId: e.target.value } as Partial<NodeData>)}
                    >
                      <option value="">{ACTION_LABELS.selectPlaceholder}</option>
                      {otherNodes.map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="wf-field">
                    <span className="wf-field__label">{ACTION_LABELS.fallbackTargetField}</span>
                    <select
                      className="wf-input wf-select"
                      value={node.data.fallbackTargetNodeId}
                      onChange={(e) =>
                        onUpdateNodeData(node.id, { fallbackTargetNodeId: e.target.value } as Partial<NodeData>)
                      }
                    >
                      <option value="">{ACTION_LABELS.selectPlaceholder}</option>
                      {otherNodes.map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </label>
                </>
              ) : null}
            </div>
          </details>
          ) : null}

          <details className="wf-inspector__details">
            <summary className="wf-inspector__details-summary">{ACTION_LABELS.advancedSection}</summary>
            <div className="wf-inspector__details-body">
              <label className="wf-field">
                <span className="wf-field__label">{ACTION_LABELS.behaviourTypeField}</span>
                {wireBehaviourType ? (
                  <input
                    className="wf-input wf-input--mono"
                    value={wireBehaviourType}
                    readOnly
                    aria-readonly="true"
                  />
                ) : (
                  <p className="wf-field__hint">{wireBehaviourNote}</p>
                )}
              </label>
              {node.kind === 'AI_STEP' ? (
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.maxRetriesField}</span>
                  <input
                    className="wf-input"
                    type="number"
                    min={0}
                    value={node.data.maxRetries}
                    onChange={(e) =>
                      onUpdateNodeData(node.id, { maxRetries: Number(e.target.value || 0) } as Partial<NodeData>)
                    }
                  />
                </label>
              ) : null}
              {node.kind === 'RETRY' ? (
                <label className="wf-field">
                  <span className="wf-field__label">{ACTION_LABELS.maxAttemptsField}</span>
                  <input
                    className="wf-input"
                    type="number"
                    min={1}
                    value={node.data.maxAttempts}
                    onChange={(e) =>
                      onUpdateNodeData(node.id, { maxAttempts: Number(e.target.value || 1) } as Partial<NodeData>)
                    }
                  />
                </label>
              ) : null}
            </div>
          </details>
        </fieldset>
      </div>
      {!readOnly ? (
        <footer className="wf-inspector__footer">
          <button
            type="button"
            className="wf-button wf-button--destructive"
            disabled={insideLoopBody}
            onClick={() => onDelete(node.id)}
          >
            {ACTION_LABELS.deleteStep}
          </button>
        </footer>
      ) : null}
    </aside>
    </>
  );
}

function DebateForm({
  node,
  agentCatalog,
  onUpdate,
}: {
  node: CanvasNode;
  agentCatalog?: AgentRef[];
  onUpdate: (id: string, partial: Partial<NodeData>) => void;
}) {
  if (node.kind !== 'AI_DEBATE') {
    return null;
  }
  const d = node.data;
  const hasCatalog = Boolean(agentCatalog && agentCatalog.length > 0);

  const agentControl = (field: 'primaryAgentRef' | 'challengerAgentRef') =>
    hasCatalog ? (
      <select
        className="wf-input wf-select"
        value={d[field]}
        onChange={(e) => onUpdate(node.id, { [field]: e.target.value } as Partial<NodeData>)}
      >
        <option value="">{ACTION_LABELS.selectPlaceholder}</option>
        {agentCatalog!.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name}
          </option>
        ))}
      </select>
    ) : (
      <input
        className="wf-input"
        value={d[field]}
        placeholder={ACTION_LABELS.agentPlaceholder}
        onChange={(e) => onUpdate(node.id, { [field]: e.target.value } as Partial<NodeData>)}
      />
    );

  return (
    <>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.primaryAgentField}</span>
        {agentControl('primaryAgentRef')}
      </label>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.challengerAgentField}</span>
        {agentControl('challengerAgentRef')}
      </label>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.maxRoundsField}</span>
        <input
          className="wf-input"
          type="number"
          min={1}
          value={d.maxRounds}
          onChange={(e) => onUpdate(node.id, { maxRounds: Number(e.target.value || 1) } as Partial<NodeData>)}
        />
      </label>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.resolutionInstructionsField}</span>
        <textarea
          className="wf-input wf-textarea"
          value={d.resolutionPrompt}
          onChange={(e) => onUpdate(node.id, { resolutionPrompt: e.target.value } as Partial<NodeData>)}
        />
      </label>
    </>
  );
}

function DecisionForm({
  node,
  otherNodes,
  onUpdate,
}: {
  node: CanvasNode;
  otherNodes: { id: string; label: string }[];
  onUpdate: (id: string, partial: Partial<NodeData>) => void;
}) {
  if (node.kind !== 'DECISION') {
    return null;
  }
  const d = node.data;
  return (
    <>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.contextKeyField}</span>
        <input
          className="wf-input"
          value={d.contextKey}
          onChange={(e) => onUpdate(node.id, { contextKey: e.target.value } as Partial<NodeData>)}
        />
        <span className="wf-field__hint">{ACTION_LABELS.contextKeyHint}</span>
      </label>
      {d.cases.map((c, idx) => (
        <div key={`${c.value}-${idx}`} className="wf-inspector__card">
          <div className="wf-inspector__card-header">
            <button
              type="button"
              className="wf-button wf-button--icon wf-button--ghost wf-inspector__card-remove"
              aria-label={ACTION_LABELS.removeCase}
              title={ACTION_LABELS.removeCase}
              data-testid="workflow-builder-inspector-remove-case"
              onClick={() =>
                onUpdate(node.id, { cases: d.cases.filter((_, i) => i !== idx) } as Partial<NodeData>)
              }
            >
              ×
            </button>
          </div>
          <input
            className="wf-input"
            placeholder={ACTION_LABELS.caseLabelPlaceholder}
            value={c.label}
            onChange={(e) => {
              const next = [...d.cases];
              next[idx] = { ...c, label: e.target.value };
              onUpdate(node.id, { cases: next } as Partial<NodeData>);
            }}
          />
          <input
            className="wf-input"
            placeholder={ACTION_LABELS.caseValuePlaceholder}
            value={c.value}
            onChange={(e) => {
              const next = [...d.cases];
              next[idx] = { ...c, value: e.target.value };
              onUpdate(node.id, { cases: next } as Partial<NodeData>);
            }}
          />
          <label className="wf-field">
            <span className="wf-field__label">{ACTION_LABELS.connectsToField}</span>
            <select
              className="wf-input wf-select"
              value={c.targetNodeId}
              onChange={(e) => {
                const next = [...d.cases];
                next[idx] = { ...c, targetNodeId: e.target.value };
                onUpdate(node.id, { cases: next } as Partial<NodeData>);
              }}
            >
              <option value="">{ACTION_LABELS.selectPlaceholder}</option>
              {otherNodes.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      ))}
      <button
        type="button"
        className="wf-button wf-button--secondary"
        data-testid="workflow-builder-inspector-add-case"
        onClick={() =>
          onUpdate(node.id, {
            cases: [...d.cases, { label: 'New case', value: `case-${d.cases.length + 1}`, targetNodeId: '' }],
          } as Partial<NodeData>)
        }
      >
        {ACTION_LABELS.addCase}
      </button>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.defaultBranchTargetField}</span>
        <select
          className="wf-input wf-select"
          value={d.defaultTargetNodeId}
          onChange={(e) => onUpdate(node.id, { defaultTargetNodeId: e.target.value } as Partial<NodeData>)}
        >
          <option value="">{ACTION_LABELS.selectPlaceholder}</option>
          {otherNodes.map((o) => (
            <option key={o.id} value={o.id}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
    </>
  );
}

function RepeatForm({ node, onUpdate }: { node: CanvasNode; onUpdate: (id: string, partial: Partial<NodeData>) => void }) {
  if (node.kind !== 'REPEAT') {
    return null;
  }
  const d = node.data;
  return (
    <>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.loopModeField}</span>
        <select
          className="wf-input wf-select"
          value={d.strategy}
          onChange={(e) =>
            onUpdate(node.id, { strategy: e.target.value as LoopTerminationStrategy } as Partial<NodeData>)
          }
        >
          <option value="FIXED_COUNT">{ACTION_LABELS.fixedCountMode}</option>
          <option value="FOR_EACH">{ACTION_LABELS.forEachMode}</option>
          <option value="AGENT_SIGNAL">{ACTION_LABELS.agentSignalMode}</option>
          <option value="EVALUATOR">{ACTION_LABELS.evaluatorMode}</option>
        </select>
      </label>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.maxIterationsField}</span>
        <input
          className="wf-input"
          type="number"
          min={1}
          value={d.maxIterations}
          onChange={(e) => onUpdate(node.id, { maxIterations: Number(e.target.value || 1) } as Partial<NodeData>)}
        />
      </label>
      {d.strategy === 'FOR_EACH' ? (
        <label className="wf-field">
          <span className="wf-field__label">{ACTION_LABELS.listContextKeyField}</span>
          <input
            className="wf-input"
            value={d.forEachKey ?? ''}
            onChange={(e) => onUpdate(node.id, { forEachKey: e.target.value } as Partial<NodeData>)}
          />
        </label>
      ) : null}
      {d.strategy === 'EVALUATOR' ? (
        <label className="wf-field">
          <span className="wf-field__label">{ACTION_LABELS.evaluatorAgentField}</span>
          <input
            className="wf-input"
            value={d.evaluatorAgentId ?? ''}
            onChange={(e) => onUpdate(node.id, { evaluatorAgentId: e.target.value } as Partial<NodeData>)}
          />
        </label>
      ) : null}
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.maxIterationsActionField}</span>
        <select
          className="wf-input wf-select"
          value={d.maxIterationsAction}
          onChange={(e) =>
            onUpdate(node.id, { maxIterationsAction: e.target.value as MaxIterationsActionUi } as Partial<NodeData>)
          }
        >
          <option value="AWAIT_USER">{ACTION_LABELS.awaitUserAction}</option>
          <option value="FAIL">{ACTION_LABELS.failAction}</option>
        </select>
      </label>
    </>
  );
}
